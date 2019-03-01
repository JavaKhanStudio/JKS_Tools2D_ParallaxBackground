package jks.tools2d.parallax.heart;


import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;

import jks.tools2d.parallax.ParallaxPage;
import jks.tools2d.parallax.Utils_Parralax;
import jks.tools2d.parallax.pages.WholePage_Model;
import jks.tools2d.parallax.side.SolarAstre;
import jks.tools2d.parallax.side.SquareBackground;

public class Parallax_Heart 
{
	public static OrthographicCamera worldCamera;
	public static Camera staticCamera;
	public static SpriteBatch batch;
	public static ParallaxPage parallaxMainPage;
	public static ParallaxPage parallaxSecondePage;
	public static AssetManager manager ;
	
	// Background
	public static ShapeRenderer shapeRender ; 
	public static SquareBackground topSquare ;
	public static SquareBackground bottomSquare ;
	
	//  1 = nothing * 0 = full screen
	public static float topSquarePercent = 0.5f; 
	public static float bottomSquareSize = Gdx.graphics.getHeight()/5 ;

	public static float worldWidth; 
	public static float worldHeight ;
	
	static WholePage_Model currentPage ; 
	static WholePage_Model currentTransfertPage ; 
	 
	
	public static SolarAstre astres ; 
	public static boolean keepOn ;
	public static boolean useTimeOfDay ; 
	
	public static boolean debug ; 
	
	public static void init(float worldWidth,AssetManager manager) 
	{	
		Parallax_Heart.worldWidth = worldWidth ;
		Parallax_Heart.worldHeight = Utils_Parralax.calculateOtherDimension(true, worldWidth, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		
		Parallax_Heart.worldCamera = new OrthographicCamera() ;
		Parallax_Heart.worldCamera.setToOrtho(false,worldWidth,worldHeight);
		Parallax_Heart.worldCamera.position.add(10000, 0, 0);
		
		Parallax_Heart.staticCamera = new PerspectiveCamera() ;
		
		Parallax_Heart.batch = new SpriteBatch();

		shapeRender = new ShapeRenderer() ;
		parallaxMainPage = new ParallaxPage();
		Parallax_Heart.manager = manager ; 
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
	}
	
	public static void init(
			OrthographicCamera worldCamera,
			OrthographicCamera staticCamera,
			SpriteBatch batch,
			WholePage_Model pageModel,
			float worldWidth,
			float worldHeight
			)
	{
		init(worldCamera,staticCamera,batch,worldWidth,worldHeight) ;
		Parallax_Utils_Page.setPage(pageModel) ; 
	}
	
	public static void setPage(WholePage_Model model)
	{Parallax_Utils_Page.setPage(model);}
	
	public static void transfertIntoPage(WholePage_Model model, float intoXSec)
	{
		Parallax_Utils_Page.transfertIntoPage(model, intoXSec);
	}
	
	public static void act(float delta)
	{
		if(astres != null)
			astres.act(delta);
		
		if(topSquare != null)
			topSquare.act(delta);
		
		if(bottomSquare != null)
			bottomSquare.act(delta);
		
		if(parallaxMainPage != null)
			parallaxMainPage.act(delta);
		
		if(parallaxSecondePage != null)
			parallaxSecondePage.act(delta);		
	}

	public static void renderMainPage()
	{
		shapeRender.setTransformMatrix(staticCamera.combined);
		shapeRender.begin(ShapeType.Filled);
		Parallax_Utils_Background.drawBackground_TopColor() ; 
		Parallax_Utils_Background.drawBackground_BottomColor() ; 
		shapeRender.end();
		
		batch.begin() ;
		Parallax_Utils_Astre.drawAstre(); 
		Parallax_Utils_Page.drawPage(parallaxMainPage) ;
		batch.end();
	}

	public static void renderSecondePage()
	{
		batch.begin() ;
		Parallax_Utils_Page.drawPage(parallaxSecondePage) ; 
		batch.end();
	}	
}