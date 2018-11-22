package jks.tools2d.parallax.heart;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import jks.tools2d.parallax.ParallaxPage;
import jks.tools2d.parallax.TextureRegionParallaxLayer;
import jks.tools2d.parallax.side.SolarAstre;
import jks.tools2d.parallax.side.SquareBackground;

public class Parrallax_Heart 
{
	public static OrthographicCamera worldCamera;
	public static OrthographicCamera staticCamera;
	public static SpriteBatch batch;
	public static ParallaxPage parallaxMainPage;
	public static ParallaxPage parallaxSecondePage;
	public static ShapeRenderer shapeRender ; 
	public static SquareBackground square ; 

	public static ArrayList<ParralaxPageModel> nextParallax = new ArrayList<ParralaxPageModel>(); 

	public static float worldWidth; 
	public static float worldHeight ;
	
	static ParralaxPageModel currentPage ; 
	public static float transfertTime = 10 ; 
	
	public static SolarAstre astres ; 
	public static boolean keepOn ;
	public static boolean useTimeOfDay ; 
	
	public static ArrayList<ParralaxPageModel> parallax_Sequence_ShowOrder ;
	static HashMap<ParralaxPageModel,List<TextureRegionParallaxLayer>> parralx_Sequence_Index ;
	
	public static void init(
			float worldWidth,
			float worldHeight) 
	{
		Parrallax_Heart.worldCamera = new OrthographicCamera() ;
		Parrallax_Heart.staticCamera = new OrthographicCamera() ;
		Parrallax_Heart.worldWidth = worldWidth ;
		Parrallax_Heart.worldHeight = worldHeight ;
		
		Parrallax_Heart.batch = new SpriteBatch();

		shapeRender = new ShapeRenderer() ;
		parallaxMainPage = new ParallaxPage(); 
		parallax_Sequence_ShowOrder = new ArrayList<ParralaxPageModel>() ;
	
	}
	
	public static void init(
			OrthographicCamera worldCamera,
			OrthographicCamera staticCamera,
			SpriteBatch batch,
			float worldWidth,
			float worldHeight) 
	{
		Parrallax_Heart.worldCamera = worldCamera ;
		Parrallax_Heart.staticCamera = staticCamera ;
		Parrallax_Heart.worldWidth = worldWidth ;
		Parrallax_Heart.worldHeight = worldHeight ;
		
		Parrallax_Heart.batch = batch;

		shapeRender = new ShapeRenderer() ;
		parallaxMainPage = new ParallaxPage(); 
		parallax_Sequence_ShowOrder = new ArrayList<ParralaxPageModel>() ;
	}
	
	public static void init(
			OrthographicCamera worldCamera,
			OrthographicCamera staticCamera,
			SpriteBatch batch,
			ParralaxPageModel pageModel,
			float worldWidth,
			float worldHeight
			)
	{
		init(worldCamera,staticCamera,batch,worldWidth,worldHeight) ;
		Parralax_Utils_Page.setPage(pageModel) ; 
	}
	
	
	public static void init(
			OrthographicCamera WorldCamera,
			OrthographicCamera StaticCamera,
			SpriteBatch batch,
			ArrayList<ParralaxPageModel> Parallax_Sequence_ShowOrder,
			HashMap<ParralaxPageModel,List<TextureRegionParallaxLayer>> Parralx_Sequence_Index,
			float WorldWidth,
			float WorldHeight)
	
	{
		init(WorldCamera,StaticCamera,batch,WorldWidth,WorldHeight) ;
		
		parallax_Sequence_ShowOrder = Parallax_Sequence_ShowOrder ;
		parralx_Sequence_Index = Parralx_Sequence_Index ;
	}
	
	
	public static void setPage(ParralaxPageModel model)
	{Parralax_Utils_Page.setPage(model);}
	
	public static void transfertIntoPage(ParralaxPageModel model, float intoXSec)
	{
		
	}
	
	public static void render(float delta)
	{
		
	}

	public static void renderMainPage(float delta)
	{
		Parralax_Utils_Background.drawBackgroundColor(delta) ; 
		
		batch.begin() ;
		Parralax_Utils_Astre.drawAstre(delta); 
		Parralax_Utils_Page.drawPage(delta) ;
		batch.end();
	}
	
	public static void renderSecondePage(float delta)
	{
		batch.begin() ;
		Parralax_Utils_Page.drawSecondePage(delta) ; 
		batch.end();
	}
	
}

