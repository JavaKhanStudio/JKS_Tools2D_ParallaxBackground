package jks.tools2d.parallax;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import jks.tools2d.parallax.heart.Gvars_Parallax;
import jks.tools2d.parallax.pages.Enum_FloorMapping;
import jks.tools2d.parallax.pages.Enum_LayerKind;
import jks.tools2d.parallax.pages.Enum_ParticleAnchor;
import jks.tools2d.parallax.pages.Enum_ShaderEffect;
import jks.tools2d.parallax.pages.Parallax_Model;
import jks.tools2d.parallax.pages.Sequence_Segment;

/**
 * One scrolling plane of a parallax. Sizes are in world units: the layer is {@code worldDimension * sizeRatio} wide
 * (or high), the other dimension follows the texture aspect ratio. An {@link Enum_LayerKind#EMPTY} layer has no
 * texture: it is the world's size times sizeRatio, and the reader calls the game's {@link LayerHook} in its place. A
 * {@link Enum_LayerKind#PARTICLES} layer is the same box, and the reader draws its {@link ParallaxParticles} there. A
 * {@link Enum_LayerKind#SHADER} layer is an image layer the reader draws through its {@link Enum_ShaderEffect}. A
 * {@link Enum_LayerKind#SEQUENCE} layer chains its regions (its segments) in a cycle drawn once from a seed: the cycle is
 * its tile, as wide as its segments and their padX, each segment as wide as the layer's height and its own image make it.
 * A {@link Enum_LayerKind#FLOOR} layer is an image layer's box drawn as rows, each at its own depth and its own distance
 * ({@link #drawFloor}); the reader moves the layers in front of it at the speed of the row under their foot
 * ({@link #setPlantedSpeedX}). An IMAGE layer with a tower radius ({@link #isTower}) is wrapped round a cylinder standing
 * in the view's middle: its scroll turns it ({@link #drawTower}).
 */
public class ParallaxLayer
{
	public final Enum_LayerKind kind;
	/** The key of the {@link LayerHook} an EMPTY layer is drawn by; null when the page names none. */
	protected String name;

	/** A PARTICLES layer's effect; null when it has none to draw (no libGDX file, or a reader that draws none). */
	private ParallaxParticles particles;
	/** Where a PARTICLES layer's effect sits as the page scrolls. */
	private Enum_ParticleAnchor anchor = Enum_ParticleAnchor.LAYER;
	/** The effect files the page names for a PARTICLES layer, kept so a page saved from its layers names them again. */
	private String particlesLibgdx, particlesGodot;

	/** The effect a SHADER layer's image is drawn through, and its numbers: see {@link Enum_ShaderEffect}. */
	private Enum_ShaderEffect shaderEffect = Enum_ShaderEffect.WAVE;
	private float shaderAmplitude, shaderWavelength, shaderSpeed;
	/** A FOG layer's depth haze, format 9's, no longer drawn: see {@link jks.tools2d.parallax.pages.Parallax_Model#shaderHaze}. */
	private float shaderHaze;

	/** A SEQUENCE layer's segments as the page names them, kept so a page saved from its layers names them again. */
	private List<Sequence_Segment> segments;
	/** Their weights, in the same order. */
	private int[] segmentWeights;
	/** The seed the page stores for a SEQUENCE layer, and the one its cycle was last drawn from (a game's may differ). */
	private int sequenceSeed, drawnSeed;
	/** A SEQUENCE layer's cycle: the segment (index in texRegion) of each slot. */
	private int[] cycle;
	/**
	 * Where each slot of the cycle starts, without the pads, in layer heights: slot i's left edge is
	 * {@code height * cycleEdges[i] + i * padX}, so a resize or a new padX recomputes nothing. One more than the slots.
	 */
	private float[] cycleEdges;
	/** Per segment: its width in layer heights, and where its packed image sits in it (as trimLeft... for one region). */
	private float[] segmentAspect, segmentTrimLeft, segmentTrimBottom, segmentPackedWidth, segmentPackedHeight;
	/** The narrowest segment, in layer heights: with padX, whether the slots' edges grow from left to right. */
	private float narrowestSegment;

	/**
	 * How a FLOOR layer's rows map its image, its top row's speed ratio X (the bottom row's is the layer's own), how
	 * many rows it is cut in and how high its box is (percent of the world height, 0: the image's height): see
	 * {@link Enum_LayerKind#FLOOR}.
	 */
	private Enum_FloorMapping floorMapping = Enum_FloorMapping.MODE7;
	private float floorTopSpeed, floorHeight;
	private int floorRows = 96;
	/**
	 * A FLOOR layer's rows, bottom to top, measured by {@link #measureFloor} when a setting changes, never in a frame:
	 * each row's speed ratio X, how much it is stretched across (its tiles {@code width * rowStretch} wide, about the
	 * view's middle), the strip of the image it draws, and how far it has scrolled (as currentDistanceX, within one of
	 * its tiles).
	 */
	private float[] rowSpeed, rowStretch, rowDistance;
	private TextureRegion[] rowStrips;
	/**
	 * The speed ratio X the reader gives a layer standing on a FLOOR in front of it: the speed of the floor's row under
	 * its foot. NaN when it stands on none: the layer's own. Never stored.
	 */
	private float plantedSpeedX = Float.NaN;

	/**
	 * The radius of the tower an IMAGE layer is wrapped round, in percent of the world width (0: flat), and how much its
	 * wall darkens as it turns away, and where the camera stands from its axis: see {@link Parallax_Model#towerRadius}
	 * and {@link Parallax_Model#towerCamera}.
	 */
	private float towerRadius, towerShade = Parallax_Model.DEFAULT_TOWER_SHADE, towerCamera;

	/**
	 * A LURKER layer's seed, odds and cover: see {@link Parallax_Model#lurkerSeed}, {@link Parallax_Model#lurkerOdds} and
	 * {@link Parallax_Model#lurkerCover}. Kept on every kind.
	 */
	private int lurkerSeed, lurkerOdds = 3;
	private float[] lurkerCover = new float[0];

	/**
	 * An IMAGE or SHADER layer's animated patch: see {@link Parallax_Model#animRegion}, {@link Parallax_Model#animX} and
	 * {@link Parallax_Model#animFps}. Kept on every kind, drawn on those two.
	 */
	private String animRegion = "";
	private int animX, animY;
	private float animFps = Parallax_Model.DEFAULT_ANIM_FPS;
	/**
	 * The patch's frames as drawn (inset by half a texel, as {@link #drawnRegions}), null when it has none; and per frame
	 * its size in pixels and where its packed image sits in it, measured by {@link #measureAnim} when the frames or the
	 * layer's region change, never in a frame. Replaced, never changed: a copy shares them.
	 */
	private List<TextureRegion> animFrames, animTexRegions;
	private float[] animWidth, animHeight, animTrimLeft, animTrimBottom, animPackedWidth, animPackedHeight;
	/** The frame drawn, set by the reader from its clock before the layer's tiles ({@link #setAnimTime}). */
	private int animFrame;
	/**
	 * What {@link #draw} and {@link #drawMirror} draw of a tile: {@link #DRAW_ALL}, or the reader's two passes over a
	 * SHADER layer with a patch, {@link #DRAW_IMAGE} then {@link #DRAW_PATCH} (r397, {@link LayerEffects#beginPatch}).
	 */
	private int drawPart;
	/** The layer's image in pixels, the size {@link #animX} and {@link #animY} are taken in. */
	private float imageWidth = 1, imageHeight = 1;
	/** The strip of the image a tower's piece draws, set before each piece's draw; made once, with the regions. */
	private TextureRegion towerStrip;

	private List<TextureRegion> texRegion;
	/** Cached first region, the one actually drawn. */
	private TextureRegion region;
	/**
	 * What is drawn of each of {@code texRegion}: the region inset by half a texel on every side (r218). Linear filtering
	 * at a tile's edge otherwise mixes in the atlas pixel next to it, transparent in an atlas packed without
	 * duplicatePadding: each join then showed a thin line of what is behind the layer, often the dark gradient. Replaced
	 * with texRegion, never changed: a copy shares it.
	 */
	private List<TextureRegion> drawnRegions;

	private final boolean isWidth;
	private final float worldDimension;

	/**
	 * The reader that took this layer first ({@link ParallaxPageReader#addLayers}): another reader takes a clone, so two
	 * hearts showing one page keep their own world size and scroll. A clone starts with none.
	 */
	ParallaxPageReader reader;
	/** The world the decal percentages are taken of: the heart's, see {@link #setWorldSize}. */
	private float worldWidth, worldHeight;
	private float decalPercentX, decalPercentY;
	private float regionWidth, regionHeight;
	private float sizeRatio = 1;

	/**
	 * True: an {@link AtlasRegion} packed with its whitespace stripped keeps its original size, and its packed image is
	 * drawn at its offset inside it. False: the packed image is stretched over the whole layer, as pages saved before
	 * format 4 were designed. See {@link jks.tools2d.parallax.pages.WholePage_Model#useOriginalSize}.
	 */
	private boolean useOriginalSize;
	/** Where the packed image sits in the layer, in fractions of it: 0, 0, 1, 1 when nothing was stripped. */
	private float trimLeft, trimBottom, packedWidthRatio = 1, packedHeightRatio = 1;

	protected float parallaxSpeedRatioX;
	protected float parallaxSpeedRatioY;

