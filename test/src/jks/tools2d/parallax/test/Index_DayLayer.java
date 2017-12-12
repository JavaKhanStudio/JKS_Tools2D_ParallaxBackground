package jks.tools2d.parallax.test;

import java.util.HashMap;
import java.util.List;

import jks.tools2d.parallax.TextureRegionParallaxLayer;

public class Index_DayLayer 
{

	
	private static HashMap<Enum_TimeOfDay,List<TextureRegionParallaxLayer>> map ;
	
	
	public static void init (float worldWidth, float worldHeight)
	{		
		map = new HashMap<Enum_TimeOfDay, List<TextureRegionParallaxLayer>>() ;
		
		for(Enum_TimeOfDay enumValue : Enum_TimeOfDay.values())
			map.put(enumValue, enumValue.createLayers_day(worldWidth, worldHeight)) ;		
	}
	
	public static List<TextureRegionParallaxLayer> getDayMap(Enum_TimeOfDay key) 
	{return map.get(key) ;}
	
}
