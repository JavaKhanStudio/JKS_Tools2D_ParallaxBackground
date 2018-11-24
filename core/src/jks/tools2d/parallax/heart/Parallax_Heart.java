package jks.tools2d.parallax.heart;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import jks.tools2d.parallax.ParallaxPage;
import jks.tools2d.parallax.TextureRegionParallaxLayer;
import jks.tools2d.parallax.Utils_Parralax;
import jks.tools2d.parallax.side.SolarAstre;
import jks.tools2d.parallax.side.SquareBackground;

public class Parallax_Heart 
{
	public static OrthographicCamera worldCamera;
	public static OrthographicCamera staticCamera;
	public static SpriteBatch batch;
	public static ParallaxPage parallaxMainPage;
	public static ParallaxPage parallaxSecondePage;
	
	// Background
	public static ShapeRenderer shapeRender ; 
	public static SquareBackground square ;
	//  1 = nothing * 0 = full screen
	public static float squarePercentage = 0.5f; 

//	public static ArrayList<ParallaxPageModel> nextParallax = new ArrayList<ParallaxPageModel>(); 

	public static float worldWidth; 
	public static float worldHeight ;
	
	static ParallaxPageModel currentPage ; 
	static ParallaxPageModel currentTransfertPage ; 
//	public static float transfertTime = 10 ; 
	
	public static SolarAstre astres ; 
	public static boolean keepOn ;
	public static boolean useTimeOfDay ; 
	
//	public static ArrayList<ParallaxPageModel> parallax_Sequence_ShowOrder ;
//	static HashMap<ParallaxPageModel,List<TextureRegionParallaxLayer>> parralx_Sequence_Index ;
	
	public static void init(
			float worldWidth) 
	{
		
		Parallax_Heart.worldWidth = worldWidth ;
		Parallax_Heart.worldHeight = Utils_Parralax.calculateOtherDimension(true, worldWidth, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		
		Parallax_Heart.worldCamera = new OrthographicCamera() ;
		Parallax_Heart.worldCamera.setToOrtho(false,worldWidth,worldHeight);
		Parallax_Heart.worldCamera.position.add(10000, 0, 0);
		
		Parallax_Heart.staticCamera = new OrthographicCamera() ;
		
		Parallax_Heart.batch = new SpriteBatch();

		shapeRender = new ShapeRenderer() ;
		parallaxMainPage = new ParallaxPage(); 
//		parallax_Sequence_ShowOrder = new ArrayList<ParallaxPageModel>() ;
	}
	
	public static void init(
			OrthographicCamera worldCamera,
			OrthographicCamera staticCamera,
			SpriteBatch batch,
			float worldWidth,
			float worldHeight) 
	{
		Parallax_Heart.worldCamera = worldCamera ;
		Parallax_Heart.staticCamera = staticCamera ;
		Parallax_Heart.worldWidth = worldWidth ;
		Parallax_Heart.worldHeight = worldHeight ;
		
		Parallax_Heart.batch = batch;

		shapeRender = new ShapeRenderer() ;
		parallaxMainPage = new ParallaxPage(); 
//		parallax_Sequence_ShowOrder = new ArrayList<ParallaxPageModel>() ;
	}
	
	public static void init(
			OrthographicCamera worldCamera,
			OrthographicCamera staticCamera,
			SpriteBatch batch,
			ParallaxPageModel pageModel,
			float worldWidth,
			float worldHeight
			)
	{
		init(worldCamera,staticCamera,batch,worldWidth,worldHeight) ;
		Parallax_Utils_Page.setPage(pageModel) ; 
	}
	
	public static void setPage(ParallaxPageModel model)
	{Parallax_Utils_Page.setPage(model);}
	
	public static void transfertIntoPage(ParallaxPageModel model, float intoXSec)
	{
		
	}
	
	public static void render(float delta)
	{
		
	}

	public static void renderMainPage(float delta)
	{
		Parallax_Utils_Background.drawBackgroundColor(delta) ; 
		
		batch.begin() ;
		Parallax_Utils_Astre.drawAstre(delta); 
		Parallax_Utils_Page.drawPage(delta) ;
		batch.end();
	}
	
	public static void renderSecondePage(float delta)
	{
		batch.begin() ;
		Parallax_Utils_Page.drawSecondePage(delta) ; 
		batch.end();
	}
	
}