	protected float currentDistanceX, currentDistanceY;
	/**
	 * How far act() has wrapped currentDistanceX back on X, kept within the effect's period ({@link #getShaderPeriod}):
	 * FOG's noise runs on across the tiles from {@link #getEffectStartX} (r229).
	 */
	protected float wrappedX;
	/** The same on Y, within {@link #getShaderPeriodY}, for {@link #getEffectStartY} (r262). */
	protected float wrappedY;

	protected float padX;
	protected float padXFactor;

	protected float padY;
	protected float padYFactor;

	protected float speedXAtRest;

	protected boolean flipX;
	protected boolean flipY;

	protected boolean isMirror;

	public ParallaxLayer(List<TextureRegion> texRegion, boolean isWidth, float worldDimension, float parallaxScrollRatioX, float parallaxScrollRatioY, float sizeRatio)
	{this(Enum_LayerKind.IMAGE, texRegion, isWidth, worldDimension, parallaxScrollRatioX, parallaxScrollRatioY, sizeRatio);}

	/**
	 * An IMAGE or a SHADER layer: one that draws a region, the first of {@code texRegion}. A SEQUENCE layer is made by
	 * {@link #sequence}.
	 */
	public ParallaxLayer(Enum_LayerKind kind, List<TextureRegion> texRegion, boolean isWidth, float worldDimension, float parallaxScrollRatioX, float parallaxScrollRatioY, float sizeRatio)
	{
		if (!drawsImage(kind))
			throw new IllegalArgumentException("A " + kind + " layer draws no region");
		this.kind = kind;
		this.isWidth = isWidth;
		this.worldDimension = worldDimension;
		this.worldWidth = Gvars_Parallax.getWorldWidth();
		this.worldHeight = Gvars_Parallax.getWorldHeight();
		this.sizeRatio = sizeRatio;
		setTexRegion(texRegion);
		setParallaxSpeedRatioX(parallaxScrollRatioX);
		setParallaxSpeedRatioY(parallaxScrollRatioY);
	}

	public ParallaxLayer(TextureRegion texRegion, boolean isWidth, float worldDimension, float parallaxScrollRatioX, float parallaxScrollRatioY, float sizeRatio)
	{
		this(singletonList(texRegion), isWidth, worldDimension, parallaxScrollRatioX, parallaxScrollRatioY, sizeRatio);
	}

	private ParallaxLayer(Enum_LayerKind kind, String name, float sizeRatio)
	{
		this.kind = kind;
		this.name = name;
		this.isWidth = true;
		this.worldDimension = 0;
		this.worldWidth = Gvars_Parallax.getWorldWidth();
		this.worldHeight = Gvars_Parallax.getWorldHeight();
		this.sizeRatio = sizeRatio;
	}

	/** An EMPTY layer: draws nothing itself, the {@link LayerHook} registered under {@code name} draws in its place. */
	public static ParallaxLayer empty(String name, float sizeRatio)
	{return new ParallaxLayer(Enum_LayerKind.EMPTY, name, sizeRatio);}

	/**
	 * A PARTICLES layer: the reader draws {@code effect} in a box the world's size times sizeRatio, as {@code anchor}
	 * says; a null effect draws nothing. Takes the effect over: {@link ParallaxParticles#allocate()}, starts it and warms
	 * it up ({@link ParallaxParticles#warmUp()}).
	 */
	public static ParallaxLayer particles(ParallaxParticles effect, Enum_ParticleAnchor anchor, float sizeRatio)
	{
		ParallaxLayer layer = new ParallaxLayer(Enum_LayerKind.PARTICLES, null, sizeRatio);
		layer.anchor = anchor == null ? Enum_ParticleAnchor.LAYER : anchor;
		layer.setParticles(effect);
		return layer;
	}

	/**
	 * A SHADER layer: {@code region} tiled as an image layer is, drawn through {@code effect} with those numbers (see
	 * {@link Enum_ShaderEffect}), {@code worldDimension} wide.
	 */
	public static ParallaxLayer shader(TextureRegion region, float worldDimension, float sizeRatio, Enum_ShaderEffect effect, float amplitude, float wavelength, float speed)
	{
		ParallaxLayer layer = new ParallaxLayer(Enum_LayerKind.SHADER, singletonList(region), true, worldDimension, 0, 0, sizeRatio);
		layer.setShaderEffect(effect);
		layer.shaderAmplitude = amplitude;
		layer.shaderWavelength = wavelength;
		layer.shaderSpeed = speed;
		return layer;
	}

	/**
	 * A SEQUENCE layer chaining {@code segments}, the first {@code worldDimension} wide and as high as its image makes it,
	 * every other as wide as that height and its own image make it; {@code length} of them, picked by
	 * {@link SequenceCycle} from {@code seed} and {@code weights} (one per segment).
	 */
	public static ParallaxLayer sequence(List<TextureRegion> segments, int[] weights, int seed, int length, float worldDimension, float sizeRatio)
	{
		if (segments == null || weights == null || segments.size() != weights.length)
			throw new IllegalArgumentException("A sequence layer needs one weight per segment");
		List<Sequence_Segment> named = new ArrayList<>(weights.length);
		for (int i = 0; i < weights.length; i++)
			named.add(new Sequence_Segment(null, i, weights[i]));
		ParallaxLayer layer = new ParallaxLayer(Enum_LayerKind.SEQUENCE, segments, true, worldDimension, 0, 0, sizeRatio);
		layer.setSequence(named, seed, length);
		return layer;
	}

	/** True for the kinds that draw a region: IMAGE, SHADER, SEQUENCE, FLOOR and LURKER. */
	public boolean drawsImage()
	{return drawsImage(kind);}

	private static boolean drawsImage(Enum_LayerKind kind)
	{return kind == Enum_LayerKind.IMAGE || kind == Enum_LayerKind.SHADER || kind == Enum_LayerKind.SEQUENCE || kind == Enum_LayerKind.FLOOR
			|| kind == Enum_LayerKind.LURKER;}

	/**
	 * A FLOOR layer: {@code region} in an image layer's box, {@code worldDimension} wide, drawn as {@code rows} rows from
	 * the bottom one at {@code bottomSpeed} to the top one at {@code topSpeed}, mapped as {@code mapping} says.
	 */
	public static ParallaxLayer floor(TextureRegion region, float worldDimension, float sizeRatio, Enum_FloorMapping mapping, float bottomSpeed, float topSpeed, int rows)
	{
		ParallaxLayer layer = new ParallaxLayer(Enum_LayerKind.FLOOR, singletonList(region), true, worldDimension, bottomSpeed, 0, sizeRatio);
		layer.setFloor(mapping, topSpeed, rows, 0);
		return layer;
	}

	private static List<TextureRegion> singletonList(TextureRegion region)
	{
		List<TextureRegion> list = new ArrayList<>(1);
		list.add(region);
		return list;
	}

	public void setUpEverything(Parallax_Model model)
	{
		setFlipX(model.flipX);
		setFlipY(model.flipY);
		setDecalPercentX(model.decal_X_Ratio);
		setDecalPercentY(model.decal_Y_Ratio);
		setSizeRatio(model.sizeRatio);
		setSpeedAtRest(model.speedXAtRest);
		setParallaxSpeedRatioX(model.parallaxScalingSpeedX);
		setParallaxSpeedRatioY(model.parallaxScalingSpeedY);
		setPadX(model.padX);
		setPadXFactor(model.padXFactor);
		setPadY(model.padY);
		setPadYFactor(model.padYFactor);
		setMirror(model.mirror);
		setName(model.name);
		particlesLibgdx = model.particlesLibgdx;
		particlesGodot = model.particlesGodot;
		if (kind == Enum_LayerKind.PARTICLES && model.particlesAnchor != null)
			anchor = model.particlesAnchor;
		setShaderEffect(model.shaderEffect);
		shaderAmplitude = model.shaderAmplitude;
		shaderWavelength = model.shaderWavelength;
		shaderSpeed = model.shaderSpeed;
		shaderHaze = model.shaderHaze;
		if (kind == Enum_LayerKind.SEQUENCE)
		{
			List<Sequence_Segment> named = new ArrayList<>(model.sequenceSegments.size());
			for (Sequence_Segment segment : model.sequenceSegments)
				named.add(segment.copy());
			setSequence(named, model.sequenceSeed, model.sequenceLength);
		}
		setFloor(model.floorMapping, model.floorTopSpeed, model.floorRows, model.floorHeight);
		setTower(model.towerRadius, model.towerShade);
		setTowerCamera(model.towerCamera);
		setLurker(model.lurkerSeed, model.lurkerOdds, model.lurkerCover);
		setAnim(model.animRegion, model.animX, model.animY, model.animFps);
	}

	public void resetPosition()
	{
		currentDistanceX = decalPercentX * (worldWidth / 100);
		currentDistanceY = decalPercentY * (worldHeight / 100);
		wrappedX = 0;
		wrappedY = 0;
		if (rowDistance != null)
			Arrays.fill(rowDistance, currentDistanceX);
	}

	/** {@link #setDrawPart}: the image and its patch; the image only; the patch only. */
	public static final int DRAW_ALL = 0, DRAW_IMAGE = 1, DRAW_PATCH = 2;

	/** What {@link #draw} and {@link #drawMirror} draw of a tile, set by the reader around a pass ({@link #DRAW_ALL} after). */
	public void setDrawPart(int part)
	{drawPart = part;}

