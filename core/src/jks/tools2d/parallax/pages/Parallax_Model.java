package jks.tools2d.parallax.pages;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.fasterxml.jackson.annotation.JsonIgnore;

/** Serialized description of one layer: which atlas region, and how it scrolls. */
@DefaultSerializer(Parallax_Model_Serializer.class)
public class Parallax_Model
{
	public String regionName;
	/** Position of the region among the atlas regions sharing {@link #regionName}. */
	public int regionPosition;
	public boolean flipX;
	public boolean flipY;

	public float parallaxScalingSpeedX;
	public float parallaxScalingSpeedY;

	public float speedXAtRest;
	public float sizeRatio = 1;
	public float decal_X_Ratio;
	public float decal_Y_Ratio;

	public float padX;
	public float padXFactor;

	public float padY;
	public float padYFactor;

	public boolean isFlipX()
	{return flipX;}

	public void setFlipX(boolean flipX)
	{this.flipX = flipX;}

	public boolean isFlipY()
	{return flipY;}

	public void setFlipY(boolean flipY)
	{this.flipY = flipY;}

	public float getParallaxScalingSpeedX()
	{return parallaxScalingSpeedX;}

	public void setParallaxScalingSpeedX(float parallaxScalingSpeedX)
	{this.parallaxScalingSpeedX = parallaxScalingSpeedX;}

	public float getParallaxScalingSpeedY()
	{return parallaxScalingSpeedY;}

	public void setParallaxScalingSpeedY(float parallaxScalingSpeedY)
	{this.parallaxScalingSpeedY = parallaxScalingSpeedY;}

	/** Alias of {@link #speedXAtRest}. */
	@JsonIgnore
	public float getSpeed()
	{return speedXAtRest;}

	@JsonIgnore
	public void setSpeed(float speed)
	{this.speedXAtRest = speed;}

	public float getSizeRatio()
	{return sizeRatio;}

	public void setSizeRatio(float sizeRatio)
	{this.sizeRatio = sizeRatio;}

	public float getDecal_X_Ratio()
	{return decal_X_Ratio;}

	public void setDecal_X_Ratio(float decal_X_Ratio)
	{this.decal_X_Ratio = decal_X_Ratio;}

	public float getDecal_Y_Ratio()
	{return decal_Y_Ratio;}

	public void setDecal_Y_Ratio(float decal_Y_Ratio)
	{this.decal_Y_Ratio = decal_Y_Ratio;}

	@JsonIgnore
	public String getCompleteRegionName()
	{return regionName + regionPosition;}
}
