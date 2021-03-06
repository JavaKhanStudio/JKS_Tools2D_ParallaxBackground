package jks.tools2d.parallax.data;

import java.util.ArrayList;

import jks.tools2d.parallax.pages.Parallax_Model;

public class ColdNightModel
{

	public static ArrayList<Parallax_Model> buildPages() 
	{
		ArrayList<Parallax_Model> returningList = new ArrayList<Parallax_Model>() ; 
		
		Parallax_Model cloudsTop0 = new Parallax_Model() ; 
		cloudsTop0.regionName = "cloudsDouble" ;
		cloudsTop0.sizeRatio = 0.5f ;
		cloudsTop0.regionPosition = 0 ; 
		cloudsTop0.speedXAtRest = 0.5f ;
		cloudsTop0.parallaxScalingSpeedX = .01f ; 
		cloudsTop0.parallaxScalingSpeedY = .01f ; 
		cloudsTop0.decal_Y_Ratio = 100f ;
		
		Parallax_Model cloudsTop1 = new Parallax_Model() ; 
		cloudsTop1.regionName = "cloudsDouble" ;
		cloudsTop1.sizeRatio = 0.5f ;
		cloudsTop1.regionPosition = 0 ; 
		cloudsTop1.speedXAtRest = 0.5f ;
		cloudsTop1.parallaxScalingSpeedX = .01f ; 
		cloudsTop1.parallaxScalingSpeedY = .01f ; 
		cloudsTop1.decal_Y_Ratio = 90f ;
		
		Parallax_Model cloudsTop2 = new Parallax_Model() ; 
		cloudsTop2.regionName = "cloudsDouble" ;
		cloudsTop2.sizeRatio = 0.5f ;
		cloudsTop2.regionPosition = 0 ; 
		cloudsTop2.speedXAtRest = 0.5f ;
		cloudsTop2.parallaxScalingSpeedX = .01f ; 
		cloudsTop2.parallaxScalingSpeedY = .01f ; 
		cloudsTop2.decal_Y_Ratio = 80f ;
		
		Parallax_Model clouds0 = new Parallax_Model() ; 
		clouds0.regionName = "cloudsDouble" ;
		clouds0.sizeRatio = 0.5f ;
		clouds0.regionPosition = 0 ; 
		clouds0.speedXAtRest = 0.5f ;
		clouds0.parallaxScalingSpeedX = .01f ; 
		clouds0.parallaxScalingSpeedY = .01f ; 
		clouds0.decal_Y_Ratio = 70f ;
		
		Parallax_Model clouds1 = new Parallax_Model() ; 
		clouds1.regionName = "cloudsSigne" ;
		clouds1.sizeRatio = 0.5f ;
		clouds1.regionPosition = 0 ; 
		clouds1.speedXAtRest = 0.5f ;
		clouds1.parallaxScalingSpeedX = .01f ; 
		clouds1.parallaxScalingSpeedY = .01f ; 
		clouds1.decal_Y_Ratio = 70f ;
		
		Parallax_Model rocks = new Parallax_Model() ; 
		rocks.regionName = "rocks" ; 
		rocks.sizeRatio = 0.5f ;
		rocks.regionPosition = 0 ; 
		rocks.parallaxScalingSpeedX = .017f ; 
		rocks.parallaxScalingSpeedY = .06f ; 
		rocks.decal_Y_Ratio = 40f ; 
		
		Parallax_Model ground0 = new Parallax_Model() ; 
		ground0.regionName = "ground_close" ; 
		ground0.sizeRatio = 0.27f ;
		ground0.regionPosition = 0 ; 
		ground0.parallaxScalingSpeedX = .02f ; 
		ground0.parallaxScalingSpeedY = .006f ; 
		ground0.decal_Y_Ratio = 38f ; 
		
		Parallax_Model ground1 = new Parallax_Model() ; 
		ground1.regionName = "ground_far" ; 
		ground1.sizeRatio = 0.32f ;
		ground1.regionPosition = 0 ; 
		ground1.parallaxScalingSpeedX = .03f ; 
		ground1.parallaxScalingSpeedY = .006f ; 
		ground1.decal_Y_Ratio = 35f ; 
		
		Parallax_Model ground2 = new Parallax_Model() ; 
		ground2.regionName = "ground_close" ; 
		ground2.sizeRatio = 0.40f ;
		ground2.regionPosition = 0 ; 
		ground2.parallaxScalingSpeedX = .025f ; 
		ground2.parallaxScalingSpeedY = .06f ; 
		ground2.decal_Y_Ratio = 30f ; 

		Parallax_Model ground3 = new Parallax_Model() ; 
		ground3.regionName = "ground_open" ; 
		ground3.sizeRatio = 0.45f ;
		ground3.regionPosition = 0 ; 
		ground3.parallaxScalingSpeedX = .03f ; 
		ground3.parallaxScalingSpeedY = .06f ; 
		ground3.decal_Y_Ratio = 25f ; 
		
		Parallax_Model ground4 = new Parallax_Model() ; 
		ground4.regionName = "ground_close" ; 
		ground4.sizeRatio = 0.6f ;
		ground4.regionPosition = 0 ; 
		ground4.parallaxScalingSpeedX = .040f ; 
		ground4.parallaxScalingSpeedY = .06f ; 
		ground4.decal_Y_Ratio = 20f ; 
		
		Parallax_Model ground5 = new Parallax_Model() ; 
		ground5.regionName = "ground_far" ; 
		ground5.sizeRatio = 0.6f ;
		ground5.regionPosition = 0 ; 
		ground5.parallaxScalingSpeedX = .05f ; 
		ground5.parallaxScalingSpeedY = .006f ; 
		ground5.decal_Y_Ratio = 20f ; 
		
		Parallax_Model water = new Parallax_Model() ; 
		water.regionName = "waterDark" ; 
		water.sizeRatio = 0.7f ;
		water.regionPosition = 0 ; 
		water.parallaxScalingSpeedX = .1f ; 
		water.parallaxScalingSpeedY = .006f ; 
		water.decal_Y_Ratio = 08f ; 
		water.speedXAtRest = -1f ;
		
		
		returningList.add(cloudsTop0) ; 
		returningList.add(cloudsTop1) ; 
		returningList.add(cloudsTop2) ; 
		
		returningList.add(clouds0) ; 
		returningList.add(clouds1) ; 
		returningList.add(rocks) ;
		returningList.add(ground0) ; 
		returningList.add(ground1) ; 
		returningList.add(ground2) ; 
		returningList.add(ground3) ; 
		returningList.add(ground4) ; 
		returningList.add(ground5) ; 
		returningList.add(water) ;
	
		return returningList ; 
	}
	
	public static ArrayList<Parallax_Model> buildSecondPages() 
	{
		ArrayList<Parallax_Model> returningList = new ArrayList<Parallax_Model>() ; 
		
		Parallax_Model water1 = new Parallax_Model() ; 
		water1.regionName = "waterDark" ; 
		water1.sizeRatio = 0.7f ;
		water1.regionPosition = 0 ; 
		water1.parallaxScalingSpeedX = .044f ; 
		water1.parallaxScalingSpeedY = .006f ; 
		water1.decal_X_Ratio = 20f ; 
		water1.decal_Y_Ratio = 10f ; 
		water1.speedXAtRest = -1.4f ;
		
		Parallax_Model water2 = new Parallax_Model() ; 
		water2.regionName = "waterDark" ; 
		water2.sizeRatio = 0.7f ;
		water2.regionPosition = 0 ; 
		water2.parallaxScalingSpeedX = .048f ; 
		water2.parallaxScalingSpeedY = .006f ; 
		water2.decal_X_Ratio = 30f ; 
		water2.decal_Y_Ratio = 00f ; 
		water2.speedXAtRest = -1f ;
		
		
		returningList.add(water1) ;
		returningList.add(water2) ;
		
		return returningList ; 
	}
}