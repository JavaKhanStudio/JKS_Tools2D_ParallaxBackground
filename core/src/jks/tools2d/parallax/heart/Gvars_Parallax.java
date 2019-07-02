package jks.tools2d.parallax.heart;

import com.badlogic.gdx.assets.AssetManager;

public class Gvars_Parallax
{
	private static AssetManager manager ;
	private static float worldWidth = 40 ;
	private static float worldHeight = 40 ;
	
	public static AssetManager getManager() 
	{
		if(manager == null)
			buildManager() ; 
		
		return manager ; 
	}


	private static void buildManager()
	{
		manager = new AssetManager() ;
	}


	public static void setManager(AssetManager newManager)
	{manager = newManager ;}
	
	
	public static float getWidthPercent()
	{return worldWidth/100 ;}
	
	public static float getHeightPercent()
	{return worldHeight/100 ; }

	public static float getWorldWidth()
	{return worldWidth;}

	public static void setWorldWidth(float worldWidth)
	{Gvars_Parallax.worldWidth = worldWidth;}

	public static float getWorldHeight()
	{return worldHeight;}

	public static void setWorldHeight(float worldHeight)
	{Gvars_Parallax.worldHeight = worldHeight;}
}