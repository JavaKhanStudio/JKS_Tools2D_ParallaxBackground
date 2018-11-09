package jks.tools2d.parallax.test;

import java.util.ArrayList;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;

import jks.tools2d.parallax.ParallaxBackground;
import jks.tools2d.parallax.side.SolarAstre;
import jks.tools2d.parallax.side.SquareBackground;

public class GVars_Heart 
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

	public static ArrayList<Enum_TimeOfDay> nextParallax = new ArrayList<Enum_TimeOfDay>(); 
	public static ArrayList<Color> nextColor = new ArrayList<Color>() ; 
	
	public static float worldWidth; 
	public static float worldHeight ;
	static Enum_TimeOfDay currentTime ; 
	public static float transfertTime = 10 ; 
	
	public static SolarAstre astres ; 
	public static boolean keepOn ;
	public static boolean useTimeOfDay ; 
	
	//Show Sequence
	public static final ArrayList<Enum_TimeOfDay> showSequence_Parallax = new ArrayList<Enum_TimeOfDay>()
	{{
		this.add(Enum_TimeOfDay.SUNRISE);
		this.add(Enum_TimeOfDay.DAY);
		this.add(Enum_TimeOfDay.STATU_QUO);
		this.add(Enum_TimeOfDay.STATU_QUO);
		this.add(Enum_TimeOfDay.SUNSET);
		this.add(Enum_TimeOfDay.NIGHT);
		this.add(Enum_TimeOfDay.STATU_QUO);
		this.add(Enum_TimeOfDay.STATU_QUO);
	}} ; 
	
		
	//Test Sequence
//	public static final ArrayList<Enum_TimeOfDay> showSequence_Parallax = new ArrayList<Enum_TimeOfDay>()
//	{{
//		this.add(Enum_TimeOfDay.SUNRISE);
//		this.add(Enum_TimeOfDay.DAY);
//		this.add(Enum_TimeOfDay.SUNSET);
//		this.add(Enum_TimeOfDay.NIGHT);
//	}} ; 
	
//	public static final ArrayList<Enum_TimeOfDay> showSequence_Parallax = new ArrayList<Enum_TimeOfDay>()
//	{{
//		this.add(Enum_TimeOfDay.DAY);
//		this.add(Enum_TimeOfDay.DAY);
//		this.add(Enum_TimeOfDay.STATU_QUO);
//	}} ; 
	
//	public static final ArrayList<Enum_TimeOfDay> showSequence_Parallax = new ArrayList<Enum_TimeOfDay>()
//	{{
//		this.add(Enum_TimeOfDay.DAY);
//		this.add(Enum_TimeOfDay.STATU_QUO);
//		this.add(Enum_TimeOfDay.STATU_QUO);
//		this.add(Enum_TimeOfDay.STATU_QUO);
//		this.add(Enum_TimeOfDay.STATU_QUO);
//	}} ; 
	
	public static void render()
	{
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		
		float delta = Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f);
		
		if(autoMoveScreen)
			worldCamera.position.add(screenMovementSpeed, 0, 0);
		
		
		drawBackgroundColor(delta) ; 
		
		batch.begin();
		
		drawAstre(delta);
		computeNextBackground() ; 
		drawBackgroud(delta) ;
		drawRoad(delta) ; 
		
		
		batch.end();
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
			else if(showSequence_Parallax.size() > 0)
			{
				nextParallax.addAll(showSequence_Parallax) ;
				computeNextBackground() ;
			}
		}
	}
	
	private static void computeNextBackground_parralax(ParallaxBackground background)
	{
		currentTime = nextParallax.get(0) ;
		
		if(useTimeOfDay) 
		{
			startAstre(currentTime) ;
			parallaxBackground.addLayersTransfert(Index_DayLayer.getDayMap(currentTime),transfertTime * currentTime.timeToTransfertInto);
			nextParallax.remove(0) ; 
		}
		else 
		{
			parallaxBackground.addLayersTransfert(parallaxBackground.layers,transfertTime * currentTime.timeToTransfertInto);
		}
			
		square.transfertInto(currentTime.top, currentTime.bottom, transfertTime * currentTime.timeToTransfertInto);
		
		if(parallaxBackground_Road != null)
		{
			parallaxBackground_Road.addColorTransfert(currentTime.colorSurronding, transfertTime * currentTime.timeToTransfertInto);
			parallaxBackground_Road.set_newLayer_Color(currentTime.colorSurronding);
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
	
	
	public static void startAstre(Enum_TimeOfDay current)
	{
		switch(current)
		{
			case SUNRISE :
			 	astres.startAstre(GVars_Heart.transfertTime * 3.7f, true); break ; 
			case NIGHT :
				astres.startAstre(GVars_Heart.transfertTime * 3.0f, false); break ;
			default:
				break; 
		}	
	}
	
	
}
