package jks.tools2d.parallax.test;

import com.badlogic.gdx.graphics.Color;

import jks.tools2d.parallax.heart.ParralaxPageModel;

public enum Enum_PageModel_Day
{

	// 026BC0
	DAY("day/day.atlas",Color.valueOf("026BC0"),Color.valueOf("39D5FF"),0.6f, new Color(1,1,1,1)),
	SUNSET("day/sunset.atlas",Color.valueOf("530F1D"),Color.valueOf("FE9946"),0.6f, new Color(0.25f,0.07f,0.0f,1)),
	SUNRISE("day/sunrise.atlas",Color.valueOf("6D6A7D"),Color.valueOf("FDD069"),0.6f, new Color(0.85f,0.5f,0.4f,1)),
	RAIN("day/rain.atlas",Color.valueOf("39393C"),Color.valueOf("A19996"),0.7f, new Color(1,1,1,1)),
	NIGHT("day/night.atlas",Color.valueOf("21354B"),Color.valueOf("BFE5FE"),0.8f, new Color(0.1f,0.1f,0.1f,1)),
	STATU_QUO(null,null,null,1, null)
	;

	public ParralaxPageModel page ;
	
	Enum_PageModel_Day(String atlasPath, Color top, Color bottom, float duree,Color colorSurronding)
	{
		page = new DayPageModel(atlasPath,top,bottom,duree,colorSurronding) ;
	}

}
