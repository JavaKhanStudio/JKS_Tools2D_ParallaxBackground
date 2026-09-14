package jks.tools2d.parallax;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.fasterxml.jackson.annotation.JsonIgnoreType;

import jks.tools2d.parallax.heart.Gvars_Parallax;
import jks.tools2d.parallax.pages.Parallax_Model;

/**
 * One scrolling plane of a parallax. Sizes are in world units: the layer is {@code worldDimension * sizeRatio} wide
 * (or high), the other dimension follows the texture aspect ratio.
 */
@JsonIgnoreType
public class ParallaxLayer implements Cloneable
{
	private List<TextureRegion> texRegion;
	/** Cached first region, the one actually drawn. */
	private TextureRegion region;

	private final boolean isWidth;
	private final float worldDimension;

	private float decalPercentX, decalPercentY;
	private float regionWidth, regionHeight;
	private float sizeRatio = 1;

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
		this.isWidth = isWidth;
		this.worldDimension = worldDimension;
		this.sizeRatio = sizeRatio;
		setTexRegion(texRegion);
		setParallaxSpeedRatioX(parallaxScrollRatioX);
		setParallaxSpeedRatioY(parallaxScrollRatioY);
	}

	public ParallaxLayer(TextureRegion texRegion, boolean isWidth, float worldDimension, float parallaxScrollRatioX, float parallaxScrollRatioY, float sizeRatio)
	{
		this(singletonList(texRegion), isWidth, worldDimension, parallaxScrollRatioX, parallaxScrollRatioY, sizeRatio);
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
	}

	public void resetPosition()
	{
		currentDistanceX = decalPercentX * Gvars_Parallax.getWidthPercent();
		currentDistanceY = decalPercentY * Gvars_Parallax.getHeightPercent();
	}

	public void draw(Batch batch, float x, float y)
	{
		float width = getRegionWidth();
		float height = getRegionHeight();
		batch.draw(region,
			flipX ? x + width : x,
			flipY ? y + height : y,
			flipX ? -width : width,
			flipY ? -height : height);
	}

	/** Draws the mirrored copy: flipped vertically when tiling on X, horizontally when tiling on Y. */
	public void drawMirror(Batch batch, float x, float y, boolean onX)
	{
		boolean fx = onX ? flipX : !flipX;
		boolean fy = onX ? !flipY : flipY;
		float width = getRegionWidth();
		float height = getRegionHeight();
		batch.draw(region,
			fx ? x + width : x,
			fy ? y + height : y,
			fx ? -width : width,
			fy ? -height : height);
	}

	@Override
	public ParallaxLayer clone()
	{
		try
		{
			ParallaxLayer copy = (ParallaxLayer) super.clone();
			copy.texRegion = new ArrayList<>(texRegion);
			return copy;
		}
		catch (CloneNotSupportedException e)
		{
			throw new AssertionError(e);
		}
	}

	public void act(float delta, float speedX, float speedY, boolean onX, boolean onY)
	{
		currentDistanceY -= delta * speedY * parallaxSpeedRatioY;
		currentDistanceX -= delta * (speedXAtRest + speedX) * parallaxSpeedRatioX;

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

	public float getDecalPercentX()
	{return decalPercentX;}

	public void setDecalPercentX(float decalPercentX)
	{
		currentDistanceX += (decalPercentX - this.decalPercentX) * Gvars_Parallax.getWidthPercent();
		this.decalPercentX = decalPercentX;
	}

	public float getDecalPercentY()
	{return decalPercentY;}

	public void setDecalPercentY(float decalPercentY)
	{
		currentDistanceY += (decalPercentY - this.decalPercentY) * Gvars_Parallax.getHeightPercent();
		this.decalPercentY = decalPercentY;
	}

	public float getRegionWidth()
	{return regionWidth * sizeRatio;}

	public float getRegionHeight()
	{return regionHeight * sizeRatio;}

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
	{return currentDistanceX - decalPercentX * Gvars_Parallax.getWidthPercent();}

	public void setScrollX(float scroll)
	{currentDistanceX = decalPercentX * Gvars_Parallax.getWidthPercent() + scroll;}

	/** Distance scrolled vertically since the layer was placed at its decal position. */
	public float getScrollY()
	{return currentDistanceY - decalPercentY * Gvars_Parallax.getHeightPercent();}

	public void setScrollY(float scroll)
	{currentDistanceY = decalPercentY * Gvars_Parallax.getHeightPercent() + scroll;}

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

	public List<TextureRegion> getTexRegion()
	{return texRegion;}

	/** Swaps the texture(s) drawn by this layer, keeping its world width and recomputing its height. */
	public void setTexRegion(List<TextureRegion> texRegion)
	{
		if (texRegion == null || texRegion.isEmpty())
			throw new IllegalArgumentException("A parallax layer needs at least one texture region");

		this.texRegion = texRegion;
		this.region = texRegion.get(0);

		if (isWidth)
		{
			regionWidth = worldDimension;
			regionHeight = Utils_Parallax.calculateOtherDimension(true, worldDimension, region);
		}
		else
		{
			regionHeight = worldDimension;
			regionWidth = Utils_Parallax.calculateOtherDimension(false, worldDimension, region);
		}
	}

	public void setTexRegion(TextureRegion texRegion)
	{setTexRegion(singletonList(texRegion));}
}