	/**
	 * Draws the region at (x, y); an EMPTY layer draws nothing, its hook does (ParallaxPageReader). A SHADER layer's
	 * effect is the batch's shader, which the reader sets around its tiles.
	 */
	public void draw(Batch batch, float x, float y)
	{
		if (kind == Enum_LayerKind.SEQUENCE)
			drawCycle(batch, x, y, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, flipX, flipY);
		else if (drawsImage())
		{
			if (drawPart != DRAW_PATCH)
				drawRegion(batch, drawnRegions.get(0), x, y, getRegionWidth(), getRegionHeight(), trimLeft, trimBottom, packedWidthRatio, packedHeightRatio, flipX, flipY);
			if (drawPart != DRAW_IMAGE)
				drawAnim(batch, x, y, flipX, flipY);
		}
	}

	/** Draws the mirrored copy: flipped vertically when tiling on X, horizontally when tiling on Y. */
	public void drawMirror(Batch batch, float x, float y, boolean onX)
	{
		boolean fx = onX ? flipX : !flipX;
		boolean fy = onX ? !flipY : flipY;
		if (kind == Enum_LayerKind.SEQUENCE)
			drawCycle(batch, x, y, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, fx, fy);
		else if (drawsImage())
		{
			if (drawPart != DRAW_PATCH)
				drawRegion(batch, drawnRegions.get(0), x, y, getRegionWidth(), getRegionHeight(), trimLeft, trimBottom, packedWidthRatio, packedHeightRatio, fx, fy);
			if (drawPart != DRAW_IMAGE)
				drawAnim(batch, x, y, fx, fy);
		}
	}

	/**
	 * Draws the animated patch's current frame over the tile at (x, y), through the same batch call as the tile: its
	 * place in the region scaled to the drawn size, mirrored with the tile by a flip. Only an IMAGE or SHADER layer's.
	 */
	private void drawAnim(Batch batch, float x, float y, boolean fx, boolean fy)
	{
		if (!hasAnim())
			return;
		float width = getRegionWidth(), height = getRegionHeight();
		float scaleX = width / imageWidth, scaleY = height / imageHeight;
		int frame = animFrame;
		float patchWidth = animWidth[frame] * scaleX, patchHeight = animHeight[frame] * scaleY;
		float left = fx ? x + width - (animX * scaleX + patchWidth) : x + animX * scaleX;
		float bottom = fy ? y + height - (animY * scaleY + patchHeight) : y + animY * scaleY;
		drawRegion(batch, animFrames.get(frame), left, bottom, patchWidth, patchHeight, animTrimLeft[frame], animTrimBottom[frame],
				animPackedWidth[frame], animPackedHeight[frame], fx, fy);
	}

	/**
	 * Draws the slots of a SEQUENCE layer's cycle, starting at (x, y), that reach between {@code fromX} and {@code toX}:
	 * a binary search finds the first, then the walk stops past {@code toX}, so a long cycle costs only what shows. A
	 * flip mirrors each segment in its own slot; the slots keep their order.
	 */
	public void drawCycle(Batch batch, float x, float y, float fromX, float toX, boolean fx, boolean fy)
	{
		if (cycle == null)
			return;
		float height = getRegionHeight();
		int slots = cycle.length;
		// A negative padX wider than a segment makes the edges go back: then every slot is looked at.
		boolean ordered = isCycleOrdered();

		int first = 0;
		if (ordered)
		{
			int last = slots;
			while (first < last)
			{
				int middle = (first + last) >>> 1;
				if (x + height * cycleEdges[middle + 1] + middle * padX > fromX)
					last = middle;
				else
					first = middle + 1;
			}
		}

		for (int slot = first; slot < slots; slot++)
		{
			float left = x + height * cycleEdges[slot] + slot * padX;
			if (left >= toX)
			{
				if (ordered)
					break;
				continue;
			}
			int segment = cycle[slot];
			float width = height * segmentAspect[segment];
			if (left + width <= fromX)
				continue;
			drawRegion(batch, drawnRegions.get(segment), left, y, width, height, segmentTrimLeft[segment], segmentTrimBottom[segment],
					segmentPackedWidth[segment], segmentPackedHeight[segment], fx, fy);
		}
	}

	/** Draws the packed image in a box at (x, y); a flip mirrors where it sits in the box too. */
	private static void drawRegion(Batch batch, TextureRegion region, float x, float y, float width, float height,
			float trimLeft, float trimBottom, float packedWidthRatio, float packedHeightRatio, boolean fx, boolean fy)
	{
		float drawWidth = width * packedWidthRatio;
		float drawHeight = height * packedHeightRatio;
		float left = x + width * (fx ? 1 - trimLeft - packedWidthRatio : trimLeft);
		float bottom = y + height * (fy ? 1 - trimBottom - packedHeightRatio : trimBottom);
		batch.draw(region,
			fx ? left + drawWidth : left,
			fy ? bottom + drawHeight : bottom,
			fx ? -drawWidth : drawWidth,
			fy ? -drawHeight : drawHeight);
	}

	/**
	 * A copy drawing the same regions from the same place. Field by field, and without {@code @Override}: GWT emulates
	 * neither {@link Cloneable} nor {@code Object.clone()}. A field added to this class must be copied here,
	 * ParallaxLayerTest checks it.
	 */
	public ParallaxLayer clone()
	{
		ParallaxLayer copy = !drawsImage() ? new ParallaxLayer(kind, name, sizeRatio)
				: new ParallaxLayer(kind, new ArrayList<>(texRegion), isWidth, worldDimension, parallaxSpeedRatioX, parallaxSpeedRatioY, sizeRatio);
		copy.name = name;
		if (drawsImage())
			copy.drawnRegions = drawnRegions;
		// Its own effect: both play during a cross-fade, and one effect updated twice a frame would run double speed.
		if (particles != null)
			copy.setParticles(new ParallaxParticles(particles));
		copy.anchor = anchor;
		copy.particlesLibgdx = particlesLibgdx;
		copy.particlesGodot = particlesGodot;
		copy.shaderEffect = shaderEffect;
		copy.shaderAmplitude = shaderAmplitude;
		copy.shaderWavelength = shaderWavelength;
		copy.shaderSpeed = shaderSpeed;
		copy.shaderHaze = shaderHaze;
		// The segments and their weights are replaced, never changed: shared. The arrays a game's seed or a resize
		// rewrites in place are the copy's own.
		copy.segments = segments;
		copy.segmentWeights = segmentWeights;
		copy.sequenceSeed = sequenceSeed;
		copy.drawnSeed = drawnSeed;
		copy.cycle = cycle == null ? null : Arrays.copyOf(cycle, cycle.length);
		copy.cycleEdges = cycleEdges == null ? null : Arrays.copyOf(cycleEdges, cycleEdges.length);
		copy.segmentAspect = segmentAspect == null ? null : Arrays.copyOf(segmentAspect, segmentAspect.length);
		copy.segmentTrimLeft = segmentTrimLeft == null ? null : Arrays.copyOf(segmentTrimLeft, segmentTrimLeft.length);
		copy.segmentTrimBottom = segmentTrimBottom == null ? null : Arrays.copyOf(segmentTrimBottom, segmentTrimBottom.length);
		copy.segmentPackedWidth = segmentPackedWidth == null ? null : Arrays.copyOf(segmentPackedWidth, segmentPackedWidth.length);
		copy.segmentPackedHeight = segmentPackedHeight == null ? null : Arrays.copyOf(segmentPackedHeight, segmentPackedHeight.length);
		copy.narrowestSegment = narrowestSegment;
		copy.floorMapping = floorMapping;
		copy.floorTopSpeed = floorTopSpeed;
		copy.floorRows = floorRows;
		copy.floorHeight = floorHeight;
		copy.plantedSpeedX = plantedSpeedX;
		copy.towerRadius = towerRadius;
		copy.towerShade = towerShade;
		copy.towerCamera = towerCamera;
		copy.lurkerSeed = lurkerSeed;
		copy.lurkerOdds = lurkerOdds;
		// Replaced, never changed: shared.
		copy.lurkerCover = lurkerCover;
		copy.animRegion = animRegion;
		copy.animX = animX;
		copy.animY = animY;
		copy.animFps = animFps;
		copy.animFrame = animFrame;
		copy.drawPart = drawPart;
		// Replaced, never changed: shared.
		copy.animFrames = animFrames;
		copy.animTexRegions = animTexRegions;
		copy.animWidth = animWidth;
		copy.animHeight = animHeight;
		copy.animTrimLeft = animTrimLeft;
		copy.animTrimBottom = animTrimBottom;
		copy.animPackedWidth = animPackedWidth;
		copy.animPackedHeight = animPackedHeight;
		// Set before each piece is drawn, and read by the batch at once: shared.
		copy.towerStrip = towerStrip;
		copy.parallaxSpeedRatioX = parallaxSpeedRatioX;
		copy.parallaxSpeedRatioY = parallaxSpeedRatioY;
		copy.worldWidth = worldWidth;
		copy.worldHeight = worldHeight;
		copy.decalPercentX = decalPercentX;
		copy.decalPercentY = decalPercentY;
		copy.regionWidth = regionWidth;
		copy.regionHeight = regionHeight;
		copy.imageWidth = imageWidth;
		copy.imageHeight = imageHeight;
		copy.useOriginalSize = useOriginalSize;
		copy.trimLeft = trimLeft;
		copy.trimBottom = trimBottom;
		copy.packedWidthRatio = packedWidthRatio;
		copy.packedHeightRatio = packedHeightRatio;
		copy.currentDistanceX = currentDistanceX;
		copy.currentDistanceY = currentDistanceY;
		copy.wrappedX = wrappedX;
		copy.wrappedY = wrappedY;
		copy.padX = padX;
		copy.padXFactor = padXFactor;
		copy.padY = padY;
		copy.padYFactor = padYFactor;
		copy.speedXAtRest = speedXAtRest;
		copy.flipX = flipX;
		copy.flipY = flipY;
		copy.isMirror = isMirror;
		// Its own rows: measured again from the settings just copied, then placed where this layer's are.
		copy.measureFloor();
		if (rowDistance != null && copy.rowDistance != null)
			System.arraycopy(rowDistance, 0, copy.rowDistance, 0, rowDistance.length);
		return copy;
	}

