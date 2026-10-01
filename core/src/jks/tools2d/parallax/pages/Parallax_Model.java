package jks.tools2d.parallax.pages;

/** Serialized description of one layer: which atlas region, and how it scrolls. */
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

	/** Stored since format 3. */
	public boolean mirror;

	/** What the layer draws; stored since format 5, IMAGE before. */
	public Enum_LayerKind kind = Enum_LayerKind.IMAGE;
	/** The key a game's {@link jks.tools2d.parallax.LayerHook} is registered under; stored since format 5, null before. */
	public String name;

	public boolean isFlipX()
	{return flipX;}

	public void setFlipX(boolean flipX)
	{this.flipX = flipX;}

	public boolean isFlipY()
	{return flipY;}

	public void setFlipY(boolean flipY)
	{this.flipY = flipY;}

	public boolean isMirror()
	{return mirror;}

	public void setMirror(boolean mirror)
	{this.mirror = mirror;}

	public float getParallaxScalingSpeedX()
	{return parallaxScalingSpeedX;}

	public void setParallaxScalingSpeedX(float parallaxScalingSpeedX)
	{this.parallaxScalingSpeedX = parallaxScalingSpeedX;}

	public float getParallaxScalingSpeedY()
	{return parallaxScalingSpeedY;}

	public void setParallaxScalingSpeedY(float parallaxScalingSpeedY)
	{this.parallaxScalingSpeedY = parallaxScalingSpeedY;}

	/** Alias of {@link #speedXAtRest}. */
	public float getSpeed()
	{return speedXAtRest;}

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

	public Enum_LayerKind getKind()
	{return kind;}

	public void setKind(Enum_LayerKind kind)
	{this.kind = kind;}

	public String getName()
	{return name;}

	public void setName(String name)
	{this.name = name;}

	public String getCompleteRegionName()
	{return regionName + regionPosition;}
}
