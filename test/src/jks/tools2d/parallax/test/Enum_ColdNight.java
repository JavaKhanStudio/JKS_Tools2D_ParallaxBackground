package jks.tools2d.parallax.test;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;

import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.pages.WholePage_Model;

public enum Enum_ColdNight 
{
	COLD_NIGHT("parralax/models/OneNight.atlas",Color.valueOf("0B4B6F"),Color.valueOf("0C77AD"), new Color(0.25f,0.07f,0.0f,1),Color.valueOf("030205")),
	COLD_WATER("parralax/models/OneNight.atlas"),
	;
	
	public WholePage_Model wholePage ; 
	
	Enum_ColdNight(String atlasPath, Color top, Color bottom,Color colorSurronding,Color bottomHalf)
	{
		wholePage = new WholePage_Model(atlasPath,top,bottom,colorSurronding,bottomHalf) ;
		wholePage.pageModel.pageList = ColdNightModel.buildPages() ; 
	}
	
	Enum_ColdNight(String atlasPath)
	{
		wholePage = new WholePage_Model(atlasPath) ;
		wholePage.pageModel.pageList = ColdNightModel.buildSecondPages() ; 
	}
	
	public void loadThemAll()
	{
		// loading all of the atlas
		for(Enum_ColdNight model : this.values())
		{
			if(wholePage.pageModel.atlasPath != null)
			{Parallax_Heart.manager.load(wholePage.pageModel.atlasPath, TextureAtlas.class);}
		}
		
		for(Enum_ColdNight model : this.values())
		{
			if(wholePage.pageModel.atlasPath != null)
			{wholePage.preload();}
		}
		
	}
}