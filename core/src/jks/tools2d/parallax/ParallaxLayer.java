package jks.tools2d.parallax;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
 * ({@link #setPlantedSpeedX}).
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

	/** True for the kinds that draw a region: IMAGE, SHADER, SEQUENCE and FLOOR. */
	public boolean drawsImage()
	{return drawsImage(kind);}

	private static boolean drawsImage(Enum_LayerKind kind)
	{return kind == Enum_LayerKind.IMAGE || kind == Enum_LayerKind.SHADER || kind == Enum_LayerKind.SEQUENCE || kind == Enum_LayerKind.FLOOR;}

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

	/**
	 * Draws the region at (x, y); an EMPTY layer draws nothing, its hook does (ParallaxPageReader). A SHADER layer's
	 * effect is the batch's shader, which the reader sets around its tiles.
	 */
	public void draw(Batch batch, float x, float y)
	{
		if (kind == Enum_LayerKind.SEQUENCE)
			drawCycle(batch, x, y, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, flipX, flipY);
		else if (drawsImage())
			drawRegion(batch, drawnRegions.get(0), x, y, getRegionWidth(), getRegionHeight(), trimLeft, trimBottom, packedWidthRatio, packedHeightRatio, flipX, flipY);
	}

	/** Draws the mirrored copy: flipped vertically when tiling on X, horizontally when tiling on Y. */
	public void drawMirror(Batch batch, float x, float y, boolean onX)
	{
		boolean fx = onX ? flipX : !flipX;
		boolean fy = onX ? !flipY : flipY;
		if (kind == Enum_LayerKind.SEQUENCE)
			drawCycle(batch, x, y, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, fx, fy);
		else if (drawsImage())
			drawRegion(batch, drawnRegions.get(0), x, y, getRegionWidth(), getRegionHeight(), trimLeft, trimBottom, packedWidthRatio, packedHeightRatio, fx, fy);
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
		copy.parallaxSpeedRatioX = parallaxSpeedRatioX;
		copy.parallaxSpeedRatioY = parallaxSpeedRatioY;
		copy.worldWidth = worldWidth;
		copy.worldHeight = worldHeight;
		copy.decalPercentX = decalPercentX;
		copy.decalPercentY = decalPercentY;
		copy.regionWidth = regionWidth;
		copy.regionHeight = regionHeight;
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
		if (kind == Enum_LayerKind.IMAGE || kind == Enum_LayerKind.SHADER)
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
