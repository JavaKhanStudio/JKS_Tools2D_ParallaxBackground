package jks.tools2d.parallax;

import com.badlogic.gdx.graphics.g2d.TextureRegion;


public class Utils_Parralax 
{

	/**
	 * calculate new width/height maintaining aspect ratio
	 * @param wh what oneDimen represents
	 * @param oneDimen either width or height 
	 * @param region the texture region
	 * @return if oneDimen is width then height else width
	 */
	public static float calculateOtherDimension(boolean isWidth,float oneDimen,TextureRegion region)
	{
		float result=0;
		
		if(isWidth)
		    result = region.getRegionHeight()*(oneDimen/region.getRegionWidth());
		else
    		result = region.getRegionWidth()*(oneDimen/region.getRegionHeight());
		
		return result;
	}
	
	/**
	 * calculate new width/height maintaining aspect ratio
	 * @param wh what oneDimen represents
	 * @param oneDimen either width or height 
	 * @param originalWidth the original width
	 * @param originalHeight the original height
	 * @return if oneDimen is width then height else width
	 */
	public static float calculateOtherDimension(boolean isWidth,float oneDimen,float originalWidth, float originalHeight)
	{
		float result=0;
		
		if(isWidth)
    		    result = originalHeight*(oneDimen/originalWidth);
		else
	    		result = originalWidth*(oneDimen/originalHeight);
		
		return result;
	}
	
	
}