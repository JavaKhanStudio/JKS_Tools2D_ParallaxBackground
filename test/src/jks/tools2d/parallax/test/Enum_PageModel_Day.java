package jks.tools2d.parallax.test;

import com.badlogic.gdx.graphics.Color;

import jks.tools2d.parallax.heart.ParallaxPageModel;

public enum Enum_PageModel_Day
{
	DAY("day/day.atlas",Color.valueOf("026BC0"),Color.valueOf("39D5FF"), new Color(1,1,1,1),Color.valueOf("085B4E")),
	SUNSET("day/sunset.atlas",Color.valueOf("530F1D"),Color.valueOf("FE9946"), new Color(0.25f,0.07f,0.0f,1),Color.valueOf("030205")),
	SUNRISE("day/sunrise.atlas",Color.valueOf("6D6A7D"),Color.valueOf("FDD069"), new Color(0.85f,0.5f,0.4f,1),Color.valueOf("2A2B2D")),
	RAIN("day/rain.atlas",Color.valueOf("39393C"),Color.valueOf("A19996"),new Color(1,1,1,1),Color.valueOf("091B18")),
	NIGHT("day/night.atlas",Color.valueOf("21354B"),Color.valueOf("BFE5FE"), new Color(0.1f,0.1f,0.1f,1),Color.valueOf("061421")),
	STATU_QUO(null,null,null,null,null),
	;

	public ParallaxPageModel page ;
	
	Enum_PageModel_Day(String atlasPath, Color top, Color bottom,Color colorSurronding,Color bottomHalf)
	{
		page = new DayPageModel(atlasPath,top,bottom,colorSurronding,bottomHalf) ;
	}

}
