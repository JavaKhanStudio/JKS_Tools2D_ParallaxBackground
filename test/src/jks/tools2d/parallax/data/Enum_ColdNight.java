package jks.tools2d.parallax.data;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;

import jks.tools2d.parallax.heart.Gvars_Parallax;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.pages.WholePage_Model;

public enum Enum_ColdNight 
{
	COLD_NIGHT("parralax/models/OneNight.atlas",Color.valueOf("0B4B6F"),Color.valueOf("0C77AD"), Color.valueOf("030205"),Color.valueOf("030205")),
	COLD_WATER("parralax/models/OneNight.atlas"),
	;
	
	//Color.valueOf("0B4B6F")
	public WholePage_Model wholePage ; 
	
	Enum_ColdNight(String atlasPath, Color topTop, Color topBottom,Color bottomTop,Color bottomBottom)
	{
		wholePage = new WholePage_Model(atlasPath,topTop,topBottom,bottomTop,bottomBottom) ;
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
			if(wholePage.pageModel.atlasName != null)
			{Gvars_Parallax.getManager().load(wholePage.pageModel.atlasName, TextureAtlas.class);}
		}
		
		for(Enum_ColdNight model : this.values())
		{
			if(wholePage.pageModel.atlasName != null)
			{wholePage.preload();}
		}
		
	}
}