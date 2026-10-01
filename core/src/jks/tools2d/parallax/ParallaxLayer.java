package jks.tools2d.parallax;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import jks.tools2d.parallax.heart.Gvars_Parallax;
import jks.tools2d.parallax.pages.Enum_LayerKind;
import jks.tools2d.parallax.pages.Enum_ParticleAnchor;
import jks.tools2d.parallax.pages.Parallax_Model;

/**
 * One scrolling plane of a parallax. Sizes are in world units: the layer is {@code worldDimension * sizeRatio} wide
 * (or high), the other dimension follows the texture aspect ratio. An {@link Enum_LayerKind#EMPTY} layer has no
 * texture: it is the world's size times sizeRatio, and the reader calls the game's {@link LayerHook} in its place. A
 * {@link Enum_LayerKind#PARTICLES} layer is the same box, and the reader draws its {@link ParallaxParticles} there.
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

	private List<TextureRegion> texRegion;
	/** Cached first region, the one actually drawn. */
	private TextureRegion region;

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

	protected float padX;
	protected float padXFactor;

	protected float padY;
	protected float padYFactor;

	protected float speedXAtRest;

	protected boolean flipX;
	protected boolean flipY;

	protected boolean isMirror;

	public ParallaxLayer(List<TextureRegion> texRegion, boolean isWidth, float worldDimension, float parallaxScrollRatioX, float parallaxScrollRatioY, float sizeRatio)
	{
		this.kind = Enum_LayerKind.IMAGE;
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
	 * says; a null effect draws nothing. Takes the effect over: {@link ParallaxParticles#allocate()} and starts it.
	 */
	public static ParallaxLayer particles(ParallaxParticles effect, Enum_ParticleAnchor anchor, float sizeRatio)
	{
		ParallaxLayer layer = new ParallaxLayer(Enum_LayerKind.PARTICLES, null, sizeRatio);
		layer.anchor = anchor == null ? Enum_ParticleAnchor.LAYER : anchor;
		layer.setParticles(effect);
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
		if (kind == Enum_LayerKind.PARTICLES && model.particlesAnchor != null)
			anchor = model.particlesAnchor;
	}

	public void resetPosition()
	{
		currentDistanceX = decalPercentX * (worldWidth / 100);
		currentDistanceY = decalPercentY * (worldHeight / 100);
	}

	/** Draws the region at (x, y); an EMPTY layer draws nothing, its hook does (ParallaxPageReader). */
	public void draw(Batch batch, float x, float y)
	{
		if (kind == Enum_LayerKind.IMAGE)
			drawRegion(batch, x, y, flipX, flipY);
	}

	/** Draws the mirrored copy: flipped vertically when tiling on X, horizontally when tiling on Y. */
	public void drawMirror(Batch batch, float x, float y, boolean onX)
	{
		boolean fx = onX ? flipX : !flipX;
		boolean fy = onX ? !flipY : flipY;
		if (kind == Enum_LayerKind.IMAGE)
			drawRegion(batch, x, y, fx, fy);
	}

	/** Draws the packed image in the layer's box at (x, y); a flip mirrors where it sits in the box too. */
	private void drawRegion(Batch batch, float x, float y, boolean fx, boolean fy)
	{
		float width = getRegionWidth();
		float height = getRegionHeight();
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
		ParallaxLayer copy = kind != Enum_LayerKind.IMAGE ? new ParallaxLayer(kind, name, sizeRatio)
				: new ParallaxLayer(new ArrayList<>(texRegion), isWidth, worldDimension, parallaxSpeedRatioX, parallaxSpeedRatioY, sizeRatio);
		copy.name = name;
		// Its own effect: both play during a cross-fade, and one effect updated twice a frame would run double speed.
		if (particles != null)
			copy.setParticles(new ParallaxParticles(particles));
		copy.anchor = anchor;
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
		copy.padX = padX;
		copy.padXFactor = padXFactor;
		copy.padY = padY;
		copy.padYFactor = padYFactor;
		copy.speedXAtRest = speedXAtRest;
		copy.flipX = flipX;
		copy.flipY = flipY;
		copy.isMirror = isMirror;
		return copy;
	}

	public void act(float delta, float speedX, float speedY, boolean onX, boolean onY)
	{
		float moveX = -delta * (speedXAtRest + speedX) * parallaxSpeedRatioX;
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
			currentDistanceX %= totalWidth;

		float totalHeight = getTotalHeight();
		if (onY && totalHeight > 0)
			currentDistanceY %= totalHeight;
	}

	public float getWidth()
	{return getRegionWidth();}

	public float getHeight()
	{return getRegionHeight();}

	public float getTotalWidth()
	{return getRegionWidth() + padX;}

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
	}

	public float getDecalPercentX()
	{return decalPercentX;}

	public void setDecalPercentX(float decalPercentX)
	{
		currentDistanceX += (decalPercentX - this.decalPercentX) * (worldWidth / 100);
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
	{return (kind != Enum_LayerKind.IMAGE ? worldWidth : regionWidth) * sizeRatio;}

	public float getRegionHeight()
	{return (kind != Enum_LayerKind.IMAGE ? worldHeight : regionHeight) * sizeRatio;}

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
	{this.sizeRatio = sizeRatio;}

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
	{this.parallaxSpeedRatioX = parallaxSpeedRatioX;}

	public float getParallaxSpeedRatioY()
	{return parallaxSpeedRatioY;}

	public void setParallaxSpeedRatioY(float parallaxSpeedRatioY)
	{this.parallaxSpeedRatioY = parallaxSpeedRatioY;}

	public boolean isMirror()
	{return isMirror;}

	public void setMirror(boolean isMirror)
	{this.isMirror = isMirror;}

	/** The regions drawn; null unless the layer is an IMAGE. */
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

	/** Sets the effect a PARTICLES layer draws, null for none; allocates its particles and starts it. */
	public void setParticles(ParallaxParticles effect)
	{
		if (effect != null && kind != Enum_LayerKind.PARTICLES)
			throw new IllegalStateException("Only a PARTICLES layer draws a particle effect, not " + kind);
		particles = effect;
		if (effect != null)
		{
			effect.allocate();
			effect.start();
		}
	}

	public Enum_ParticleAnchor getAnchor()
	{return anchor;}

	public void setAnchor(Enum_ParticleAnchor anchor)
	{this.anchor = anchor == null ? Enum_ParticleAnchor.LAYER : anchor;}

	public boolean isUseOriginalSize()
	{return useOriginalSize;}

	/** Resizes the layer: from the region's original size when true, from its packed size when false. */
	public void setUseOriginalSize(boolean useOriginalSize)
	{
		this.useOriginalSize = useOriginalSize;
		if (kind == Enum_LayerKind.IMAGE)
			setTexRegion(texRegion);
	}

	/** Swaps the texture(s) drawn by this layer, keeping its world width and recomputing its height. */
	public void setTexRegion(List<TextureRegion> texRegion)
	{
		if (kind != Enum_LayerKind.IMAGE)
			throw new IllegalStateException("A " + kind + " layer draws no region");
		if (texRegion == null || texRegion.isEmpty())
			throw new IllegalArgumentException("A parallax layer needs at least one texture region");

		this.texRegion = texRegion;
		this.region = texRegion.get(0);

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
	}

	public void setTexRegion(TextureRegion texRegion)
	{setTexRegion(singletonList(texRegion));}
}
