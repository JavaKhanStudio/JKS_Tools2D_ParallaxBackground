package jks.tools2d.parallax.data;

import java.util.ArrayList;

import jks.tools2d.parallax.pages.Parallax_Model;

public class ColdNightModel
{

	public static ArrayList<Parallax_Model> buildPages() 
	{
		ArrayList<Parallax_Model> returningList = new ArrayList<Parallax_Model>() ; 
		
		Parallax_Model cloudsTop0 = new Parallax_Model() ; 
		cloudsTop0.region_Name = "clouds" ;
		cloudsTop0.sizeRatio = 0.5f ;
		cloudsTop0.region_Position = 0 ; 
		cloudsTop0.speed = 0.5f ;
		cloudsTop0.parallaxScalingSpeedX = .01f ; 
		cloudsTop0.parallaxScalingSpeedY = .01f ; 
		cloudsTop0.decal_Y_Ratio = 100f ;
		
		Parallax_Model cloudsTop1 = new Parallax_Model() ; 
		cloudsTop1.region_Name = "clouds" ;
		cloudsTop1.sizeRatio = 0.5f ;
		cloudsTop1.region_Position = 0 ; 
		cloudsTop1.speed = 0.5f ;
		cloudsTop1.parallaxScalingSpeedX = .01f ; 
		cloudsTop1.parallaxScalingSpeedY = .01f ; 
		cloudsTop1.decal_Y_Ratio = 90f ;
		
		Parallax_Model cloudsTop2 = new Parallax_Model() ; 
		cloudsTop2.region_Name = "clouds" ;
		cloudsTop2.sizeRatio = 0.5f ;
		cloudsTop2.region_Position = 0 ; 
		cloudsTop2.speed = 0.5f ;
		cloudsTop2.parallaxScalingSpeedX = .01f ; 
		cloudsTop2.parallaxScalingSpeedY = .01f ; 
		cloudsTop2.decal_Y_Ratio = 80f ;
		
		Parallax_Model clouds0 = new Parallax_Model() ; 
		clouds0.region_Name = "clouds" ;
		clouds0.sizeRatio = 0.5f ;
		clouds0.region_Position = 0 ; 
		clouds0.speed = 0.5f ;
		clouds0.parallaxScalingSpeedX = .01f ; 
		clouds0.parallaxScalingSpeedY = .01f ; 
		clouds0.decal_Y_Ratio = 70f ;
		
		Parallax_Model clouds1 = new Parallax_Model() ; 
		clouds1.region_Name = "clouds" ;
		clouds1.sizeRatio = 0.5f ;
		clouds1.region_Position = 1 ; 
		clouds1.speed = 0.5f ;
		clouds1.parallaxScalingSpeedX = .01f ; 
		clouds1.parallaxScalingSpeedY = .01f ; 
		clouds1.decal_Y_Ratio = 70f ;
		
		Parallax_Model rocks = new Parallax_Model() ; 
		rocks.region_Name = "rocks" ; 
		rocks.sizeRatio = 0.5f ;
		rocks.region_Position = 0 ; 
		rocks.parallaxScalingSpeedX = .005f ; 
		rocks.parallaxScalingSpeedY = .006f ; 
		rocks.decal_Y_Ratio = 40f ; 
		
		Parallax_Model ground0 = new Parallax_Model() ; 
		ground0.region_Name = "ground" ; 
		ground0.sizeRatio = 0.27f ;
		ground0.region_Position = 1 ; 
		ground0.parallaxScalingSpeedX = .008f ; 
		ground0.parallaxScalingSpeedY = .006f ; 
		ground0.decal_Y_Ratio = 38f ; 
		
		Parallax_Model ground1 = new Parallax_Model() ; 
		ground1.region_Name = "ground" ; 
		ground1.sizeRatio = 0.32f ;
		ground1.region_Position = 2 ; 
		ground1.parallaxScalingSpeedX = .0105f ; 
		ground1.parallaxScalingSpeedY = .006f ; 
		ground1.decal_Y_Ratio = 35f ; 
		
		Parallax_Model ground2 = new Parallax_Model() ; 
		ground2.region_Name = "ground" ; 
		ground2.sizeRatio = 0.40f ;
		ground2.region_Position = 1 ; 
		ground2.parallaxScalingSpeedX = .0135f ; 
		ground2.parallaxScalingSpeedY = .006f ; 
		ground2.decal_Y_Ratio = 30f ; 

		Parallax_Model ground3 = new Parallax_Model() ; 
		ground3.region_Name = "ground" ; 
		ground3.sizeRatio = 0.45f ;
		ground3.region_Position = 0 ; 
		ground3.parallaxScalingSpeedX = .015f ; 
		ground3.parallaxScalingSpeedY = .006f ; 
		ground3.decal_Y_Ratio = 25f ; 
		
		Parallax_Model ground4 = new Parallax_Model() ; 
		ground4.region_Name = "ground" ; 
		ground4.sizeRatio = 0.6f ;
		ground4.region_Position = 1 ; 
		ground4.parallaxScalingSpeedX = .020f ; 
		ground4.parallaxScalingSpeedY = .006f ; 
		ground4.decal_Y_Ratio = 20f ; 
		
		Parallax_Model ground5 = new Parallax_Model() ; 
		ground5.region_Name = "ground" ; 
		ground5.sizeRatio = 0.6f ;
		ground5.region_Position = 2 ; 
		ground5.parallaxScalingSpeedX = .024f ; 
		ground5.parallaxScalingSpeedY = .006f ; 
		ground5.decal_Y_Ratio = 20f ; 
		
		Parallax_Model water = new Parallax_Model() ; 
		water.region_Name = "waterDark" ; 
		water.sizeRatio = 0.7f ;
		water.region_Position = 0 ; 
		water.parallaxScalingSpeedX = .04f ; 
		water.parallaxScalingSpeedY = .006f ; 
		water.decal_Y_Ratio = 08f ; 
		water.speed = -1f ;
		
		
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
		water1.region_Name = "waterDark" ; 
		water1.sizeRatio = 0.7f ;
		water1.region_Position = 0 ; 
		water1.parallaxScalingSpeedX = .044f ; 
		water1.parallaxScalingSpeedY = .006f ; 
		water1.decal_X_Ratio = 20f ; 
		water1.decal_Y_Ratio = 10f ; 
		water1.speed = -1.4f ;
		
		Parallax_Model water2 = new Parallax_Model() ; 
		water2.region_Name = "waterDark" ; 
		water2.sizeRatio = 0.7f ;
		water2.region_Position = 0 ; 
		water2.parallaxScalingSpeedX = .048f ; 
		water2.parallaxScalingSpeedY = .006f ; 
		water2.decal_X_Ratio = 30f ; 
		water2.decal_Y_Ratio = 00f ; 
		water2.speed = -1f ;
		
		
		returningList.add(water1) ;
		returningList.add(water2) ;
		
		return returningList ; 
	}
}