package jks.tools2d.parallax.mains;

import java.util.HashMap;
import java.util.List;

import jks.tools2d.parallax.TextureRegionParallaxLayer;
import jks.tools2d.parallax.heart.ParralaxPageModel;
import jks.tools2d.parallax.test.Enum_PageModel_Day;

public class Index_DayLayer 
{
	private static HashMap<ParralaxPageModel,List<TextureRegionParallaxLayer>> map ;
	
	
	public static void init (float worldWidth, float worldHeight)
	{		
		map = new HashMap<ParralaxPageModel, List<TextureRegionParallaxLayer>>() ;
		
		for(Enum_PageModel_Day enumValue : Enum_PageModel_Day.values())
		{
			map.put(enumValue.page, enumValue.createLayers_day(worldWidth, worldHeight)) ;	
		}
				
	}
	
	public static List<TextureRegionParallaxLayer> getDayMap(ParralaxPageModel key) 
	{return map.get(key) ;}	
}
