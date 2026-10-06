package jks.tools2d.parallax;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
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
 * the {@link LayerEffects} of the engine ({@link #setLayerEffects}), at a phase taken from this reader's clock. A FOG
 * layer with a depth haze mixes every layer of its page behind it toward the mist's white ({@link #hazeOf}), through
 * the same effects. A
 * SEQUENCE layer's cycle is the one its page stores the seed of, unless the game passes its own
 * ({@link #setSequenceSeed}).
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

	/** The game's seed for the SEQUENCE layers, when {@link #hasSequenceSeed}: see {@link #setSequenceSeed}. */
	private int sequenceSeed;
	private boolean hasSequenceSeed;

	// Visible area of the camera, refreshed each draw.
	private float viewLeft, viewBottom, viewWidth, viewHeight;

	public void addLayers(List<ParallaxLayer> newLayers)
	{
		for (ParallaxLayer layer : newLayers)
		{
			layer.setWorldSize(worldWidth, worldHeight);
			drawCycle(layer);
		}
		layers.addAll(newLayers);
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
	{
		resetTransfert();

		List<ParallaxLayer> newLayers = pageModel.getDrawing(relativePath, worldWidth, worldHeight);
		if (newLayers == null || newLayers.isEmpty())
			return;

		// Transferring into the page on screen: its layers must not be moved and drawn twice per frame.
		for (ParallaxLayer layer : newLayers)
		{
			ParallaxLayer incoming = layers.contains(layer) ? layer.clone() : layer;
			incoming.setWorldSize(worldWidth, worldHeight);
			drawCycle(incoming);
			transferLayers.add(incoming);
		}
		syncTransferPositions();

		if (inXSecondes <= 0)
		{
			layers = transferLayers;
			resetTransfert();
			return;
		}

		transfertType = Enum_TransfertType.EACH_FRAME;
		newLayerFadeSpeed = oldLayerFadeSpeed = 1 / inXSecondes;
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
		}
	}

	public void draw(OrthographicCamera worldCamera, Batch batch)
	{
		viewWidth = worldCamera.viewportWidth * worldCamera.zoom;
		viewHeight = worldCamera.viewportHeight * worldCamera.zoom;
		viewLeft = worldCamera.position.x - viewWidth / 2;
		viewBottom = worldCamera.position.y - viewHeight / 2;

		// A layer at alpha 0 still costs its pixels on the GPU, and during a transfer a flush when the pages' atlases differ.
		if (transferLayers.isEmpty())
		{
			if (setBatchColor(batch, 1))
				for (int i = 0, n = layers.size(); i < n; i++)
					drawLayer(layers, i, batch);
		}
		else
		{
			int total = Math.max(layers.size(), transferLayers.size());
			int oldOffset = total - layers.size();
			int newOffset = total - transferLayers.size();

			for (int slot = 0; slot < total; slot++)
			{
				if (slot >= oldOffset && setBatchColor(batch, oldLayerAlpha))
					drawLayer(layers, slot - oldOffset, batch);
				if (slot >= newOffset && setBatchColor(batch, newLayerAlpha))
					drawLayer(transferLayers, slot - newOffset, batch);
			}
		}

		batch.setColor(Color.WHITE);
	}

	/** Sets the tint at that opacity, and says whether anything drawn with it would show. */
	private boolean setBatchColor(Batch batch, float alpha)
	{
		float a = tint.a * alpha;
		batch.setColor(tint.r, tint.g, tint.b, a);
		return a > 0;
	}

	/**
	 * How much of the mist's white the layer at {@code index} of {@code page} (back to front) is mixed toward: 1 minus
	 * the product, over every FOG layer in front of it, of {@code (1 - haze)} once per step between them. One FOG layer
	 * of haze h, n layers in front: {@code 1 - (1 - h)^n}. 0 for a layer with no hazy FOG in front of it.
	 */
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

	private void drawLayer(ArrayList<ParallaxLayer> page, int index, Batch batch)
	{
		ParallaxLayer layer = page.get(index);
		float originX = viewLeft + layer.currentDistanceX;
		float originY = viewBottom + drawingHeight + layer.currentDistanceY;

		if (layer.kind == Enum_LayerKind.EMPTY)
		{
			// No mirrored copy: the hook draws what it likes in each tile. Its color changes stay its own.
			LayerHook hook = layer.name == null ? null : hooks.get(layer.name);
			if (hook != null)
			{
				hookColor.set(batch.getColor());
				tile(layer, batch, originX, originY, repeatOnX, repeatOnY, false, hook);
				batch.setColor(hookColor);
			}
			return;
		}

		if (layer.kind == Enum_LayerKind.PARTICLES)
		{
			ParallaxParticles particles = layer.getParticles();
			if (particles == null || particles.isEmpty())
				return;
			int srcColor = batch.getBlendSrcFunc(), dstColor = batch.getBlendDstFunc();
			int srcAlpha = batch.getBlendSrcFuncAlpha(), dstAlpha = batch.getBlendDstFuncAlpha();
			if (layer.getAnchor() == Enum_ParticleAnchor.VIEW)
			{
				float x = viewLeft + layer.getDecalPercentX() * (layer.getWorldWidth() / 100);
				float y = viewBottom + drawingHeight + layer.getDecalPercentY() * (layer.getWorldHeight() / 100);
				if (x + particles.getMaxX() > viewLeft && x + particles.getMinX() < viewLeft + viewWidth
						&& y + particles.getMaxY() > viewBottom && y + particles.getMinY() < viewBottom + viewHeight)
					particles.draw(batch, x, y, batch.getColor());
			}
			else
				tile(layer, batch, originX, originY, repeatOnX, repeatOnY, false, null);
			// The emitters set the blend function their .p asks for: the next layer is drawn with the game's.
			batch.setBlendFunctionSeparate(srcColor, dstColor, srcAlpha, dstAlpha);
			return;
		}

		// The depth haze is drawn on images: an EMPTY or PARTICLES layer behind a FOG layer counts as a step, drawn as is.
		float haze = hazeOf(page, index);
		boolean shaded = (layer.kind == Enum_LayerKind.SHADER || haze > 0)
				&& getLayerEffects().begin(batch, layer, layer.getShaderPhase(effectTime), haze);

		tile(layer, batch, originX, originY, repeatOnX, repeatOnY, false, null);

		if (layer.isMirror && repeatOnX != repeatOnY)
		{
			// A mirrored copy is stacked next to the tiled strip: above it when tiling on X, to its right on Y.
			if (repeatOnX)
				tile(layer, batch, originX, originY + layer.padY + layer.getHeight(), true, false, true, null);
			else
				tile(layer, batch, originX + layer.padX + layer.getWidth(), originY, false, true, true, null);
		}

		if (shaded)
			effects.end(batch, layer);
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

		// A step <= 0 (e.g. a negative padding larger than the image) can't tile: draw the layer once.
		float stepX = layer.getTotalWidth(), stepY = layer.getTotalHeight();
		boolean tileX = onX && stepX > 0, tileY = onY && stepY > 0;

		float startX = tileX ? x - (float) Math.ceil((x + right - viewLeft) / stepX) * stepX : x;
		float startY = tileY ? y - (float) Math.ceil((y + top - viewBottom) / stepY) * stepY : y;
		int countX = tileX ? (int) Math.ceil((viewLeft + viewWidth - left - startX) / stepX) + 1 : 1;
		int countY = tileY ? (int) Math.ceil((viewBottom + viewHeight - bottom - startY) / stepY) + 1 : 1;

		for (int row = 0; row < countY; row++)
		{
			float drawY = startY + row * stepY;
			if (drawY + top <= viewBottom || drawY + bottom >= viewBottom + viewHeight)
				continue;

			for (int column = 0; column < countX; column++)
			{
				float drawX = startX + column * stepX;
				if (drawX + right <= viewLeft || drawX + left >= viewLeft + viewWidth)
					continue;

				if (particles != null)
					particles.draw(batch, drawX, drawY, batch.getColor());
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

	public void resetTransfert()
	{
		transferLayers = new ArrayList<>();
		transfertType = Enum_TransfertType.NONE;
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
				layers = transferLayers;
				resetTransfert();
			}
		}

		if (tintElapsed < tintDuration)
		{
			tintElapsed = Math.min(tintDuration, tintElapsed + delta);
			tint.set(tintFrom).lerp(tintTo, tintElapsed / tintDuration);
		}
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
}