	public void act(float delta, float speedX, float speedY, boolean onX, boolean onY)
	{
		float moveX = -delta * (speedXAtRest + speedX) * getActingSpeedX();
		float moveY = -delta * speedY * parallaxSpeedRatioY;
		currentDistanceY += moveY;
		currentDistanceX += moveX;

		if (particles != null)
		{
			// Emitted from the view, the particles still move with the depth they are at.
			if (anchor == Enum_ParticleAnchor.VIEW)
				particles.translateParticles(moveX, moveY);
			particles.update(delta);
		}

		// Keep the offset within one tile so the tiling loops stay short and floats stay precise.
		float totalWidth = getTotalWidth();
		if (onX && totalWidth > 0)
		{
			float before = currentDistanceX;
			currentDistanceX %= totalWidth;
			float period = getShaderPeriod();
			wrappedX = period > 0 ? (wrappedX + before - currentDistanceX) % period : 0;
		}

		float totalHeight = getTotalHeight();
		if (onY && totalHeight > 0)
		{
			float before = currentDistanceY;
			currentDistanceY %= totalHeight;
			float period = getShaderPeriodY();
			wrappedY = period > 0 ? (wrappedY + before - currentDistanceY) % period : 0;
		}

		if (rowDistance != null)
		{
			// Each row at its own speed, kept within one of its tiles: a floor is tiled across whatever the page repeats.
			float width = getRegionWidth();
			for (int i = 0; i < rowDistance.length; i++)
			{
				float step = width * rowStretch[i];
				float distance = rowDistance[i] - delta * (speedXAtRest + speedX) * rowSpeed[i];
				rowDistance[i] = step > 0 ? distance % step : distance;
			}
		}
	}

	/** The layer's tile: its image, or a SEQUENCE layer's whole cycle, without the padX after its last slot. */
	public float getWidth()
	{
		if (kind == Enum_LayerKind.SEQUENCE && cycle != null)
			return getRegionHeight() * cycleEdges[cycle.length] + (cycle.length - 1) * padX;
		return getRegionWidth();
	}

	/**
	 * Where a SEQUENCE layer's leftmost slot starts, from the tile's corner: 0, unless a negative padX wider than a
	 * segment takes slots back past it. Other kinds: 0. Walks the slots only when they go back.
	 */
	public float getCycleLeft()
	{
		if (kind != Enum_LayerKind.SEQUENCE || cycle == null || isCycleOrdered())
			return 0;
		float height = getRegionHeight(), left = 0;
		for (int slot = 0; slot < cycle.length; slot++)
			left = Math.min(left, height * cycleEdges[slot] + slot * padX);
		return left;
	}

	/**
	 * Where a SEQUENCE layer's rightmost slot ends, from the tile's corner: {@link #getWidth()}, unless a negative padX
	 * wider than a segment brings slots back (then the width can be 0 or less, the slots still drawn). Other kinds: the
	 * width.
	 */
	public float getCycleRight()
	{
		if (kind != Enum_LayerKind.SEQUENCE || cycle == null || isCycleOrdered())
			return getWidth();
		float height = getRegionHeight(), right = -Float.MAX_VALUE;
		for (int slot = 0; slot < cycle.length; slot++)
			right = Math.max(right, height * (cycleEdges[slot] + segmentAspect[cycle[slot]]) + slot * padX);
		return right;
	}

	/** True when every slot starts right of the one before: no negative padX outweighs the narrowest segment. */
	private boolean isCycleOrdered()
	{return getRegionHeight() * narrowestSegment + padX > 0;}

	/** The layer's box's height: its image's, or a FLOOR layer's own when it sets one ({@link #getFloorHeight}). */
	public float getHeight()
	{return kind == Enum_LayerKind.FLOOR && floorHeight > 0 ? floorHeight * (worldHeight / 100) : getRegionHeight();}

	/** The step between two tiles: a SEQUENCE layer's cycle, each slot followed by padX. */
	public float getTotalWidth()
	{
		if (kind == Enum_LayerKind.SEQUENCE && cycle != null)
			return getRegionHeight() * cycleEdges[cycle.length] + cycle.length * padX;
		return getRegionWidth() + padX;
	}

	public float getTotalHeight()
	{return getRegionHeight() + padY;}

	public float getWorldWidth()
	{return worldWidth;}

	public float getWorldHeight()
	{return worldHeight;}

	/**
	 * Sets the world the decal percentages are taken of, {@link Gvars_Parallax}'s when the layer was built. Does not move
	 * the layer: {@link #resetPosition()} places it at its decal in the new world.
	 */
	public void setWorldSize(float worldWidth, float worldHeight)
	{
		this.worldWidth = worldWidth;
		this.worldHeight = worldHeight;
		// A floor's box against its image: mode 7's depths follow.
		measureFloor();
	}

	public float getDecalPercentX()
	{return decalPercentX;}

	public void setDecalPercentX(float decalPercentX)
	{
		float by = (decalPercentX - this.decalPercentX) * (worldWidth / 100);
		currentDistanceX += by;
		if (rowDistance != null)
			for (int i = 0; i < rowDistance.length; i++)
				rowDistance[i] += by;
		this.decalPercentX = decalPercentX;
	}

	public float getDecalPercentY()
	{return decalPercentY;}

	public void setDecalPercentY(float decalPercentY)
	{
		currentDistanceY += (decalPercentY - this.decalPercentY) * (worldHeight / 100);
		this.decalPercentY = decalPercentY;
	}

	public float getRegionWidth()
	{return (drawsImage() ? regionWidth : worldWidth) * sizeRatio;}

	public float getRegionHeight()
	{return (drawsImage() ? regionHeight : worldHeight) * sizeRatio;}

	public float getSpeedAtRest()
	{return speedXAtRest;}

	public void setSpeedAtRest(float speed)
	{this.speedXAtRest = speed;}

	public float getCurrentDistanceX()
	{return currentDistanceX;}

	public void setCurrentDistanceX(float decalX)
	{this.currentDistanceX = decalX;}

	public float getCurrentDistanceY()
	{return currentDistanceY;}

	public void setCurrentDistanceY(float decalY)
	{this.currentDistanceY = decalY;}

	/** Distance scrolled horizontally since the layer was placed at its decal position. */
	public float getScrollX()
	{return currentDistanceX - decalPercentX * (worldWidth / 100);}

	public void setScrollX(float scroll)
	{currentDistanceX = decalPercentX * (worldWidth / 100) + scroll;}

	/** Distance scrolled vertically since the layer was placed at its decal position. */
	public float getScrollY()
	{return currentDistanceY - decalPercentY * (worldHeight / 100);}

	public void setScrollY(float scroll)
	{currentDistanceY = decalPercentY * (worldHeight / 100) + scroll;}

	public float getSizeRatio()
	{return sizeRatio;}

	public void setSizeRatio(float sizeRatio)
	{
		this.sizeRatio = sizeRatio;
		measureFloor();
	}

	public boolean isFlipX()
	{return flipX;}

	public void setFlipX(boolean flipX)
	{this.flipX = flipX;}

	public boolean isFlipY()
	{return flipY;}

	public void setFlipY(boolean flipY)
	{this.flipY = flipY;}

	public float getPadX()
	{return padX;}

	public void setPadX(float padX)
	{this.padX = padX;}

	public float getPadXFactor()
	{return padXFactor;}

	public void setPadXFactor(float padXFactor)
	{this.padXFactor = padXFactor;}

	public float getPadY()
	{return padY;}

	public void setPadY(float padY)
	{this.padY = padY;}

	public float getPadYFactor()
	{return padYFactor;}

	public void setPadYFactor(float padYFactor)
	{this.padYFactor = padYFactor;}

	public float getParallaxSpeedRatioX()
	{return parallaxSpeedRatioX;}

	public void setParallaxSpeedRatioX(float parallaxSpeedRatioX)
	{
		this.parallaxSpeedRatioX = parallaxSpeedRatioX;
		// A floor's bottom row: its rows' speeds follow.
		if (kind == Enum_LayerKind.FLOOR)
			measureFloor();
	}

	/**
	 * The speed ratio X act() moves the layer at: the one the reader planted it at on a floor ({@link #setPlantedSpeedX}),
	 * or its own.
	 */
	public float getActingSpeedX()
	{return plantedSpeedX == plantedSpeedX ? plantedSpeedX : parallaxSpeedRatioX;}

	/** The speed of the floor's row under the layer's foot, NaN when it stands on no floor; see {@link #setPlantedSpeedX}. */
	public float getPlantedSpeedX()
	{return plantedSpeedX;}

	/**
	 * Makes act() move the layer at {@code speed} instead of its own speed ratio X, which stays what the page stores; NaN
	 * gives it its own back. The reader sets it on every layer in front of a FLOOR, each frame
	 * ({@link ParallaxPageReader#plant}).
	 */
	public void setPlantedSpeedX(float speed)
	{this.plantedSpeedX = speed;}

