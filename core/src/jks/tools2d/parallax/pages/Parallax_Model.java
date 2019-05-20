package jks.tools2d.parallax.pages;

import com.esotericsoftware.kryo.DefaultSerializer;

@DefaultSerializer(Parallax_Model_Serializer.class)
public class Parallax_Model 
{
	public String region_Name ; 
	public int region_Position ;
	public boolean flipX ; 
	public boolean flipY ; 
	
	public float parallaxScalingSpeedX ; 
	public float parallaxScalingSpeedY ; 
	
	public float speed ; 
	public float sizeRatio = 1; 
	public float decal_X_Ratio ; 
	public float decal_Y_Ratio ; 
	
	public float padMin ; 
	public float padFactor ;
	
	public String getRegion_Name()
	{return region_Name;}
	
	public void setRegion_Name(String region_Name)
	{this.region_Name = region_Name;}
	
	public int getRegion_Position()
	{return region_Position;}
	
	public void setRegion_Position(int region_Position)
	{this.region_Position = region_Position;}
	
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
	
	public float getSpeed()
	{return speed;}
	
	public void setSpeed(float speed)
	{this.speed = speed;}
	
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
	
	public float getPadMin()
	{return padMin;}
	
	public void setPadMin(float padMin)
	{this.padMin = padMin;}
	
	public float getPadFactor()
	{return padFactor;}
	
	public void setPadFactor(float padFactor)
	{this.padFactor = padFactor;} 

}