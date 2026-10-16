package jks.tools2d.parallax;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Disposable;

import jks.tools2d.parallax.heart.Gvars_Parallax;
import jks.tools2d.parallax.pages.Enum_LayerKind;
import jks.tools2d.parallax.pages.Enum_ParticleAnchor;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * Scrolls and draws the layers of a page, tiling them across the camera view, and cross-fades into another page
 * ({@link #addLayersTransfert}) or tint ({@link #addColorTransfert}).
 * <p>
 * Layers are screen-anchored: they are laid out from the left/bottom edge of the camera view, so moving the game
 * camera does not drag the background away.
 * <p>
 * An EMPTY layer is drawn by the game: {@link #setLayerHook} registers what draws it, under the layer's name. A
 * PARTICLES layer draws its {@link ParallaxParticles} in its place: once per tile when pinned to the layer, once from
 * the view when anchored to it ({@link Enum_ParticleAnchor}). A SHADER layer's tiles are drawn through its effect, by
 * the {@link LayerEffects} of the engine ({@link #setLayerEffects}), at a phase taken from this reader's clock. A page's
 * depth fog ({@link #setFog}) mixes each IMAGE, SEQUENCE and SHADER layer toward the fog's colour, more the slower the
 * layer scrolls ({@link #fogOf}), through the same effects. A
 * SEQUENCE layer's cycle is the one its page stores the seed of, unless the game passes its own
 * ({@link #setSequenceSeed}). A FLOOR layer is drawn row by row, each row at its depth
 * ({@link ParallaxLayer#drawFloor}), and every layer in front of it scrolls at the speed of its row under the layer's
 * foot ({@link #plant}). A page with a planet ({@link #setPlanetRadius}) is the side of a world turning round one centre
 * under the view: its IMAGE, SHADER and SEQUENCE layers are wrapped round it alike, each turning at its own speed, and
 * what stands up leans out from it ({@link PlanetBatch}). A LURKER layer's figure appears or vanishes only while
 * nothing of it is on screen: gone at the sighting after one where it was seen, there by chance from its seed, or when
 * the game says ({@link #reveal}; docs/lurker-layers.md).
 */
public class ParallaxPageReader implements Disposable
{
	public ArrayList<ParallaxLayer> layers = new ArrayList<>();
	public ArrayList<ParallaxLayer> transferLayers = new ArrayList<>();

	public Enum_TransfertType transfertType = Enum_TransfertType.NONE;

	/** The world this reader's layers are placed in, see {@link #setWorldSize}. */
	private float worldWidth = Gvars_Parallax.getWorldWidth(), worldHeight = Gvars_Parallax.getWorldHeight();

	private boolean repeatOnX, repeatOnY;
	private float drawingHeight;

	/** Opacity of the incoming page (0 → 1) and of the outgoing one (1 → 0) during a page transfer. */
	private float newLayerAlpha = 0, oldLayerAlpha = 1;
	private float newLayerFadeSpeed, oldLayerFadeSpeed;
	/** How the transfert under way is drawn: {@link TransfertStyle#FADE} outside one. */
	private TransfertStyle transfertStyle = TransfertStyle.FADE;

	private final Color tint = new Color(Color.WHITE);
	private final Color tintFrom = new Color(Color.WHITE);
	private final Color tintTo = new Color(Color.WHITE);
	private float tintDuration, tintElapsed;

	/** What draws each EMPTY layer, by layer name. */
	private final HashMap<String, LayerHook> hooks = new HashMap<>();
	/** The batch color before a hook, put back after it. */
	private final Color hookColor = new Color();

	/** Draws the SHADER layers: {@link GdxLayerEffects} unless the engine set its own, made when first needed. */
	private LayerEffects effects;
	private boolean ownsEffects;
	/** Seconds acted since the reader was made: the SHADER layers' clock, shared by both pages of a cross-fade. */
	private double effectTime;

	/** The depth fog of the page on screen ({@link #setFog}), and of the page a cross-fade brings in. */
	private float fogStrength, transferFogStrength;
	private final Color fogColor = new Color(WholePage_Model.FOG_R, WholePage_Model.FOG_G, WholePage_Model.FOG_B, 1);
	private final Color transferFogColor = new Color(fogColor);
	/** Each page's fastest speed ratio X ({@link #frontSpeedOf}), refreshed each draw. */
	private float frontSpeed, transferFrontSpeed;
	/**
	 * This draw's fog is the engine's page fog ({@link LayerEffects#beginPageFog}): the IMAGE and SEQUENCE layers' fog in
	 * the batch color, one shader for all; and whether it is on now (a hook or particles turn it off).
	 */
	private boolean pageFog, pageFogOn;
	/** The batch alpha of the layer drawn through the page fog, given back with the tint after it. */
	private float packedAlpha;
	/**
	 * A dissolve's layer is being drawn through the engine's mask ({@link LayerEffects#setDissolve}), and the opacity it
	 * fades at when the engine cannot begin it after all.
	 */
	private boolean dissolving;
	private float dissolveFallback;
	/**
	 * A transfert through a colour's layer is being drawn mixed toward it by the engine ({@link LayerEffects#setGrade}),
	 * and the opacity it fades at when the engine cannot begin it after all.
	 */
	private boolean grading;
	private float gradeFallback;

	/** The game's seed for the SEQUENCE layers, when {@link #hasSequenceSeed}: see {@link #setSequenceSeed}. */
	private int sequenceSeed;
	private boolean hasSequenceSeed;

	// Visible area of the camera, refreshed each draw; during a dollied layer, that layer's own (setDolly).
	private float viewLeft, viewBottom, viewWidth, viewHeight;
	/** The camera's viewport, zoom and middle, refreshed each draw. */
	private float viewportWidth, viewportHeight, cameraZoom, cameraX, cameraY;
	/** Where the layer being drawn is placed from: the view's corner; dollied, the corner of the view at zoom 1. */
	private float anchorLeft, anchorBottom;
	/** Each layer drawn at its own zoom by its depth ({@link #setDolly}), through {@link #dollyBatch}. */
	private boolean dolly;
	private final DollyBatch dollyBatch = new DollyBatch();
	/** The planet's radius of the page on screen ({@link #setPlanetRadius}), and of the page a cross-fade brings in. */
	private float planetRadius, transferPlanetRadius;
	private final PlanetBatch planetBatch = new PlanetBatch();
	/**
	 * Each page's tower seen from close by ({@link #nearTowerOf}), refreshed each draw: the layers slower than it are drawn
	 * round it as panoramas, through {@link #panoramaBatch}.
	 */
	private ParallaxLayer nearTower, transferNearTower;
	private final PanoramaBatch panoramaBatch = new PanoramaBatch();
	/**
	 * The planet's radius in the view of the PARTICLES layer being drawn ({@link #planetInView}): each effect is turned
	 * round it as one block. 0: flat.
	 */
	private float particlePlanet;
	/**
	 * How far above the view's top a flat tile of the layer being drawn still shows round a planet: the view's top
	 * corners show what lies that much higher ({@link #planetReachOf}). 0: flat.
	 */
	private float planetReach;

	/**
	 * Shows {@code newLayers} after the current ones. A layer another reader already shows is cloned, so each reader
	 * scrolls its own; a page's layers come from {@link #layersOf}, built for this reader's world.
	 */
	public void addLayers(List<ParallaxLayer> newLayers)
	{
		for (ParallaxLayer layer : newLayers)
		{
			ParallaxLayer taken = take(layer);
			taken.setWorldSize(worldWidth, worldHeight);
			drawCycle(taken);
			layers.add(taken);
		}
	}

	/** Drops every layer, letting another reader take the page's own layers. */
	public void clearLayers()
	{
		release(layers);
		layers.clear();
	}

	/** Lets another reader take these layers as they are: this one drops them. */
	private void release(List<ParallaxLayer> dropped)
	{
		for (int i = 0, n = dropped.size(); i < n; i++)
			if (dropped.get(i).reader == this)
				dropped.get(i).reader = null;
	}

	/**
	 * The layers of {@code pageModel} for this reader's world: the page's own, built once ({@link
	 * WholePage_Model#getDrawing(String, float, float)}), unless another reader shows them (one page in two hearts), then
	 * layers the page builds for this world.
	 */
	public List<ParallaxLayer> layersOf(WholePage_Model pageModel, String relativePath)
	{
		List<ParallaxLayer> drawing = pageModel.getDrawing(relativePath, worldWidth, worldHeight);
		for (int i = 0, n = drawing == null ? 0 : drawing.size(); i < n; i++)
			if (drawing.get(i).reader != null && drawing.get(i).reader != this)
				return pageModel.buildDrawing(worldWidth, worldHeight);
		return drawing;
	}

	/**
	 * The layer itself if no other reader shows it, else a clone of it, this reader's own from then on. A clone keeps
	 * the width it was built for: {@link #layersOf} builds a page's for this world.
	 */
	private ParallaxLayer take(ParallaxLayer layer)
	{
		ParallaxLayer taken = layer.reader == null || layer.reader == this ? layer : layer.clone();
		taken.reader = this;
		return taken;
	}

	/**
	 * Cross-fades from the current layers into the layers of {@code pageModel} over {@code inXSecondes}. A page not
	 * built yet takes its atlas from the internal assets.
	 */
	public void addLayersTransfert(WholePage_Model pageModel, float inXSecondes)
	{addLayersTransfert(pageModel, null, inXSecondes);}

	/**
	 * Cross-fades into the layers of {@code pageModel}. A page not built yet takes its atlas from {@code relativePath},
	 * or from the internal assets when it is null or empty (see {@link WholePage_Model#getDrawing(String, float, float)}).
	 */
	public void addLayersTransfert(WholePage_Model pageModel, String relativePath, float inXSecondes)
	{addLayersTransfert(pageModel, relativePath, inXSecondes, TransfertStyle.FADE);}

	/**
	 * Cross-fades into the layers of {@code pageModel}, drawn as {@code style} says (null: {@link TransfertStyle#FADE}).
	 * A page not built yet takes its atlas from {@code relativePath}, as {@link #addLayersTransfert(WholePage_Model, String, float)}.
	 */
	public void addLayersTransfert(WholePage_Model pageModel, String relativePath, float inXSecondes, TransfertStyle style)
	{
		resetTransfert();

		List<ParallaxLayer> newLayers = layersOf(pageModel, relativePath);
		if (newLayers == null || newLayers.isEmpty())
			return;
		transferFogStrength = pageModel.getFogStrength();
		transferFogColor.set(pageModel.fogColor);
		transferPlanetRadius = pageModel.planetRadius;

		// Transferring into the page on screen: its layers must not be moved and drawn twice per frame.
		for (ParallaxLayer layer : newLayers)
		{
			ParallaxLayer incoming = layers.contains(layer) ? layer.clone() : take(layer);
			incoming.setWorldSize(worldWidth, worldHeight);
			drawCycle(incoming);
			transferLayers.add(incoming);
		}
		syncTransferPositions();

		if (inXSecondes <= 0)
		{
			release(layers);
			layers = transferLayers;
			takeTransferFog();
			resetTransfert();
			return;
		}

		transfertType = Enum_TransfertType.EACH_FRAME;
		transfertStyle = style == null ? TransfertStyle.FADE : style;
		newLayerFadeSpeed = oldLayerFadeSpeed = 1 / inXSecondes;
	}

	/**
	 * The depth fog of the page on screen: its strength, 0 or more (0: no fog), and its colour, which the page's tint
	 * does not change; see {@link WholePage_Model#getFogStrength}. A heart sets its page's; a cross-fade brings in the
	 * incoming page's.
	 */
	public void setFog(float strength, Color color)
	{
		fogStrength = strength > 0 ? strength : 0;
		if (color != null)
			fogColor.set(color);
	}

	public float getFogStrength()
	{return fogStrength;}

	public Color getFogColor()
	{return fogColor;}

	private void takeTransferFog()
	{
		fogStrength = transferFogStrength;
		fogColor.set(transferFogColor);
		planetRadius = transferPlanetRadius;
	}

	/**
	 * The planet the page on screen runs round: its radius, in percent of the world width (0 or less: none, the layers
	 * are flat); see {@link WholePage_Model#planetRadius}. A heart sets its page's; a cross-fade brings in the incoming
	 * page's, each page bent by its own during the fade.
	 */
	public void setPlanetRadius(float radius)
	{planetRadius = radius > 0 ? radius : 0;}

	public float getPlanetRadius()
	{return planetRadius;}

	/**
	 * The planet's radius, in world units, in the view being drawn, for the page on screen or the incoming one: the page's
	 * {@code radius} percent of the world width, scaled as the view is against the camera's (a dollied layer's view is
	 * scaled back into the camera's, so every layer of a page is wrapped round the same circle on screen). 0 for none.
	 */
	private float planetInView(boolean incoming)
	{
		float radius = (incoming ? transferPlanetRadius : planetRadius) * (worldWidth / 100);
		return radius > 0 ? radius * viewWidth / (viewportWidth * cameraZoom) : 0;
	}

	/** Whether {@code layer} is one of the page a cross-fade brings in. */
	private boolean incoming(ParallaxLayer layer)
	{
		if (transferLayers == layers)
			return false;
		for (int i = 0, n = transferLayers.size(); i < n; i++)
			if (transferLayers.get(i) == layer)
				return true;
		return false;
	}

	/**
	 * How far above a view {@code viewWidth} by {@code viewHeight} its top corners show, wrapped round a planet of
	 * {@code radius} under its bottom ({@link PlanetBatch}): {@code √((R + h)² + (w / 2)²) - R - h}. 0 for none.
	 */
	static float planetReachOf(float radius, float viewWidth, float viewHeight)
	{
		if (!(radius > 0))
			return 0;
		float far = radius + viewHeight;
		return (float) Math.sqrt(far * far + viewWidth * viewWidth / 4) - far;
	}

	/**
	 * Where the point {@code (x, y)} of {@code layer} is drawn round its page's planet (r347, {@link PlanetBatch}): the
	 * view is the side of a world turning round one centre under the middle of its bottom, x along the ground's arc and
	 * y along the radius. For a {@link LayerHook} to place what it draws in an EMPTY layer, turned by
	 * {@link #planetAngleAt}; {@code (x, y)} for no planet. Called from a hook, the view is the layer's own (dollied,
	 * {@link #setDolly}).
	 * 
	 * @return {@code out}, set
	 */
	public Vector2 planetPlaceAt(ParallaxLayer layer, float x, float y, Vector2 out)
	{
		float radius = planetInView(incoming(layer)), middle = viewLeft + viewWidth / 2;
		if (!(radius > 0))
			return out.set(x, y);
		return out.set(PlanetBatch.placeX(radius, middle, viewBottom, x, y), PlanetBatch.placeY(radius, middle, viewBottom, x, y));
	}

	/**
	 * How far, in degrees counterclockwise (libGDX's {@code rotation}), what stands at {@code x} in {@code layer} leans
	 * round its page's planet: out along the radius, to the right right of the view's middle (a negative angle). 0 for
	 * no planet. See {@link #planetPlaceAt}.
	 */
	public float planetAngleAt(ParallaxLayer layer, float x)
	{
		float radius = planetInView(incoming(layer));
		return radius > 0 ? -(x - (viewLeft + viewWidth / 2)) / radius * MathUtils.radiansToDegrees : 0;
	}

	/**
	 * How far the ground under {@code x} is lowered round {@code layer}'s planet: the view's bottom less where
	 * {@link #planetPlaceAt} puts {@code (x, bottom)}.
	 * 
	 * @deprecated r347: round a planet a hook's drawing leans too; place it by {@link #planetPlaceAt} and turn it by
	 *             {@link #planetAngleAt}.
	 */
	@Deprecated
	public float planetDropAt(ParallaxLayer layer, float x)
	{
		float radius = planetInView(incoming(layer));
		return radius > 0 ? viewBottom - PlanetBatch.placeY(radius, viewLeft + viewWidth / 2, viewBottom, x, viewBottom) : 0;
	}

	/** Tints every layer toward {@code color} over {@code inXSecondes}. */
	public void addColorTransfert(Color color, float inXSecondes)
	{
		if (color == null)
			return;

		tintFrom.set(tint);
		tintTo.set(color);
		tintElapsed = 0;
		tintDuration = inXSecondes;
		if (inXSecondes <= 0)
			tint.set(color);
	}

	/**
	 * Pages are stacked back to front and matched from the front: when the incoming page has more layers, its extra
	 * back layers have no outgoing counterpart. Matched layers keep their own placement but take the distance their
	 * counterpart has scrolled, so the fade is seamless.
	 */
	private void syncTransferPositions()
	{
		int total = Math.max(layers.size(), transferLayers.size());
		int oldOffset = total - layers.size();
		int newOffset = total - transferLayers.size();

		for (int slot = Math.max(oldOffset, newOffset); slot < total; slot++)
		{
			ParallaxLayer from = layers.get(slot - oldOffset);
			ParallaxLayer to = transferLayers.get(slot - newOffset);
			to.setScrollX(from.getScrollX());
			to.setScrollY(from.getScrollY());
			// Its own noise, from its own tile: what the outgoing layer wrapped is not its.
			to.wrappedX = 0;
			to.wrappedY = 0;
		}
	}

	public void draw(OrthographicCamera worldCamera, Batch batch)
	{
		boolean dissolve = transfertStyle.getKind() == TransfertStyle.Kind.DISSOLVE && !transferLayers.isEmpty();
		boolean throughColor = transfertStyle.grades() && !transferLayers.isEmpty();
		beginDraw(worldCamera, batch, dissolve || throughColor);

		// A layer at alpha 0 still costs its pixels on the GPU, and during a transfer a flush when the pages' atlases differ.
		if (transferLayers.isEmpty())
		{
			if (setBatchColor(batch, 1))
				for (int i = 0, n = layers.size(); i < n; i++)
					drawLayer(layers, i, batch, false);
		}
		else
			drawTransfert(batch, dissolve, throughColor);

		endPageFog(batch);
		release(batch);
		batch.setColor(Color.WHITE);
	}

	/**
	 * Draws only the layer at {@code index} of the page, as {@link #draw} draws it without a transfert in progress: for a
	 * 3D view that puts each layer on its own plane (r444, the editor's and the depth lab's), the batch's transform
	 * matrix set per layer by the caller. A game draws the page with {@link #draw}.
	 */
	public void drawLayer(OrthographicCamera worldCamera, Batch batch, int index)
	{
		beginDraw(worldCamera, batch, false);
		if (setBatchColor(batch, 1))
			drawLayer(layers, index, batch, false);
		endPageFog(batch);
		release(batch);
		batch.setColor(Color.WHITE);
	}

	/**
	 * What every draw begins with: the camera's view, each page's front, its tower and its lurkers, and the page fog's
	 * shared shader unless {@code ownShaders} (a dissolve or a transfert through a colour begins each layer with its own).
	 */
	private void beginDraw(OrthographicCamera worldCamera, Batch batch, boolean ownShaders)
	{
		viewportWidth = worldCamera.viewportWidth;
		viewportHeight = worldCamera.viewportHeight;
		cameraZoom = worldCamera.zoom;
		cameraX = worldCamera.position.x;
		cameraY = worldCamera.position.y;
		view(cameraZoom);
		frontSpeed = frontSpeedOf(layers);
		transferFrontSpeed = frontSpeedOf(transferLayers);
		nearTower = nearTowerOf(layers);
		transferNearTower = nearTowerOf(transferLayers);
		// The lurkers against this view, which act() only knew from the last draw.
		sightLurkers();
		// A dissolve begins each layer with its own mask, a transfert through a colour or a fog creep with its own grade:
		// no page fog's shared shader then.
		pageFogOn = pageFog = !ownShaders && (fogStrength > 0 || (transferFogStrength > 0 && !transferLayers.isEmpty()))
				&& getLayerEffects().beginPageFog(batch, tint, fogColor, transferFogColor);
	}

	/** Both pages of a transfert, slot by slot, back to front. */
	private void drawTransfert(Batch batch, boolean dissolve, boolean throughColor)
	{
		int total = Math.max(layers.size(), transferLayers.size());
		int oldOffset = total - layers.size();
		int newOffset = total - transferLayers.size();
		boolean staggered = transfertStyle.getKind() != TransfertStyle.Kind.FADE;

		for (int slot = 0; slot < total; slot++)
		{
			// Depth stagger and dissolve: each slot at its own point of the transfert, the back ones ahead.
			float ramp = staggered ? transfertStyle.slotRamp(newLayerAlpha, slot, total) : newLayerAlpha;
			if (dissolve)
			{
				if (slot >= oldOffset)
					drawDissolved(layers, slot - oldOffset, batch, false, ramp);
				if (slot >= newOffset)
					drawDissolved(transferLayers, slot - newOffset, batch, true, ramp);
				continue;
			}
			if (throughColor)
			{
				// The outgoing layer until the slot is all colour (halfway through its window; a fog creep's, halfway through
				// the transfert), the incoming one after.
				boolean incoming = transfertStyle.showsIncoming(newLayerAlpha, slot, total);
				float grade = transfertStyle.gradeAt(newLayerAlpha, slot, total);
				if (!incoming && slot >= oldOffset)
					drawGraded(layers, slot - oldOffset, batch, false, grade);
				else if (incoming && slot >= newOffset)
					drawGraded(transferLayers, slot - newOffset, batch, true, grade);
				continue;
			}
			if (slot >= oldOffset && setBatchColor(batch, staggered ? 1 - ramp : oldLayerAlpha))
				drawLayer(layers, slot - oldOffset, batch, false);
			if (slot >= newOffset && setBatchColor(batch, ramp))
				drawLayer(transferLayers, slot - newOffset, batch, true);
		}
	}

	/**
	 * A dissolve's layer: an IMAGE, SEQUENCE or SHADER one at full opacity through the engine's mask, its share of the
	 * slot at {@code ramp}; an EMPTY or PARTICLES one, or any when the engine draws no dissolve, faded to that share. Not
	 * drawn when its share is none.
	 */
	private void drawDissolved(ArrayList<ParallaxLayer> page, int index, Batch batch, boolean incoming, float ramp)
	{
		float share = incoming ? ramp : 1 - ramp;
		if (!(share > 0))
			return;
		Enum_LayerKind kind = page.get(index).kind;
		dissolving = kind != Enum_LayerKind.EMPTY && kind != Enum_LayerKind.PARTICLES
				&& getLayerEffects().setDissolve(incoming ? LayerEffects.DISSOLVE_INCOMING : LayerEffects.DISSOLVE_OUTGOING, ramp,
						transfertStyle.getSoftness(), TransfertStyle.drift(effectTime), transfertStyle.getPatches(),
						transfertStyle.cellsUp(viewWidth, viewHeight));
		dissolveFallback = share;
		if (setBatchColor(batch, dissolving ? 1 : share))
			drawLayer(page, index, batch, incoming);
		if (dissolving)
			effects.setDissolve(LayerEffects.DISSOLVE_NONE, 0, 0, 0, 0, 0);
		dissolving = false;
	}

	/**
	 * A transfert through a colour's or a fog creep's layer: an IMAGE, SEQUENCE or SHADER one at full opacity, mixed
	 * toward the colour by the engine by {@code grade} ({@link TransfertStyle#gradeAt}); an EMPTY or PARTICLES one, or any
	 * when the engine draws no grade, faded out by as much, to the gradients going through the colour behind it.
	 */
	private void drawGraded(ArrayList<ParallaxLayer> page, int index, Batch batch, boolean incoming, float grade)
	{
		Enum_LayerKind kind = page.get(index).kind;
		grading = kind != Enum_LayerKind.EMPTY && kind != Enum_LayerKind.PARTICLES
				&& getLayerEffects().setGrade(transfertStyle.getColorR(), transfertStyle.getColorG(), transfertStyle.getColorB(), grade);
		gradeFallback = 1 - grade;
		if (setBatchColor(batch, grading ? 1 : gradeFallback))
			drawLayer(page, index, batch, incoming);
		if (grading)
			effects.setGrade(0, 0, 0, 0);
		grading = false;
	}

	/** Puts the game's shader back before a hook or particles, which draw with it. */
	private void endPageFog(Batch batch)
	{
		if (pageFogOn)
			effects.endPageFog(batch);
		pageFogOn = false;
	}

	/** Lets the engine's effects put back a shader they kept bound across layers: before a layer they do not draw. */
	private void release(Batch batch)
	{
		if (effects != null)
			effects.release(batch);
	}

	/** Sets the tint at that opacity, and says whether anything drawn with it would show. */
	private boolean setBatchColor(Batch batch, float alpha)
	{
		float a = tint.a * alpha;
		batch.setColor(tint.r, tint.g, tint.b, a);
		return a > 0;
	}

	/**
	 * How much of the fog's colour a layer is mixed toward, 0 to 1: {@code 1 - exp(-strength * (1 / speed - 1 / front))},
	 * speed the layer's speed ratio X and front the page's fastest ({@link #frontSpeedOf}), both as magnitudes: the
	 * slower a layer scrolls, the farther it is, and the more it is fogged. 0 for the front layer, for no fog, and for a
	 * page that does not scroll; 1 for a still layer in front of a scrolling one.
	 */
	public static float fogOf(float strength, float front, ParallaxLayer layer)
	{
		if (!(strength > 0) || !(front > 0))
			return 0;
		float speed = Math.abs(layer.getParallaxSpeedRatioX());
		if (!(speed > 0))
			return 1;
		float depth = 1 / speed - 1 / front;
		return depth > 0 ? 1 - (float) Math.exp(-strength * depth) : 0;
	}

	/** The fastest speed ratio X of {@code page}, as a magnitude: its front, for {@link #fogOf}. 0 for no layer. */
	/**
	 * The tower of {@code page} seen from close by ({@link ParallaxLayer#isNearTower}), the fastest when there are more;
	 * null when none: the page's layers are drawn flat behind its towers.
	 */
	public static ParallaxLayer nearTowerOf(List<ParallaxLayer> page)
	{
		ParallaxLayer tower = null;
		for (int i = 0, n = page.size(); i < n; i++)
		{
			ParallaxLayer layer = page.get(i);
			if (layer.isNearTower() && (tower == null || Math.abs(layer.getParallaxSpeedRatioX()) > Math.abs(tower.getParallaxSpeedRatioX())))
				tower = layer;
		}
		return tower;
	}

	/**
	 * The radius, in world units, of the panorama {@code layer} is wrapped in round {@code tower} (r350,
	 * {@link PanoramaBatch}); 0 when it is drawn flat. An IMAGE, SHADER or SEQUENCE layer slower than the tower and not
	 * still is behind it. Seen from the camera {@code D} from the axis ({@link ParallaxLayer#getTowerCamera} radii
	 * {@code r}), the tower's front is {@code f = D - r} away and the layer as far as its speed says, {@code f s_tower / s}
	 * (a speed is 1 / distance); the middle of its far side, so {@code R = f s_tower / s - D}, at least {@code D}: the
	 * camera stays inside every panorama, which then covers the view.
	 * A still layer is the sky; FLOOR, PARTICLES and EMPTY layers and other towers stay flat.
	 */
	public static float panoramaRadiusOf(ParallaxLayer tower, ParallaxLayer layer)
	{
		if (tower == null || layer == tower || layer.isTower())
			return 0;
		Enum_LayerKind kind = layer.getKind();
		if (kind != Enum_LayerKind.IMAGE && kind != Enum_LayerKind.SHADER && kind != Enum_LayerKind.SEQUENCE)
			return 0;
		float speed = Math.abs(layer.getParallaxSpeedRatioX()), towerSpeed = Math.abs(tower.getParallaxSpeedRatioX());
		if (!(speed > 0) || !(speed < towerSpeed))
			return 0;
		float camera = towerCameraOf(tower);
		return Math.max(camera, (camera - towerRadiusOf(tower)) * towerSpeed / speed - camera);
	}

	/** How far the camera looking at {@code tower} stands from its axis, in world units. */
	static float towerCameraOf(ParallaxLayer tower)
	{return towerRadiusOf(tower) * tower.getTowerCamera();}

	/** {@code tower}'s radius in world units. */
	static float towerRadiusOf(ParallaxLayer tower)
	{return tower.getTowerRadius() * (tower.getWorldWidth() / 100);}

	public static float frontSpeedOf(List<ParallaxLayer> page)
	{
		float front = 0;
		for (int i = 0, n = page.size(); i < n; i++)
			front = Math.max(front, Math.abs(page.get(i).getParallaxSpeedRatioX()));
		return front;
	}

	/**
	 * Format 9's depth haze, which the reader no longer draws (r217: the page's fog replaced it); kept for the haze lab.
	 * How much of the mist's white the layer at {@code index} of {@code page} (back to front) is mixed toward: 1 minus
	 * the product, over every FOG layer in front of it, of {@code (1 - haze)} once per step between them. One FOG layer
	 * of haze h, n layers in front: {@code 1 - (1 - h)^n}. 0 for a layer with no hazy FOG in front of it.
	 */
	@Deprecated
	public static float hazeOf(List<ParallaxLayer> page, int index)
	{
		// step: the product of (1 - haze) of the FOG layers passed; keep: what is left of the color, one more step back.
		float keep = 1, step = 1;
		for (int i = page.size() - 1; i > index; i--)
		{
			float haze = page.get(i).getHazePerLayer();
			if (haze > 0)
				step *= 1 - haze;
			keep *= step;
		}
		return 1 - keep;
	}

	/**
	 * The view at {@code zoom} about the camera's middle: what the layers are tiled across, and the effects laid on;
	 * where the layers are placed from in it: its corner, or dollied, the corner of the view at zoom 1.
	 */
	private void view(float zoom)
	{
		viewWidth = viewportWidth * zoom;
		viewHeight = viewportHeight * zoom;
		viewLeft = cameraX - viewWidth / 2;
		viewBottom = cameraY - viewHeight / 2;
		anchorLeft = dolly ? cameraX - viewportWidth / 2 : viewLeft;
		anchorBottom = dolly ? cameraY - viewportHeight / 2 : viewBottom;
		getLayerEffects().setView(viewWidth, viewHeight, anchorLeft - viewLeft, drawingHeight + anchorBottom - viewBottom);
		getLayerEffects().setRepeat(repeatOnX, repeatOnY);
	}

	/**
	 * Draws a layer. Dollied ({@link #setDolly}), it is placed from the corner of the view at zoom 1, so that a zoom
	 * scales it about the view's middle, and at a zoom other than the camera's it is tiled across its own view and drawn
	 * through {@link #dollyBatch} into the camera's: the effects keep the game's batch.
	 */
	private void drawLayer(ArrayList<ParallaxLayer> page, int index, Batch batch, boolean incoming)
	{
		ParallaxLayer layer = page.get(index);
		if (layer.kind == Enum_LayerKind.LURKER && !layer.isLurkerThere())
			return;
		float zoom = dolly ? dollyZoom(cameraZoom, incoming ? transferFrontSpeed : frontSpeed, layer) : cameraZoom;
		Batch drawn = batch;
		if (zoom != cameraZoom)
		{
			view(zoom);
			drawn = dollyBatch.set(batch, cameraZoom / zoom, cameraX, cameraY);
		}
		float originX = anchorLeft + layer.currentDistanceX;
		float originY = anchorBottom + drawingHeight + layer.currentDistanceY;
		if (layer.kind == Enum_LayerKind.EMPTY)
			drawHookLayer(layer, batch, drawn, originX, originY);
		else if (layer.kind == Enum_LayerKind.PARTICLES)
			drawParticleLayer(layer, batch, drawn, originX, originY, incoming);
		else
			drawImageLayer(layer, batch, drawn, originX, originY, incoming);
		if (drawn != batch)
			view(cameraZoom);
	}

	/**
	 * The zoom a dollied layer is drawn at, the camera's being {@code zoom} and its page's fastest speed ratio X
	 * {@code front}: {@code 1 - k (1 - zoom)}, k the layer's speed ratio X over the front's, as magnitudes. That is a
	 * camera moving in or out, each layer as far as its speed says (a parallax speed is 1 / distance): the front layer
	 * at the camera's zoom, as without the dolly, a still one at 1, whatever the zoom; the layers between grow less the
	 * farther they are. The camera's zoom for every layer of a page that does not scroll.
	 */
	public static float dollyZoom(float zoom, float front, ParallaxLayer layer)
	{
		if (!(front > 0))
			return zoom;
		float k = Math.min(1, Math.abs(layer.getParallaxSpeedRatioX()) / front);
		return 1 - k * (1 - zoom);
	}

	/** An EMPTY layer: the game's hook, once per tile the view shows, its tile's corner at {@code (originX, originY)}. */
	private void drawHookLayer(ParallaxLayer layer, Batch batch, Batch drawn, float originX, float originY)
	{
		// No mirrored copy: the hook draws what it likes in each tile. Its color changes stay its own.
		LayerHook hook = layer.name == null ? null : hooks.get(layer.name);
		if (hook != null)
		{
			endPageFog(batch);
			release(batch);
			hookColor.set(batch.getColor());
			tile(layer, drawn, originX, originY, repeatOnX, repeatOnY, false, hook);
			batch.setColor(hookColor);
		}
	}

	/**
	 * A PARTICLES layer: its effect from the view's corner plus its decal (VIEW), or in each tile (LAYER); round a planet,
	 * each effect turned as one block, moved and turned as the middle of its layer's box is ({@link PlanetBatch#turn},
	 * r347): a box's middle is known to every engine, the live particles' are not.
	 */
	private void drawParticleLayer(ParallaxLayer layer, Batch batch, Batch drawn, float originX, float originY, boolean incoming)
	{
		ParallaxParticles particles = layer.getParticles();
		if (particles == null || particles.isEmpty())
			return;
		endPageFog(batch);
		release(batch);
		int srcColor = batch.getBlendSrcFunc(), dstColor = batch.getBlendDstFunc();
		int srcAlpha = batch.getBlendSrcFuncAlpha(), dstAlpha = batch.getBlendDstFuncAlpha();
		particlePlanet = planetInView(incoming);
		if (layer.getAnchor() == Enum_ParticleAnchor.VIEW)
		{
			float x = anchorLeft + layer.getDecalPercentX() * (layer.getWorldWidth() / 100);
			float y = anchorBottom + drawingHeight + layer.getDecalPercentY() * (layer.getWorldHeight() / 100);
			// Round a planet, where the middle of its box goes, as far down and across as it goes.
			float midX = x + layer.getWidth() / 2, midY = y + layer.getHeight() / 2, dx = 0, dy = 0;
			Batch to = drawn;
			if (particlePlanet > 0)
			{
				to = planetBatch.set(drawn, particlePlanet, viewLeft, viewBottom, viewWidth).turn(midX, midY);
				dx = PlanetBatch.placeX(particlePlanet, viewLeft + viewWidth / 2, viewBottom, midX, midY) - midX;
				dy = PlanetBatch.placeY(particlePlanet, viewLeft + viewWidth / 2, viewBottom, midX, midY) - midY;
			}
			if (x + dx + particles.getMaxX() > viewLeft && x + dx + particles.getMinX() < viewLeft + viewWidth
					&& y + dy + particles.getMaxY() > viewBottom && y + dy + particles.getMinY() < viewBottom + viewHeight)
				particles.draw(to, x, y, batch.getColor());
		}
		else
			tile(layer, drawn, originX, originY, repeatOnX, repeatOnY, false, null);
		// The emitters set the blend function their .p asks for: the next layer is drawn with the game's.
		batch.setBlendFunctionSeparate(srcColor, dstColor, srcAlpha, dstAlpha);
	}

	/**
	 * An IMAGE, SHADER, SEQUENCE or FLOOR layer: through the page fog's shader or the layer's effect (or faded, when the
	 * engine has none for a dissolve or a grade), tiled, its mirrored copy stacked beside it.
	 */
	private void drawImageLayer(ParallaxLayer layer, Batch batch, Batch drawn, float originX, float originY, boolean incoming)
	{
		// The fog is drawn on images: an EMPTY or PARTICLES layer is drawn as is.
		float fog = incoming ? fogOf(transferFogStrength, transferFrontSpeed, layer) : fogOf(fogStrength, frontSpeed, layer);
		boolean shaded, packed = false;
		// The animated patch's frame, on the clock the effects run on: drawn after each tile (ParallaxLayer.draw). The
		// glint pulses by it: set before the effect begins.
		layer.setAnimTime(effectTime);
		if (pageFog && layer.kind != Enum_LayerKind.SHADER)
		{
			// The page fog's shader, the layer's fog and page in the batch color: no flush from one layer to the next.
			if (!pageFogOn)
				pageFogOn = effects.beginPageFog(batch, tint, fogColor, transferFogColor);
			release(batch);
			packedAlpha = batch.getColor().a;
			packed = pageFogOn;
			if (packed)
				batch.setColor(fog, incoming ? 1 : 0, 0, packedAlpha);
			shaded = false;
		}
		else
		{
			shaded = (layer.kind == Enum_LayerKind.SHADER || fog > 0 || dissolving || grading)
					&& getLayerEffects().begin(batch, layer, layer.getShaderPhase(effectTime), fog, incoming ? transferFogColor : fogColor);
			if (!shaded)
			{
				release(batch);
				if (dissolving)
					// The engine could not mask it: the layer fades to its share instead.
					setBatchColor(batch, dissolveFallback);
				else if (grading)
					// Nor mix it toward the colour: the layer fades out to the gradients instead.
					setBatchColor(batch, gradeFallback);
			}
		}

		boolean tower = layer.isTower();
		// Behind a tower seen from close by: a panorama round its axis, cut in strips, each placed and scaled.
		ParallaxLayer near = incoming ? transferNearTower : nearTower;
		float panorama = panoramaRadiusOf(near, layer);
		// Round the page's planet: its tiles and its mirrored copy cut in strips, each wrapped round it. Not a floor, a
		// tower or a panorama, nor a lurker (drawn flat until asked, docs/lurker-layers.md).
		float planet = layer.kind == Enum_LayerKind.FLOOR || layer.kind == Enum_LayerKind.LURKER || tower || panorama > 0 ? 0 : planetInView(incoming);
		if (panorama > 0)
		{
			// At its own scale: the camera's distance to the tower's front, f, over the panorama's middle's, D + R.
			float camera = towerCameraOf(near), front = camera - towerRadiusOf(near);
			drawn = panoramaBatch.set(drawn, front * panorama / (camera + panorama), camera / panorama, viewLeft + viewWidth / 2,
					viewBottom + viewHeight / 2, viewLeft, viewWidth);
		}
		else if (planet > 0)
		{
			drawn = planetBatch.set(drawn, planet, viewLeft, viewBottom, viewWidth);
			planetReach = planetReachOf(planet, viewWidth, viewHeight);
		}
		if (layer.kind == Enum_LayerKind.FLOOR)
			// Its rows across the whole view, whatever the page repeats; no mirrored copy.
			layer.drawFloor(drawn, originY, viewLeft, viewLeft + viewWidth, viewBottom, viewBottom + viewHeight);
		else if (tower)
			// Round its tower in the view's middle, tiled up as the page says; no mirrored copy.
			towerRows(layer, drawn, originX, originY, packed);
		else if (shaded && layer.kind == Enum_LayerKind.SHADER && layer.hasAnim())
		{
			// Its patches after all its tiles, through its effect given the frame's region in the layer's (r397): the
			// effect keeps its samples inside the region it is given, and in the layer's the patch draws the strip.
			layer.setDrawPart(ParallaxLayer.DRAW_IMAGE);
			tileAndMirror(layer, drawn, originX, originY);
			effects.beginPatch(batch, layer);
			layer.setDrawPart(ParallaxLayer.DRAW_PATCH);
			tileAndMirror(layer, drawn, originX, originY);
			layer.setDrawPart(ParallaxLayer.DRAW_ALL);
		}
		else
			tileAndMirror(layer, drawn, originX, originY);

		planetReach = 0;
		if (shaded)
			effects.end(batch, layer);
		else if (packed)
			// The next layer is drawn with the tint at this alpha, which draw() sets only once outside a cross-fade.
			batch.setColor(tint.r, tint.g, tint.b, packedAlpha);
	}

	/**
	 * The layer tiled as the page repeats, and its mirrored copy stacked next to the tiled strip: above it when tiling on
	 * X, to its right on Y.
	 */
	private void tileAndMirror(ParallaxLayer layer, Batch drawn, float originX, float originY)
	{
		tile(layer, drawn, originX, originY, repeatOnX, repeatOnY, false, null);
		if (layer.isMirror && repeatOnX != repeatOnY)
		{
			if (repeatOnX)
				tile(layer, drawn, originX, originY + layer.padY + layer.getHeight(), true, false, true, null);
			else
				tile(layer, drawn, originX + layer.padX + layer.getWidth(), originY, false, true, true, null);
		}
	}

	/**
	 * A tower layer ({@link ParallaxLayer#drawTower}) round its axis in the view's middle, its tile at {@code (x, y)}: once,
	 * or on a page repeating on Y every repetition up that intersects the view; round the tower it repeats whatever the
	 * page does.
	 */
	private void towerRows(ParallaxLayer layer, Batch batch, float x, float y, boolean packed)
	{
		float height = layer.getHeight(), stepY = layer.getTotalHeight();
		if (!(height > 0))
			return;
		boolean tileY = repeatOnY && stepY > 0;
		float startY = tileY ? y - (float) Math.ceil((y + height - viewBottom) / stepY) * stepY : y;
		int countY = tileY ? (int) Math.ceil((viewBottom + viewHeight - startY) / stepY) + 1 : 1;
		for (int row = 0; row < countY; row++)
		{
			float drawY = startY + row * stepY;
			if (drawY + height <= viewBottom || drawY >= viewBottom + viewHeight)
				continue;
			layer.drawTower(batch, viewLeft + viewWidth / 2, x, drawY, viewLeft, viewLeft + viewWidth, viewBottom + viewHeight / 2, packed);
		}
	}

	/**
	 * Draws the layer at {@code (x, y)} plus every repetition, on the requested axes, that intersects the view; through
	 * {@code hook} when it is not null. A PARTICLES layer's tile is where its particles are, which may reach past its box.
	 */
	private void tile(ParallaxLayer layer, Batch batch, float x, float y, boolean onX, boolean onY, boolean mirror, LayerHook hook)
	{
		float width = layer.getWidth(), height = layer.getHeight();
		boolean sequence = layer.kind == Enum_LayerKind.SEQUENCE;
		// A SEQUENCE layer whose pads outweigh its segments is no wider than 0, its slots still drawn: once, below.
		if ((width <= 0 && !sequence) || height <= 0)
			return;

		ParallaxParticles particles = layer.kind == Enum_LayerKind.PARTICLES ? layer.getParticles() : null;
		// What is drawn of a tile, from its corner: the box, the particles, or a SEQUENCE layer's slots.
		float left = particles != null ? particles.getMinX() : sequence ? layer.getCycleLeft() : 0;
		float right = particles != null ? particles.getMaxX() : sequence ? layer.getCycleRight() : width;
		float bottom = particles == null ? 0 : particles.getMinY(), top = particles == null ? height : particles.getMaxY();
		if (particles != null && particlePlanet > 0)
			// Round a planet each tile's effect is turned as its box's middle is: lowered, at most, as the ground is under
			// one whose particles just reach into the view at its side.
			bottom -= particleDrop(viewLeft - Math.max(right - width / 2, width / 2 - left));
		// Round a planet the view's top corners show what lies a little over it.
		float viewTop = viewBottom + viewHeight + planetReach;

		// A step <= 0 (e.g. a negative padding larger than the image) can't tile: draw the layer once.
		float stepX = layer.getTotalWidth(), stepY = layer.getTotalHeight();
		boolean tileX = onX && stepX > 0, tileY = onY && stepY > 0;

		float startX = tileX ? x - (float) Math.ceil((x + right - viewLeft) / stepX) * stepX : x;
		float startY = tileY ? y - (float) Math.ceil((y + top - viewBottom) / stepY) * stepY : y;
		int countX = tileX ? (int) Math.ceil((viewLeft + viewWidth - left - startX) / stepX) + 1 : 1;
		int countY = tileY ? (int) Math.ceil((viewTop - bottom - startY) / stepY) + 1 : 1;

		for (int row = 0; row < countY; row++)
		{
			float drawY = startY + row * stepY;
			if (drawY + top <= viewBottom || drawY + bottom >= viewTop)
				continue;

			for (int column = 0; column < countX; column++)
			{
				float drawX = startX + column * stepX;
				if (drawX + right <= viewLeft || drawX + left >= viewLeft + viewWidth)
					continue;

				if (particles != null)
				{
					if (particlePlanet > 0)
						particles.draw(planetBatch.set(batch, particlePlanet, viewLeft, viewBottom, viewWidth)
								.turn(drawX + width / 2, drawY + height / 2), drawX, drawY, batch.getColor());
					else
						particles.draw(batch, drawX, drawY, batch.getColor());
				}
				else if (layer.kind == Enum_LayerKind.SEQUENCE)
					layer.drawCycle(batch, drawX, drawY, viewLeft, viewLeft + viewWidth,
							mirror && !onX ? !layer.flipX : layer.flipX, mirror && onX ? !layer.flipY : layer.flipY);
				else if (hook != null)
					hook.draw(batch, layer, drawX, drawY, width, height);
				else if (mirror)
					layer.drawMirror(batch, drawX, drawY, onX);
				else
					layer.draw(batch, drawX, drawY);
			}
		}
	}

	/** How far the ground under {@code x} is lowered round the planet of the PARTICLES layer being drawn. */
	private float particleDrop(float x)
	{
		return viewBottom - PlanetBatch.placeY(particlePlanet, viewLeft + viewWidth / 2, viewBottom, x, viewBottom);
	}

	public void resetTransfert()
	{
		// An interrupted cross-fade's layers are dropped; a finished one's are the layers on screen.
		if (transferLayers != layers)
			release(transferLayers);
		transferLayers = new ArrayList<>();
		transfertType = Enum_TransfertType.NONE;
		transfertStyle = TransfertStyle.FADE;
		newLayerAlpha = 0;
		oldLayerAlpha = 1;
	}

	public void resetPositions()
	{
		for (int i = 0, n = layers.size(); i < n; i++)
			layers.get(i).resetPosition();
	}

	public void act(float delta, float speedX, float speedY)
	{
		effectTime += delta;
		plant(layers);
		plant(transferLayers);
		for (int i = 0, n = layers.size(); i < n; i++)
			layers.get(i).act(delta, speedX, speedY, repeatOnX, repeatOnY);

		if (transfertType != Enum_TransfertType.NONE)
		{
			for (int i = 0, n = transferLayers.size(); i < n; i++)
				transferLayers.get(i).act(delta, speedX, speedY, repeatOnX, repeatOnY);

			newLayerAlpha = Math.min(1, newLayerAlpha + delta * newLayerFadeSpeed);
			oldLayerAlpha = Math.max(0, oldLayerAlpha - delta * oldLayerFadeSpeed);

			if (newLayerAlpha >= 1)
			{
				release(layers);
				layers = transferLayers;
				takeTransferFog();
				resetTransfert();
			}
		}

		if (tintElapsed < tintDuration)
		{
			tintElapsed = Math.min(tintDuration, tintElapsed + delta);
			tint.set(tintFrom).lerp(tintTo, tintElapsed / tintDuration);
		}
		// Every step, drawn or not: a sighting between two draws counts. Against the last draw's view.
		sightLurkers();
	}

	/** Runs the sightings of every LURKER layer on screen, of both pages during a cross-fade. */
	private void sightLurkers()
	{
		sightLurkers(layers, false);
		if (transferLayers != layers)
			sightLurkers(transferLayers, true);
	}

	private void sightLurkers(ArrayList<ParallaxLayer> page, boolean incoming)
	{
		for (int i = 0, n = page.size(); i < n; i++)
		{
			ParallaxLayer figure = page.get(i);
			if (figure.kind != Enum_LayerKind.LURKER)
				continue;
			if (!figure.lurkerStarted)
				startLurker(figure);
			boolean inSight = lurkerInSight(page, i, incoming);
			if (inSight && figure.lurkerThere)
				figure.lurkerSeen = true;
			else if (!inSight && figure.lurkerInSight)
				hideLurker(figure);
			figure.lurkerInSight = inSight;
		}
	}

	/** A LURKER layer's first draw, from its seed (0: SequenceCycle's zero seed), as each later one: rule 2. */
	private static void startLurker(ParallaxLayer figure)
	{
		figure.lurkerState = figure.getLurkerSeed() == 0 ? SequenceCycle.ZERO_SEED : figure.getLurkerSeed();
		figure.lurkerState = SequenceCycle.next(figure.lurkerState);
		figure.lurkerThere = (figure.lurkerState >>> 1) % figure.getLurkerOdds() == 0;
		figure.lurkerSeen = figure.lurkerInSight = figure.lurkerForced = false;
		figure.lurkerStarted = true;
	}

	/**
	 * A sighting has ended: whether the figure is there at the next one. Forced by {@link #reveal}; else gone after it was
	 * seen; else the state steps once and it is there by 1 chance in its odds. Integers only: every engine draws the same.
	 */
	private static void hideLurker(ParallaxLayer figure)
	{
		if (figure.lurkerForced)
			figure.lurkerThere = true;
		else if (figure.lurkerSeen)
			figure.lurkerThere = false;
		else
		{
			figure.lurkerState = SequenceCycle.next(figure.lurkerState);
			figure.lurkerThere = (figure.lurkerState >>> 1) % figure.getLurkerOdds() == 0;
		}
		figure.lurkerForced = figure.lurkerSeen = false;
	}

	/**
	 * Whether a copy of the LURKER layer {@code index} of {@code page} is in the view (its tile's box, in its own view when
	 * dollied) and not wholly covered by one layer in front of it ({@link ParallaxLayer#getLurkerCover}). The cover counts
	 * only where it was worked out for: a page repeating on X, the figure's own row, nothing turning or fading the layers in
	 * front (no planet, panorama, dolly away from zoom 1, cross-fade nor tint below full opacity), and the layers' Y apart
	 * as saved. Out of it, the figure is hidden only out of the view. Allocates nothing.
	 */
	private boolean lurkerInSight(ArrayList<ParallaxLayer> page, int index, boolean incoming)
	{
		ParallaxLayer figure = page.get(index);
		float width = figure.getWidth(), height = figure.getHeight();
		if (!(width > 0) || !(height > 0) || !(viewportWidth > 0))
			return false;
		float zoom = dolly ? dollyZoom(cameraZoom, frontSpeedOf(page), figure) : cameraZoom;
		float vw = viewportWidth * zoom, vh = viewportHeight * zoom, vl = cameraX - vw / 2, vb = cameraY - vh / 2;
		float al = dolly ? cameraX - viewportWidth / 2 : vl, ab = dolly ? cameraY - viewportHeight / 2 : vb;
		float originX = al + figure.currentDistanceX, originY = ab + drawingHeight + figure.currentDistanceY;
		float[] cover = figure.getLurkerCover();
		boolean covers = repeatOnX && cover.length > 0 && !((incoming ? transferPlanetRadius : planetRadius) > 0)
				&& nearTowerOf(page) == null && (!dolly || cameraZoom == 1) && transferLayers.isEmpty() && tint.a >= 1;

		float stepX = figure.getTotalWidth(), stepY = figure.getTotalHeight();
		boolean tileX = repeatOnX && stepX > 0, tileY = repeatOnY && stepY > 0;
		int firstColumn = tileX ? -(int) Math.ceil((originX + width - vl) / stepX) : 0;
		int firstRow = tileY ? -(int) Math.ceil((originY + height - vb) / stepY) : 0;
		for (int row = firstRow; ; row++)
		{
			float y = originY + row * stepY;
			if (row > firstRow && (!tileY || y >= vb + vh))
				break;
			if (y + height <= vb || y >= vb + vh)
				continue;
			for (int column = firstColumn; ; column++)
			{
				float x = originX + column * stepX;
				if (column > firstColumn && (!tileX || x >= vl + vw))
					break;
				if (x + width <= vl || x >= vl + vw)
					continue;
				if (!(covers && row == 0 && lurkerCovered(page, index, x - al)))
					return true;
			}
		}
		return false;
	}

	/**
	 * Whether a layer in front covers the LURKER layer {@code index}'s copy whose tile corner is {@code x} from the anchor:
	 * its offset {@code d} against that layer's tiles, modulo their period, in one of the cover's intervals.
	 */
	private static boolean lurkerCovered(ArrayList<ParallaxLayer> page, int index, float x)
	{
		ParallaxLayer figure = page.get(index);
		float[] cover = figure.getLurkerCover();
		for (int k = 0; k + 2 < cover.length; k += 3)
		{
			int in = index + (int) cover[k];
			if (in <= index || in >= page.size())
				continue;
			ParallaxLayer near = page.get(in);
			float period = near.getTotalWidth();
			if (!(period > 0))
				continue;
			// Their Y apart as saved, which the sweep took them at.
			float restApart = (figure.getDecalPercentY() * figure.getWorldHeight() - near.getDecalPercentY() * near.getWorldHeight()) / 100;
			if (Math.abs(figure.currentDistanceY - near.currentDistanceY - restApart) > 1e-3f * figure.getHeight())
				continue;
			float d = ((x - near.currentDistanceX) % period + period) % period;
			if (((d - cover[k + 1]) % period + period) % period <= cover[k + 2] - cover[k + 1])
				return true;
		}
		return false;
	}

	/**
	 * Makes the next sighting of every LURKER layer named {@code name} show its figure, whatever its draws say: at once
	 * when it is not in sight now, else from the end of this sighting. A game's sure first scare (docs/lurker-layers.md).
	 */
	public void reveal(String name)
	{
		reveal(layers, name);
		if (transferLayers != layers)
			reveal(transferLayers, name);
	}

	private static void reveal(ArrayList<ParallaxLayer> page, String name)
	{
		for (int i = 0, n = page.size(); i < n; i++)
		{
			ParallaxLayer figure = page.get(i);
			if (figure.kind != Enum_LayerKind.LURKER || name == null || !name.equals(figure.name))
				continue;
			if (!figure.lurkerStarted)
				startLurker(figure);
			if (figure.lurkerInSight)
				figure.lurkerForced = true;
			else
				figure.lurkerThere = true;
		}
	}

	/**
	 * Plants every layer of {@code page} in front of its last FLOOR layer on it (r275, Simon: the trees slid on the
	 * ground until they moved with it): such a layer scrolls at the speed of the floor's row under its foot
	 * ({@link ParallaxLayer#getFootY}), as a thing standing on the ground does, the floor's box taken at both decals. A
	 * foot below the box stands on its bottom row; a foot above its top row stands on no floor, and keeps its own speed (a
	 * foreground hanging from the top of the view). Every other layer keeps its own. Run by act(), every frame, so a
	 * speed the editor changes plants at once; allocates nothing.
	 */
	public static void plant(List<ParallaxLayer> page)
	{
		int floor = -1;
		for (int i = 0, n = page.size(); i < n; i++)
			if (page.get(i).kind == Enum_LayerKind.FLOOR)
				floor = i;
		for (int i = 0, n = page.size(); i < n; i++)
			page.get(i).setPlantedSpeedX(i > floor && floor >= 0 ? plantedSpeedOn(page.get(floor), page.get(i)) : Float.NaN);
	}

	/** The speed of {@code floor}'s row under {@code layer}'s foot, NaN when its foot is above the floor's top row. */
	public static float plantedSpeedOn(ParallaxLayer floor, ParallaxLayer layer)
	{
		float bottom = floor.getDecalPercentY() * (floor.getWorldHeight() / 100), height = floor.getHeight();
		float t = height > 0 ? (layer.getFootY() - bottom) / height : 0;
		if (t > 1)
			return Float.NaN;
		t = Math.max(0, t);
		return floor.getParallaxSpeedRatioX() + (floor.getFloorTopSpeed() - floor.getParallaxSpeedRatioX()) * t;
	}

	/**
	 * Makes {@code hook} draw every EMPTY layer named {@code name}, in this reader's pages and the pages it fades into;
	 * null removes it. A layer with no hook draws nothing.
	 */
	public void setLayerHook(String name, LayerHook hook)
	{
		if (hook == null)
			hooks.remove(name);
		else
			hooks.put(name, hook);
	}

	public LayerHook getLayerHook(String name)
	{return hooks.get(name);}

	/**
	 * Draws every SEQUENCE layer's cycle, in this reader's pages and the pages it fades into, from the game's
	 * {@code seed} rather than the page's: a new ground each run. Each layer draws from {@code seed} XOR the seed its page
	 * stores, so two layers stored with different seeds stay different. Redraws the cycles of the layers on screen.
	 */
	public void setSequenceSeed(int seed)
	{
		sequenceSeed = seed;
		hasSequenceSeed = true;
		drawCycles();
	}

	/** Back to the seeds the pages store: what the editor previews. */
	public void clearSequenceSeed()
	{
		hasSequenceSeed = false;
		drawCycles();
	}

	/** The game's seed, or null when the layers draw from their pages' seeds. */
	public Integer getSequenceSeed()
	{return hasSequenceSeed ? sequenceSeed : null;}

	private void drawCycles()
	{
		for (int i = 0, n = layers.size(); i < n; i++)
			drawCycle(layers.get(i));
		for (int i = 0, n = transferLayers.size(); i < n; i++)
			drawCycle(transferLayers.get(i));
	}

	/** Draws a SEQUENCE layer's cycle from the seed this reader gives it, when it was drawn from another. */
	private void drawCycle(ParallaxLayer layer)
	{
		if (layer.kind != Enum_LayerKind.SEQUENCE)
			return;
		int seed = hasSequenceSeed ? sequenceSeed ^ layer.getSequenceSeed() : layer.getSequenceSeed();
		if (seed != layer.getDrawnSeed())
			layer.drawCycleFrom(seed);
	}

	/**
	 * Sets what draws the SHADER layers' effects: a jME game's PlaxBackground sets its own, a test a recorder. Left
	 * unset, the reader makes a {@link GdxLayerEffects} when it first draws one, and disposes it in {@link #dispose()};
	 * one set here is the caller's to dispose.
	 */
	public void setLayerEffects(LayerEffects effects)
	{
		dispose();
		this.effects = effects;
		ownsEffects = false;
	}

	/** What draws the SHADER layers; a {@link GdxLayerEffects} made now when none was set. */
	public LayerEffects getLayerEffects()
	{
		if (effects == null)
		{
			effects = new GdxLayerEffects();
			ownsEffects = true;
		}
		return effects;
	}

	/** Seconds acted since the reader was made: where the SHADER layers' effects are. */
	public double getEffectTime()
	{return effectTime;}

	/** Disposes the shaders this reader made for its SHADER layers; it makes them again if it draws one later. */
	@Override
	public void dispose()
	{
		if (ownsEffects && effects instanceof Disposable)
			((Disposable) effects).dispose();
		if (ownsEffects)
			effects = null;
		ownsEffects = false;
	}

	public float getWorldWidth()
	{return worldWidth;}

	public float getWorldHeight()
	{return worldHeight;}

	/**
	 * Sets the world the layers are placed in (their decal percentages are taken of it), for this reader only: the
	 * {@link Gvars_Parallax} size is just the default of a new reader. Moves no layer, like
	 * {@link ParallaxLayer#setWorldSize}.
	 */
	public void setWorldSize(float worldWidth, float worldHeight)
	{
		this.worldWidth = worldWidth;
		this.worldHeight = worldHeight;
		for (int i = 0, n = layers.size(); i < n; i++)
			layers.get(i).setWorldSize(worldWidth, worldHeight);
		for (int i = 0, n = transferLayers.size(); i < n; i++)
			transferLayers.get(i).setWorldSize(worldWidth, worldHeight);
	}

	public boolean isInTransfer()
	{return transfertType != Enum_TransfertType.NONE;}

	/** How the transfert under way is drawn; {@link TransfertStyle#FADE} outside one. */
	public TransfertStyle getTransfertStyle()
	{return transfertStyle;}

	public float getDrawingHeight()
	{return drawingHeight;}

	public void setDrawingHeight(float drawingHeight)
	{this.drawingHeight = drawingHeight;}

	public Color getTint()
	{return tint;}

	public boolean isRepeatOnX()
	{return repeatOnX;}

	public void setRepeatOnX(boolean repeatOnX)
	{this.repeatOnX = repeatOnX;}

	public boolean isRepeatOnY()
	{return repeatOnY;}

	public void setRepeatOnY(boolean repeatOnY)
	{this.repeatOnY = repeatOnY;}

	/**
	 * The dolly (r278, docs/depth-tricks.md): with it, the camera's zoom scales each layer by its depth, the near ones
	 * more, the sky not at all ({@link #dollyZoom}), all about the view's middle; without it (the default) the page
	 * zooms as one picture, about the view's bottom left corner, where its layers are placed from. Nothing stored: a
	 * game's switch, for a camera that zooms.
	 */
	public void setDolly(boolean dolly)
	{this.dolly = dolly;}

	public boolean isDolly()
	{return dolly;}
}