	public float getParallaxSpeedRatioY()
	{return parallaxSpeedRatioY;}

	public void setParallaxSpeedRatioY(float parallaxSpeedRatioY)
	{this.parallaxSpeedRatioY = parallaxSpeedRatioY;}

	public boolean isMirror()
	{return isMirror;}

	public void setMirror(boolean isMirror)
	{this.isMirror = isMirror;}

	/** The regions drawn; null unless the layer is an IMAGE or a SHADER. */
	public List<TextureRegion> getTexRegion()
	{return texRegion;}

	public Enum_LayerKind getKind()
	{return kind;}

	public String getName()
	{return name;}

	public void setName(String name)
	{this.name = name;}

	/** A PARTICLES layer's effect, null when it draws none. */
	public ParallaxParticles getParticles()
	{return particles;}

	/**
	 * Sets the effect a PARTICLES layer draws, null for none; allocates its particles, starts it and runs it on until its
	 * particles are out ({@link ParallaxParticles#warmUp()}): the page's first frame shows them already falling.
	 */
	public void setParticles(ParallaxParticles effect)
	{
		if (effect != null && kind != Enum_LayerKind.PARTICLES)
			throw new IllegalStateException("Only a PARTICLES layer draws a particle effect, not " + kind);
		particles = effect;
		if (effect != null)
		{
			effect.allocate();
			effect.start();
			effect.warmUp();
		}
	}

	public Enum_ParticleAnchor getAnchor()
	{return anchor;}

	public void setAnchor(Enum_ParticleAnchor anchor)
	{this.anchor = anchor == null ? Enum_ParticleAnchor.LAYER : anchor;}

	/** The libGDX effect (.p) the page names for this layer, null when none; see {@link Parallax_Model#particlesLibgdx}. */
	public String getParticlesLibgdx()
	{return particlesLibgdx;}

	public void setParticlesLibgdx(String particlesLibgdx)
	{this.particlesLibgdx = particlesLibgdx;}

	/** The Godot scene the page names for this layer, null when none; libGDX never reads it. */
	public String getParticlesGodot()
	{return particlesGodot;}

	public void setParticlesGodot(String particlesGodot)
	{this.particlesGodot = particlesGodot;}

	public Enum_ShaderEffect getShaderEffect()
	{return shaderEffect;}

	public void setShaderEffect(Enum_ShaderEffect shaderEffect)
	{this.shaderEffect = shaderEffect == null ? Enum_ShaderEffect.WAVE : shaderEffect;}

	/** WAVE's sideways shift in world units, FOG's thinning from 0 to 1. */
	public float getShaderAmplitude()
	{return shaderAmplitude;}

	public void setShaderAmplitude(float shaderAmplitude)
	{this.shaderAmplitude = shaderAmplitude;}

	/** WAVE's wavelength, FOG's patch size, in world units; 0 draws the image without its effect. */
	public float getShaderWavelength()
	{return shaderWavelength;}

	public void setShaderWavelength(float shaderWavelength)
	{this.shaderWavelength = shaderWavelength;}

	/** How fast the effect moves, in world units per second. */
	public float getShaderSpeed()
	{return shaderSpeed;}

	public void setShaderSpeed(float shaderSpeed)
	{this.shaderSpeed = shaderSpeed;}

	/**
	 * A FOG layer's depth haze, format 9's: the reader no longer draws it. Saved through Utils_Page.buildFromPage, it
	 * becomes the page's fog strength when the page sets none ({@link jks.tools2d.parallax.pages.WholePage_Model#getFogStrength}).
	 */
	public float getShaderHaze()
	{return shaderHaze;}

	public void setShaderHaze(float shaderHaze)
	{this.shaderHaze = shaderHaze;}

	/**
	 * The share of the mist's white this layer gives a layer one step behind it, 0 to 1: its haze when it is a SHADER
	 * layer drawn through FOG, 0 otherwise.
	 */
	@Deprecated
	public float getHazePerLayer()
	{
		if (kind != Enum_LayerKind.SHADER || shaderEffect != Enum_ShaderEffect.FOG || !(shaderHaze > 0))
			return 0;
		return Math.min(1, shaderHaze);
	}

	/**
	 * Where the effect is after {@code seconds} of the reader's clock, in world units: {@code speed * seconds} wrapped
	 * to where the effect repeats, one wavelength for WAVE, {@link Enum_ShaderEffect#FOG_PERIOD} for FOG, so a shader
	 * is handed a small number however long the game runs. 0 when the wavelength is not positive.
	 */
	public float getShaderPhase(double seconds)
	{
		if (!(shaderWavelength > 0))
			return 0;
		double period = (double) shaderWavelength * (shaderEffect == null ? 1 : shaderEffect.period());
		double phase = (seconds * shaderSpeed) % period;
		return (float) (phase < 0 ? phase + period : phase);
	}

	/** Where the effect repeats, in world units: one wavelength for WAVE, {@link Enum_ShaderEffect#FOG_PERIOD} for FOG; 0 without a wavelength. */
	public float getShaderPeriod()
	{return shaderWavelength > 0 ? shaderWavelength * (shaderEffect == null ? 1 : shaderEffect.period()) : 0;}

	/**
	 * Where FOG's noise starts on X, in world units from the view's left edge, wrapped to {@link #getShaderPeriod}: the
	 * left edge of the image of the tile at currentDistanceX, its right edge when flipped on X (the noise then runs
	 * leftward), carried back by what act() wrapped. The noise runs on from there across every tile, so a tile edge is no
	 * seam (r229); a layer that never wraps on X keeps the noise its image always had.
	 */
	public float getEffectStartX()
	{
		float start = currentDistanceX + wrappedX + getRegionWidth() * (flipX ? 1 - trimLeft : trimLeft);
		float period = getShaderPeriod();
		return period > 0 ? start % period : start;
	}

	/** Where the effect repeats up, in world units: one wavelength for WAVE, {@link Enum_ShaderEffect#FOG_PERIOD_Y} for FOG; 0 without a wavelength. */
	public float getShaderPeriodY()
	{return shaderWavelength > 0 ? shaderWavelength * (shaderEffect == null ? 1 : shaderEffect.periodY()) : 0;}

	/**
	 * {@link #getEffectStartX} on Y: where FOG's noise and WAVE's ripple start, in world units above the layers' y 0, wrapped to
	 * {@link #getShaderPeriodY}: the bottom edge of the image of the tile at currentDistanceY, its top edge when flipped
	 * on Y (the noise then runs down), carried back by what act() wrapped, so a tile edge on Y is no seam either (r262, WAVE r273).
	 */
	public float getEffectStartY()
	{
		float start = currentDistanceY + wrappedY + getRegionHeight() * (flipY ? 1 - trimBottom : trimBottom);
		float period = getShaderPeriodY();
		return period > 0 ? start % period : start;
	}

	/** A SEQUENCE layer's segments as the page names them, each with its weight; null for other kinds. */
	public List<Sequence_Segment> getSequenceSegments()
	{return segments;}

	/** The seed the page stores for a SEQUENCE layer; a game's own is given to {@link #drawCycleFrom(int)}. */
	public int getSequenceSeed()
	{return sequenceSeed;}

	/** The seed the cycle was last drawn from. */
	public int getDrawnSeed()
	{return drawnSeed;}

	/** How many slots the cycle holds; 0 for other kinds. */
	public int getSequenceLength()
	{return cycle == null ? 0 : cycle.length;}

	/** The segment (index in {@link #getTexRegion()}) a slot of the cycle draws. */
	public int getCycleSegment(int slot)
	{return cycle[slot];}

	/**
	 * Sets a SEQUENCE layer's segments (one per region, in {@link #getTexRegion()}'s order), the seed the page stores and
	 * the cycle's length, and draws the cycle from that seed.
	 */
	public void setSequence(List<Sequence_Segment> segments, int seed, int length)
	{
		if (kind != Enum_LayerKind.SEQUENCE)
			throw new IllegalStateException("Only a SEQUENCE layer chains segments, not " + kind);
		if (segments.size() != texRegion.size())
			throw new IllegalArgumentException(segments.size() + " segments for " + texRegion.size() + " regions");
		this.segments = segments;
		segmentWeights = new int[segments.size()];
		for (int i = 0; i < segmentWeights.length; i++)
			segmentWeights[i] = segments.get(i).weight;
		this.sequenceSeed = seed;
		cycle = new int[Math.max(1, length)];
		cycleEdges = new float[cycle.length + 1];
		drawCycleFrom(seed);
	}

	/**
	 * Draws a SEQUENCE layer's cycle again from {@code seed}, in place: the page keeps its stored seed. Allocates
	 * nothing; does nothing to other kinds.
	 */
	public void drawCycleFrom(int seed)
	{
		if (kind != Enum_LayerKind.SEQUENCE)
			return;
		SequenceCycle.draw(seed, segmentWeights, cycle);
		drawnSeed = seed;
		measureCycle();
	}

	/** The cycle's edges, in layer heights, from the slots' segments. */
	private void measureCycle()
	{
		if (cycle == null || segmentAspect == null)
			return;
		float edge = 0;
		for (int slot = 0; slot < cycle.length; slot++)
		{
			cycleEdges[slot] = edge;
			edge += segmentAspect[cycle[slot]];
		}
		cycleEdges[cycle.length] = edge;
	}

