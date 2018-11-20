package jks.tools2d.parallax.heart;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;

import jks.tools2d.parallax.ParallaxBackground;
import jks.tools2d.parallax.TextureRegionParallaxLayer;
import jks.tools2d.parallax.side.Enum_AstreType;
import jks.tools2d.parallax.side.SolarAstre;
import jks.tools2d.parallax.side.SquareBackground;

public class Parrallax_Heart 
{
	public static OrthographicCamera worldCamera;
	public static OrthographicCamera staticCamera;
	public static final float screenMovementSpeed = 1.5f;
	public static boolean autoMoveScreen = true ; 
	public static SpriteBatch batch;
	public static ParallaxBackground parallaxBackground;
	public static ParallaxBackground parallaxBackground_Road;
	public static ShapeRenderer shapeRender ; 
	public static SquareBackground square ; 

	public static ArrayList<ParralaxPageModel> nextParallax = new ArrayList<ParralaxPageModel>(); 
	public static ArrayList<Color> nextColor = new ArrayList<Color>() ; 
	
	public static float worldWidth; 
	public static float worldHeight ;
	
	static ParralaxPageModel currentPage ; 
	public static float transfertTime = 10 ; 
	
	public static SolarAstre astres ; 
	public static boolean keepOn ;
	public static boolean useTimeOfDay ; 
	
	public static ArrayList<ParralaxPageModel> parallax_Sequence_ShowOrder ;
	private static HashMap<ParralaxPageModel,List<TextureRegionParallaxLayer>> parralx_Sequence_Index ;
	
	public static void init(
			OrthographicCamera WorldCamera,
			OrthographicCamera StaticCamera,
			float WorldWidth,
			float WorldHeight) 
	{
		worldCamera = WorldCamera ;
		staticCamera = StaticCamera ;
		worldWidth = WorldWidth ;
		worldHeight = WorldHeight ;
		
		batch = new SpriteBatch() ;

		shapeRender = new ShapeRenderer() ;
	}
	
	public static void init(
			OrthographicCamera WorldCamera,
			OrthographicCamera StaticCamera,
			ParralaxPageModel pageModel,
			float WorldWidth,
			float WorldHeight
			)
	{
		init(WorldCamera,StaticCamera,WorldWidth,WorldHeight) ;
		currentPage = pageModel ; 
		parallaxBackground = new ParallaxBackground();
		parallaxBackground.addLayers(currentPage.howToDraw(worldWidth,worldHeight));
		parallaxBackground.setDrawingHeight(6.78f);
		
		parallax_Sequence_ShowOrder = new ArrayList<ParralaxPageModel>() ;
	}
	
	
	public static void init(
			OrthographicCamera WorldCamera,
			OrthographicCamera StaticCamera,
			ArrayList<ParralaxPageModel> Parallax_Sequence_ShowOrder,
			HashMap<ParralaxPageModel,List<TextureRegionParallaxLayer>> Parralx_Sequence_Index,
			float WorldWidth,
			float WorldHeight)
	
	{
		init(WorldCamera,StaticCamera,WorldWidth,WorldHeight) ;
		
		parallax_Sequence_ShowOrder = Parallax_Sequence_ShowOrder ;
		parralx_Sequence_Index = Parralx_Sequence_Index ;
	}
	
	
	

	private static void drawBackgroundColor(float delta)
	{
		if(square != null)
		{
			shapeRender.begin(ShapeType.Filled);
			square.act(delta);
			square.draw(shapeRender);
			shapeRender.end();
		}
	}
	
	private static void computeNextBackground()
	{
		if(!parallaxBackground.isInTransfer())
		{
			if(nextParallax.size() > 0)
			{
				computeNextBackground_parralax(parallaxBackground) ; 
				computeNextBackground_Color(parallaxBackground) ;
			}
			else if(parallax_Sequence_ShowOrder.size() > 0)
			{
				nextParallax.addAll(parallax_Sequence_ShowOrder) ;
				computeNextBackground() ;
			}
		}
	}
	
	private static void computeNextBackground_parralax(ParallaxBackground background)
	{
		currentPage = nextParallax.get(0) ;
		
		//startAstre(currentTime) ;
		parallaxBackground.addLayersTransfert(parralx_Sequence_Index.get(currentPage),transfertTime * currentPage.timeToTransfertInto);
		nextParallax.remove(0) ; 
		square.transfertInto(currentPage.top, currentPage.bottom, transfertTime * currentPage.timeToTransfertInto);
		
		if(parallaxBackground_Road != null)
		{
			parallaxBackground_Road.addColorTransfert(currentPage.colorSurronding, transfertTime * currentPage.timeToTransfertInto);
			parallaxBackground_Road.set_newLayer_Color(currentPage.colorSurronding);
		}
			
	}
	
	private static void computeNextBackground_Color(ParallaxBackground background)
	{
		if(nextColor.size() > 0)
		{
			parallaxBackground.set_newLayer_Color(nextColor.get(0));
			nextColor.remove(0) ; 
		}
	}
	
	private static void drawAstre(float delta)
	{
		if(astres != null)
		{
			batch.setProjectionMatrix(staticCamera.combined);
			astres.act(delta);
			astres.draw(batch);
		}
	}
	
	private static void drawBackgroud(float delta)
	{
		parallaxBackground.act(delta) ; 
		parallaxBackground.draw(worldCamera, batch);
	}
	
	private static void drawRoad(float delta)
	{
		if(parallaxBackground_Road != null)
		{
			parallaxBackground_Road.act(delta) ; 
			parallaxBackground_Road.draw(worldCamera, batch);
		}
	}
	
	
	public static void startAstre(Enum_AstreType current)
	{
		switch(current)
		{
			case SUN :
			 	astres.startAstre(transfertTime * 3.7f, true); break ; 
			case MOON :
				astres.startAstre(transfertTime * 3.0f, false); break ;
			default:
				break; 
		}	
	}
	
	public static void render(float delta)
	{
		drawBackgroundColor(delta) ; 
		
		batch.begin() ;
		drawAstre(delta);
		computeNextBackground() ; 
		drawBackgroud(delta) ;
		drawRoad(delta) ; 
		batch.end();
	}
	
}

