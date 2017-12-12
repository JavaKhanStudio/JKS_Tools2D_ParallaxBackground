package jks.tools2d.parallax.test;

import java.util.ArrayList;

import com.badlogic.gdx.Gdx;
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
	public static final float screenMovementSpeed = 2.3f;
	public static boolean autoMoveScreen = true ; 
	public static SpriteBatch batch;
	public static ParallaxBackground parallaxBackground;
	public static ParallaxBackground parallaxBackground_Road;
	public static ShapeRenderer shapeRender ; 
	public static SquareBackground square ; 

	public static ArrayList<Enum_TimeOfDay> nextStep = new ArrayList<Enum_TimeOfDay>(); 
	public static float worldWidth; 
	public static float worldHeight ;
	static Enum_TimeOfDay currentTime ; 
	public static float transfertTime = 6 ; 
	
	public static SolarAstre astres ; 
	public static boolean keepOn ;
	
	//Show Sequence
	public static final ArrayList<Enum_TimeOfDay> showSequence = new ArrayList<Enum_TimeOfDay>()
	{{
		this.add(Enum_TimeOfDay.SUNRISE);
		this.add(Enum_TimeOfDay.DAY);
		this.add(Enum_TimeOfDay.STATU_QUO);
		this.add(Enum_TimeOfDay.SUNSET);
		this.add(Enum_TimeOfDay.NIGHT);
		this.add(Enum_TimeOfDay.STATU_QUO);
		this.add(Enum_TimeOfDay.STATU_QUO);
	}} ; 
	
	//Test Sequence
//	public static final ArrayList<Enum_TimeOfDay> showSequence = new ArrayList<Enum_TimeOfDay>()
//	{{
//		this.add(Enum_TimeOfDay.SUNRISE);
//		this.add(Enum_TimeOfDay.DAY);
//		this.add(Enum_TimeOfDay.SUNSET);
//		this.add(Enum_TimeOfDay.NIGHT);
//	}} ; 
	
//	public static final ArrayList<Enum_TimeOfDay> showSequence = new ArrayList<Enum_TimeOfDay>()
//	{{
//		this.add(Enum_TimeOfDay.DAY);
//		this.add(Enum_TimeOfDay.SUNSET);
//	}} ; 
	
//	public static final ArrayList<Enum_TimeOfDay> showSequence = new ArrayList<Enum_TimeOfDay>()
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
		
		if(square != null)
		{
			shapeRender.begin(ShapeType.Filled);
			square.act(delta);
			square.draw(shapeRender);
			shapeRender.end();
		}
		
		batch.begin();
		
		if(astres != null)
		{
			batch.setProjectionMatrix(staticCamera.combined);
			astres.act(delta);
			astres.draw(batch);
		}
		
		
		parallaxBackground.act(delta) ; 
		parallaxBackground.draw(worldCamera, batch);
		
		if(!parallaxBackground.isInTransfer())
		{
			if(nextStep.size() > 0)
			{
				currentTime = nextStep.get(0) ;
				startAstre(currentTime) ;
				
				parallaxBackground.addLayersTransfert(Index_DayLayer.getDayMap(currentTime),transfertTime * currentTime.timeToTransfertInto);
				nextStep.remove(0) ; 
				square.transfertInto(currentTime.top, currentTime.bottom, transfertTime * currentTime.timeToTransfertInto);
			}
			else
			{
				nextStep.addAll(showSequence) ;
			}
		}
		
		if(parallaxBackground_Road != null)
		{
			parallaxBackground_Road.act(delta) ; 
			parallaxBackground_Road.draw(worldCamera, batch);
		}
		
		
		batch.end();
	}
	
	
	public static void startAstre(Enum_TimeOfDay current)
	{
		switch(current)
		{
			case SUNRISE :
			 	astres.startAstre(GVars_Heart.transfertTime * 3.1f, true); break ; 
			case NIGHT :
				astres.startAstre(GVars_Heart.transfertTime * 0.8f, false); break ;
			default:
				break; 
		}	
	}
	
	
}