	public Enum_FloorMapping getFloorMapping()
	{return floorMapping;}

	/** A FLOOR layer's top row's speed ratio X; its bottom row's is {@link #getParallaxSpeedRatioX()}. */
	public float getFloorTopSpeed()
	{return floorTopSpeed;}

	public int getFloorRows()
	{return floorRows;}

	/** A FLOOR layer's box's height in percent of the world height; 0: as high as its image makes it. */
	public float getFloorHeight()
	{return floorHeight;}

	/**
	 * Sets how a FLOOR layer's rows map its image, its top row's speed, how many rows it is cut in (2 to
	 * {@link #MAX_FLOOR_ROWS}) and its box's height (percent of the world height, 0: its image's), and measures its rows
	 * again. Kept on other kinds, which draw no rows, so a page saved from its layers stores them back.
	 */
	public void setFloor(Enum_FloorMapping mapping, float topSpeed, int rows, float height)
	{
		floorMapping = mapping == null ? Enum_FloorMapping.MODE7 : mapping;
		floorTopSpeed = topSpeed;
		floorRows = rows;
		floorHeight = height;
		measureFloor();
	}

	/** The most rows a FLOOR layer is cut in; and the fewest, 2. */
	public static final int MAX_FLOOR_ROWS = 512;
	/**
	 * Mode 7's tilt: a step up the bottom rows crosses the ground this many times as fast as a step across. The floor
	 * lab's (r275), what Simon tried.
	 */
	public static final float FLOOR_TILT = 0.6f;

	/** How many rows a FLOOR layer draws: its setting within 2 and {@link #MAX_FLOOR_ROWS}; 0 for other kinds. */
	public int getFloorRowCount()
	{return rowSpeed == null ? 0 : rowSpeed.length;}

	/**
	 * A FLOOR layer's rows, from its settings and its image (FloorLab.drawFloor's math, r275). Row i of n covers the box's
	 * heights i/n to (i+1)/n; its speed is the bottom row's (the layer's own) at 0, the top row's at 1, straight between,
	 * taken at the row's middle: in a perspective a row's height over the horizon goes as 1 / its depth, and so does its
	 * speed. MODE7 stretches a row's tiles by its speed over the bottom row's, and draws the image's rows where the ground
	 * is at that depth: depth goes as 1 / speed, the ground runs {@code (depth - 1) * near} along from the bottom row,
	 * {@code near} set so a step up the bottom rows of the box crosses the ground {@link #FLOOR_TILT} times as fast as a
	 * step across, the image's own height (its pixels square on the ground) one tile of depth; a row whose ground crosses
	 * the image's top edge draws up to it. LINE_SCROLL only shifts each row: row i draws the image's rows i/n to (i+1)/n,
	 * stretched to the box. Allocates only when the row count changes.
	 */
	private void measureFloor()
	{
		if (kind != Enum_LayerKind.FLOOR || drawnRegions == null)
			return;
		int n = Math.max(2, Math.min(MAX_FLOOR_ROWS, floorRows));
		if (rowSpeed == null || rowSpeed.length != n)
		{
			float start = rowDistance == null ? currentDistanceX : rowDistance[0];
			rowSpeed = new float[n];
			rowStretch = new float[n];
			rowDistance = new float[n];
			rowStrips = new TextureRegion[n];
			for (int i = 0; i < n; i++)
				rowStrips[i] = new TextureRegion();
			Arrays.fill(rowDistance, start);
		}
		float bottom = parallaxSpeedRatioX, top = floorTopSpeed;
		boolean mode7 = floorMapping == Enum_FloorMapping.MODE7;
		// In image heights: how far along the ground the row at t is from the bottom row is (depth - 1) * nearInHeights.
		float imageHeight = getRegionHeight();
		boolean recedes = mode7 && Math.abs(bottom) > Math.abs(top) && bottom * top >= 0 && imageHeight > 0;
		float nearInHeights = recedes ? getHeight() / imageHeight * bottom / (FLOOR_TILT * (bottom - top)) : 0;
		TextureRegion image = drawnRegions.get(0);
		float u = image.getU(), u2 = image.getU2(), v = image.getV(), v2 = image.getV2();
		for (int i = 0; i < n; i++)
		{
			float t0 = (float) i / n, t1 = (float) (i + 1) / n, tc = (i + 0.5f) / n;
			float speed = bottom + (top - bottom) * tc;
			rowSpeed[i] = speed;
			rowStretch[i] = mode7 && bottom != 0 ? Math.max(0.02f, speed / bottom) : 1;
			// Which rows of the image, up from its bottom: t0..t1 of it, or in mode 7 where the ground is at those depths.
			float from = t0, to = t1;
			if (recedes)
			{
				from = along(bottom, top, t0, nearInHeights);
				to = along(bottom, top, t1, nearInHeights);
				float start = from - (float) Math.floor(from);
				to = Math.min(1, start + (to - from));
				from = start;
			}
			// The image's rows count down from its top (v), t up from its bottom. A region with no texture (a test's) has no
			// texture coordinates to cut: its strips stay empty, placed all the same.
			if (image.getTexture() == null)
				continue;
			rowStrips[i].setTexture(image.getTexture());
			rowStrips[i].setRegion(u, v + (v2 - v) * (1 - to), u2, v + (v2 - v) * (1 - from));
		}
	}

	/** How far along the ground the row at {@code t} is from the bottom row, in image heights: (depth - 1) * near. */
	private static float along(float bottom, float top, float t, float nearInHeights)
	{
		float speed = bottom + (top - bottom) * t;
		return (bottom / speed - 1) * nearInHeights;
	}

	/**
	 * Draws a FLOOR layer's rows in its box, whose bottom left corner is at {@code y} and whose rows tile across the view
	 * from {@code viewLeft} to {@code viewRight}: row i's tiles are {@code width * stretch} wide, stretched about the
	 * view's middle, and start where the row has scrolled to. One {@code batch.draw(region, x, y, width, height)} a tile
	 * of a row, the call every engine's batch draws; nothing allocated.
	 */
	public void drawFloor(Batch batch, float y, float viewLeft, float viewRight, float viewBottom, float viewTop)
	{
		if (rowStrips == null)
			return;
		int n = rowStrips.length;
		float width = getRegionWidth(), rowHeight = getHeight() / n, middle = (viewLeft + viewRight) / 2;
		for (int i = 0; i < n; i++)
		{
			float rowY = y + i * rowHeight;
			if (rowY + rowHeight <= viewBottom || rowY >= viewTop)
				continue;
			float across = width * rowStretch[i];
			if (!(across > 0))
				continue;
			float origin = middle + (viewLeft - middle) * rowStretch[i] + rowDistance[i];
			float first = origin + across * (float) Math.floor((viewLeft - origin) / across);
			int count = (int) Math.ceil((viewRight - first) / across);
			for (int k = 0; k < count; k++)
				batch.draw(rowStrips[i], first + k * across, rowY, across, rowHeight);
		}
	}

	/** A tower's radius in percent of the world width; 0: the layer is flat. Kept on every kind, drawn on IMAGE only. */
	public float getTowerRadius()
	{return towerRadius;}

	/** How much a tower's wall darkens toward its sides, 0 to 1. */
	public float getTowerShade()
	{return towerShade;}

	/**
	 * Sets the radius of the tower the layer is wrapped round (percent of the world width, 0: flat) and how much its wall
	 * darkens as it turns away (0 to 1). Kept on kinds other than IMAGE, which are drawn flat, so a page saved from its
	 * layers stores them back.
	 */
	public void setTower(float radius, float shade)
	{
		towerRadius = radius;
		towerShade = shade;
	}

	/**
	 * Where the camera looking at the tower stands, in tower radii from its axis: above 1, its courses arc and the page's
	 * slower layers wrap round it ({@link Parallax_Model#towerCamera}); 0 (or up to 1), far away. Kept on every kind.
	 */
	public float getTowerCamera()
	{return towerCamera;}

	/** Sets {@link #getTowerCamera}, in tower radii from its axis. */
	public void setTowerCamera(float camera)
	{towerCamera = camera;}

	/** Where a LURKER layer's draws start ({@link Parallax_Model#lurkerSeed}). Kept on every kind. */
	public int getLurkerSeed()
	{return lurkerSeed;}

	/** After a LURKER layer's figure has gone, each later sighting shows it by 1 chance in this, 1 at least. */
	public int getLurkerOdds()
	{return lurkerOdds;}

	/** Where the layers in front of a LURKER layer cover it wholly ({@link Parallax_Model#lurkerCover}); never null. */
	public float[] getLurkerCover()
	{return lurkerCover;}

	/** Sets a LURKER layer's seed, its odds (1 at least) and its cover (null: none). The cover is kept, not copied. */
	public void setLurker(int seed, int odds, float[] cover)
	{
		lurkerSeed = seed;
		lurkerOdds = Math.max(1, odds);
		lurkerCover = cover == null ? new float[0] : cover;
	}

	/** The name of the animated patch's frames in the page's atlas ({@link Parallax_Model#animRegion}); empty: none. */
	public String getAnimRegion()
	{return animRegion;}

	/** Where the patch's bottom-left corner lies in the layer's region, in its pixels from its bottom-left. */
	public int getAnimX()
	{return animX;}

	public int getAnimY()
	{return animY;}

	/** The patch's frames a second. */
	public float getAnimFps()
	{return animFps;}

