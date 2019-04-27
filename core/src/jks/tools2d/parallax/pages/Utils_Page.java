package jks.tools2d.parallax.pages;

import jks.tools2d.parallax.ParallaxLayer;

public class Utils_Page
{

	public static Parallax_Model buildFromPage(ParallaxLayer page,String regionName, int region_Position)
	{
		Parallax_Model model = new Parallax_Model() ; 
		model.region_Name = regionName ; 
		model.region_Position = region_Position ; 
		model.flipX = (page.isFlipX());
		model.flipY = (page.isFlipY());
		model.parallaxScalingSpeedX = (page.getParallaxSpeedRatio().x);
		model.parallaxScalingSpeedY = (page.getParallaxSpeedRatio().y);
		model.speed = (page.getSpeedAtRest());
		model.sizeRatio = (page.getSizeRatio());
		model.decal_X_Ratio = (page.getDecalPercentX());
		model.decal_Y_Ratio = (page.getDecalPercentY());
		model.padMin = page.getPadMin() ; 
		model.padFactor =page.getPadFactor() ; 
		return model ; 
	}
	
}