	/** Sets the patch's frames' name (null: none), its place in the region's pixels and its frame rate. */
	public void setAnim(String region, int x, int y, float fps)
	{
		animRegion = region == null ? "" : region;
		animX = x;
		animY = y;
		animFps = fps;
	}

	/** The patch's frames, as given to {@link #setAnimFrames}; null when it has none. */
	public List<TextureRegion> getAnimFrames()
	{return animFrames == null ? null : animTexRegions;}

	/**
	 * The patch's frames, in order (null or empty: none), each drawn at its own size and trim: the page finds them in its
	 * atlas when it builds the layer. Not in a frame: it measures them.
	 */
	public void setAnimFrames(List<TextureRegion> frames)
	{
		animTexRegions = frames == null || frames.isEmpty() ? null : frames;
		animFrame = 0;
		measureAnim();
	}

	/** The frame drawn: {@code floor(time × fps)} modulo the frame count, on the reader's clock ({@link Parallax_Model#animFps}). */
	public void setAnimTime(double time)
	{
		if (animFrames == null)
			return;
		int count = animFrames.size();
		// A NaN frame rate casts to frame 0; a negative one plays backwards.
		int frame = (int) (Math.floor(time * animFps) % count);
		animFrame = frame < 0 ? frame + count : frame;
	}

	/** The frame {@link #setAnimTime} chose. */
	public int getAnimFrame()
	{return animFrame;}

	/** Whether this layer draws a patch over its tiles: an IMAGE or SHADER layer with frames. */
	public boolean hasAnim()
	{return animFrames != null && (kind == Enum_LayerKind.IMAGE || kind == Enum_LayerKind.SHADER);}

	/** The current frame as drawn (inset by half a texel); null without a patch. */
	public TextureRegion getAnimDrawnRegion()
	{return animFrames == null ? null : animFrames.get(animFrame);}

	/** The current frame's packed image's width as drawn, in world units: the layer's u_size is its image's. */
	public float getAnimDrawnWidth()
	{return animFrames == null ? 0 : animWidth[animFrame] * getRegionWidth() / imageWidth * animPackedWidth[animFrame];}

	/** The same, its height. */
	public float getAnimDrawnHeight()
	{return animFrames == null ? 0 : animHeight[animFrame] * getRegionHeight() / imageHeight * animPackedHeight[animFrame];}

	/** Each frame's pixel size and trim, as {@link #setTexRegion} measures the layer's region. */
	private void measureAnim()
	{
		if (animTexRegions == null)
		{
			animFrames = null;
			return;
		}
		int count = animTexRegions.size();
		List<TextureRegion> drawn = new ArrayList<>(count);
		float[] width = new float[count], height = new float[count], left = new float[count], bottom = new float[count];
		float[] packedWidth = new float[count], packedHeight = new float[count];
		for (int i = 0; i < count; i++)
		{
			TextureRegion frame = animTexRegions.get(i);
			drawn.add(insetByHalfATexel(frame));
			width[i] = frame.getRegionWidth();
			height[i] = frame.getRegionHeight();
			packedWidth[i] = packedHeight[i] = 1;
			if (useOriginalSize && frame instanceof AtlasRegion)
			{
				AtlasRegion atlasRegion = (AtlasRegion) frame;
				if (atlasRegion.originalWidth > 0 && atlasRegion.originalHeight > 0)
				{
					left[i] = atlasRegion.offsetX / atlasRegion.originalWidth;
					bottom[i] = atlasRegion.offsetY / atlasRegion.originalHeight;
					packedWidth[i] = (float) atlasRegion.packedWidth / atlasRegion.originalWidth;
					packedHeight[i] = (float) atlasRegion.packedHeight / atlasRegion.originalHeight;
					width[i] = atlasRegion.originalWidth;
					height[i] = atlasRegion.originalHeight;
				}
			}
		}
		animWidth = width;
		animHeight = height;
		animTrimLeft = left;
		animTrimBottom = bottom;
		animPackedWidth = packedWidth;
		animPackedHeight = packedHeight;
		animFrames = drawn;
	}

	/** True when this is a tower seen from close by: its courses arc, and its page's slower layers wrap round it. */
	public boolean isNearTower()
	{return isTower() && towerCamera > 1;}

	/**
	 * How much a tower seen from {@code camera} radii from its axis ({@link #getTowerCamera}) scales its wall at angle
	 * {@code a} from its front, about the view's middle: its front's distance over the strip's,
	 * {@code (camera - 1) / (camera - cos a)}; 1 for a camera up to 1 (far away).
	 */
	public static float towerScaleAt(float camera, float a)
	{return camera > 1 ? (camera - 1) / (camera - (float) Math.cos(a)) : 1;}

	/** True when the reader wraps this layer round a tower ({@link #drawTower}): an IMAGE layer with a radius. */
	public boolean isTower()
	{return kind == Enum_LayerKind.IMAGE && towerRadius > 0;}

	/** How many strips a tower's front half is cut in, by angle: {@code PI / TOWER_STRIPS} each. */
	public static final int TOWER_STRIPS = 128;
	private static final float HALF_PI = (float) (Math.PI / 2), STRIP_ANGLE = (float) (Math.PI / TOWER_STRIPS);

	/**
	 * Draws the layer wrapped round a tower (docs/depth-tricks.md §2, r276): a cylinder of {@link #getTowerRadius} whose
	 * axis is at {@code axisX}, so {@code 2 radius} wide, of the layer's height, its box's bottom at {@code y}. The image
	 * runs round it at its own scale: the point an arc {@code s} round the wall from its front (the angle
	 * {@code a = s / radius}) shows what the flat layer, its tile at {@code x}, shows at {@code axisX + s}, and is drawn at
	 * {@code axisX + radius sin(a)}, so the image narrows toward the sides and the layer's scroll turns the tower. Its
	 * tiles follow each other round it, padX apart, whatever the page repeats; flips mirror each image in its own place.
	 * The front half, cut in {@link #TOWER_STRIPS} strips of equal angle and at each image's edges, one
	 * {@code batch.draw(region, x, y, width, height)} a piece, the ones outside {@code viewLeft} to {@code viewRight}
	 * skipped; each piece at {@code 1 - towerShade (1 - cos a)} of its brightness, at its middle. Seen from close by
	 * ({@link #isNearTower}), each piece is also scaled up and down about {@code eyeY} by {@link #towerScaleAt} at its
	 * middle, so the courses arc toward the eye at the sides (r350). {@code packed}: the batch
	 * colour holds the page fog's numbers ({@link LayerEffects#beginPageFog}), the darkness goes in its blue; otherwise
	 * its red, green and blue are multiplied. The batch colour is put back after. Nothing allocated.
	 */
	public void drawTower(Batch batch, float axisX, float x, float y, float viewLeft, float viewRight, float eyeY, boolean packed)
	{
		float radius = towerRadius * (worldWidth / 100), width = getRegionWidth(), height = getRegionHeight();
		if (!(radius > 0) || !(width > 0) || !(height > 0))
			return;
		// What shows of the front half: its arc from the view's left edge to its right one.
		float from = radius * (float) Math.asin(Math.max(-1, Math.min(1, (viewLeft - axisX) / radius)));
		float to = radius * (float) Math.asin(Math.max(-1, Math.min(1, (viewRight - axisX) / radius)));
		if (!(to > from))
			return;
		// The image in its tile, along the arc, and up the box, as drawRegion places it.
		float imageLeft = width * (flipX ? 1 - trimLeft - packedWidthRatio : trimLeft), imageWidth = width * packedWidthRatio;
		float drawHeight = height * packedHeightRatio;
		float bottom = y + height * (flipY ? 1 - trimBottom - packedHeightRatio : trimBottom);
		float drawY = flipY ? bottom + drawHeight : bottom, drawH = flipY ? -drawHeight : drawHeight;
		// Tile k's image starts at an arc of corner + k * step; a step <= 0 can't tile: the image goes round once.
		float corner = x - axisX + imageLeft, step = getTotalWidth();
		int first = 0, last = 0;
		if (step > 0)
		{
			first = (int) Math.floor((from - corner - imageWidth) / step);
			last = (int) Math.ceil((to - corner) / step);
		}
		TextureRegion image = drawnRegions.get(0);
		float u = image.getU(), u2 = image.getU2(), v = image.getV(), v2 = image.getV2();
		Color color = batch.getColor();
		float r = color.r, g = color.g, b = color.b, a = color.a;
		for (int k = first; k <= last; k++)
		{
			float left = corner + k * step;
			float lo = Math.max(from, left), hi = Math.min(to, left + imageWidth);
			if (!(hi > lo))
				continue;
			int strip = Math.max(0, (int) Math.floor((lo / radius + HALF_PI) / STRIP_ANGLE));
			int lastStrip = Math.min(TOWER_STRIPS - 1, (int) Math.floor((hi / radius + HALF_PI) / STRIP_ANGLE));
			for (; strip <= lastStrip; strip++)
			{
				float p0 = Math.max(lo, radius * (strip * STRIP_ANGLE - HALF_PI));
				float p1 = Math.min(hi, radius * ((strip + 1) * STRIP_ANGLE - HALF_PI));
				if (!(p1 > p0))
					continue;
				float x0 = axisX + radius * (float) Math.sin(p0 / radius), x1 = axisX + radius * (float) Math.sin(p1 / radius);
				float mid = (p0 + p1) / (2 * radius), bright = 1 - towerShade * (1 - (float) Math.cos(mid));
				float scale = towerScaleAt(towerCamera, mid), pieceY = eyeY + (drawY - eyeY) * scale, pieceH = drawH * scale;
				// The piece's share of its image, left to right; flipped on X the image runs right to left.
				float f0 = (p0 - left) / imageWidth, f1 = (p1 - left) / imageWidth;
				if (image.getTexture() != null)
				{
					towerStrip.setTexture(image.getTexture());
					towerStrip.setRegion(u + (u2 - u) * (flipX ? 1 - f1 : f0), v, u + (u2 - u) * (flipX ? 1 - f0 : f1), v2);
				}
				if (packed)
					batch.setColor(r, g, 1 - bright, a);
				else
					batch.setColor(r * bright, g * bright, b * bright, a);
				if (flipX)
					batch.draw(towerStrip, x1, pieceY, x0 - x1, pieceH);
				else
					batch.draw(towerStrip, x0, pieceY, x1 - x0, pieceH);
			}
		}
		batch.setColor(r, g, b, a);
	}

	/** Where a FLOOR layer's row {@code i} (from the bottom) has scrolled to, within one of its tiles. */
	public float getRowDistance(int i)
	{return rowDistance[i];}

	/** A FLOOR layer's row {@code i}'s speed ratio X. */
	public float getRowSpeed(int i)
	{return rowSpeed[i];}

	/** How much a FLOOR layer's row {@code i}'s tiles are stretched across: 1 in LINE_SCROLL. */
	public float getRowStretch(int i)
	{return rowStretch[i];}

	/** The strip of the image a FLOOR layer's row {@code i} draws. */
	public TextureRegion getRowStrip(int i)
	{return rowStrips[i];}

	/**
	 * Where the layer stands, in world units above the layers' y 0 at its decal: the bottom of what it draws, its decal
	 * and, for an image whose atlas trimmed transparent rows off its bottom, those rows (PurpleFairy's pink trees: 95 of
	 * 1080). What {@link ParallaxPageReader#plant} reads.
	 */
	public float getFootY()
	{
		float foot = decalPercentY * (worldHeight / 100);
		if (kind == Enum_LayerKind.IMAGE || kind == Enum_LayerKind.SHADER || kind == Enum_LayerKind.LURKER)
			foot += getRegionHeight() * (flipY ? 1 - trimBottom - packedHeightRatio : trimBottom);
		return foot;
	}

	/** The packed image's share of the layer's width: the part of the box the region is drawn over. */
	public float getPackedWidthRatio()
	{return packedWidthRatio;}

	/** The packed image's share of the layer's height. */
	public float getPackedHeightRatio()
	{return packedHeightRatio;}

	/** The region drawn, the first of {@link #getTexRegion()}; null unless the layer draws one. */
	public TextureRegion getRegion()
	{return region;}

	/**
	 * What is drawn of {@link #getRegion()}: inset by half a texel on every side (r218). Effects map texture coordinates
	 * through this one, so their pattern lands where the quad's texels do.
	 */
	public TextureRegion getDrawnRegion()
	{return drawnRegions == null ? null : drawnRegions.get(0);}

	/** Where the packed image's left edge sits in the layer's box, unflipped, in fractions of its width; 0 untrimmed. */
	float getTrimLeft()
	{return trimLeft;}

	/** Where the packed image's bottom sits in the layer's box, unflipped, in fractions of its height; 0 untrimmed. */
	float getTrimBottom()
	{return trimBottom;}

	/**
	 * A SEQUENCE layer's segment (index in {@link #getTexRegion()}): its width in layer heights, then where its packed
	 * image sits in its slot as {@link #getTrimLeft}, {@link #getTrimBottom}, {@link #getPackedWidthRatio} and
	 * {@link #getPackedHeightRatio} say for one region. Null for other kinds.
	 */
	float[] getSegmentBox(int segment)
	{
		if (segmentAspect == null)
			return null;
		return new float[] { segmentAspect[segment], segmentTrimLeft[segment], segmentTrimBottom[segment], segmentPackedWidth[segment],
				segmentPackedHeight[segment] };
	}

	public boolean isUseOriginalSize()
	{return useOriginalSize;}

	/** Resizes the layer: from the region's original size when true, from its packed size when false. */
	public void setUseOriginalSize(boolean useOriginalSize)
	{
		this.useOriginalSize = useOriginalSize;
		if (drawsImage())
			setTexRegion(texRegion);
	}

	/** Swaps the texture(s) drawn by this layer, keeping its world width and recomputing its height. */
	public void setTexRegion(List<TextureRegion> texRegion)
	{
		if (!drawsImage())
			throw new IllegalStateException("A " + kind + " layer draws no region");
		if (texRegion == null || texRegion.isEmpty())
			throw new IllegalArgumentException("A parallax layer needs at least one texture region");

		this.texRegion = texRegion;
		this.region = texRegion.get(0);
		List<TextureRegion> drawn = new ArrayList<>(texRegion.size());
		for (int i = 0, n = texRegion.size(); i < n; i++)
			drawn.add(insetByHalfATexel(texRegion.get(i)));
		this.drawnRegions = drawn;
		towerStrip = new TextureRegion();

		float imageWidth = region.getRegionWidth();
		float imageHeight = region.getRegionHeight();
		trimLeft = trimBottom = 0;
		packedWidthRatio = packedHeightRatio = 1;
		if (useOriginalSize && region instanceof AtlasRegion)
		{
			AtlasRegion atlasRegion = (AtlasRegion) region;
			if (atlasRegion.originalWidth > 0 && atlasRegion.originalHeight > 0)
			{
				trimLeft = atlasRegion.offsetX / atlasRegion.originalWidth;
				trimBottom = atlasRegion.offsetY / atlasRegion.originalHeight;
				packedWidthRatio = (float) atlasRegion.packedWidth / atlasRegion.originalWidth;
				packedHeightRatio = (float) atlasRegion.packedHeight / atlasRegion.originalHeight;
				imageWidth = atlasRegion.originalWidth;
				imageHeight = atlasRegion.originalHeight;
			}
		}
		this.imageWidth = imageWidth;
		this.imageHeight = imageHeight;

		if (isWidth)
		{
			regionWidth = worldDimension;
			regionHeight = Utils_Parallax.calculateOtherDimension(true, worldDimension, imageWidth, imageHeight);
		}
		else
		{
			regionHeight = worldDimension;
			regionWidth = Utils_Parallax.calculateOtherDimension(false, worldDimension, imageWidth, imageHeight);
		}

		if (kind == Enum_LayerKind.SEQUENCE)
			measureSegments();
		measureFloor();
		measureAnim();
	}

	/** Each segment's width in layer heights and where its packed image sits, as {@link #setTexRegion} does for one. */
	private void measureSegments()
	{
		int count = texRegion.size();
		if (segmentAspect == null || segmentAspect.length != count)
		{
			segmentAspect = new float[count];
			segmentTrimLeft = new float[count];
			segmentTrimBottom = new float[count];
			segmentPackedWidth = new float[count];
			segmentPackedHeight = new float[count];
		}
		narrowestSegment = Float.POSITIVE_INFINITY;
		for (int i = 0; i < count; i++)
		{
			TextureRegion segment = texRegion.get(i);
			float imageWidth = segment.getRegionWidth(), imageHeight = segment.getRegionHeight();
			segmentTrimLeft[i] = segmentTrimBottom[i] = 0;
			segmentPackedWidth[i] = segmentPackedHeight[i] = 1;
			if (useOriginalSize && segment instanceof AtlasRegion)
			{
				AtlasRegion atlasRegion = (AtlasRegion) segment;
				if (atlasRegion.originalWidth > 0 && atlasRegion.originalHeight > 0)
				{
					segmentTrimLeft[i] = atlasRegion.offsetX / atlasRegion.originalWidth;
					segmentTrimBottom[i] = atlasRegion.offsetY / atlasRegion.originalHeight;
					segmentPackedWidth[i] = (float) atlasRegion.packedWidth / atlasRegion.originalWidth;
					segmentPackedHeight[i] = (float) atlasRegion.packedHeight / atlasRegion.originalHeight;
					imageWidth = atlasRegion.originalWidth;
					imageHeight = atlasRegion.originalHeight;
				}
			}
			// The first segment is the layer's own width, as an image layer's region is.
			segmentAspect[i] = i == 0 ? regionWidth / regionHeight : imageWidth / imageHeight;
			narrowestSegment = Math.min(narrowestSegment, segmentAspect[i]);
		}
		measureCycle();
	}

	public void setTexRegion(TextureRegion texRegion)
	{setTexRegion(singletonList(texRegion));}

	/**
	 * {@code region} with its edges moved half a texel inwards: a linear sample at the drawn quad's edge then reads the
	 * edge texel alone, never the one past it. Stretches the image by one texel, a third of a pixel on a 3000-texel
	 * layer drawn screen-wide. A region without a texture (a test's) or under 2 texels wide is drawn as is.
	 */
	static TextureRegion insetByHalfATexel(TextureRegion region)
	{
		Texture texture = region.getTexture();
		if (texture == null || Math.abs(region.getRegionWidth()) < 2 || Math.abs(region.getRegionHeight()) < 2)
			return region;
		float du = Math.signum(region.getU2() - region.getU()) * 0.5f / texture.getWidth();
		float dv = Math.signum(region.getV2() - region.getV()) * 0.5f / texture.getHeight();
		TextureRegion inset = new TextureRegion(region);
		inset.setRegion(region.getU() + du, region.getV() + dv, region.getU2() - du, region.getV2() - dv);
		return inset;
	}
}
