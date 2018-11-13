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
import jks.tools2d.parallax.heart.ParralaxPageModel;
import jks.tools2d.parallax.heart.Parrallax_Heart;
import jks.tools2d.parallax.mains.Index_DayLayer;
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

	public static ArrayList<ParralaxPageModel> nextParallax = new ArrayList<ParralaxPageModel>(); 
	public static ArrayList<Color> nextColor = new ArrayList<Color>() ; 
	
	public static float worldWidth; 
	public static float worldHeight ;
	static ParralaxPageModel currentTime ; 
	public static float transfertTime = 10 ; 
	
	public static SolarAstre astres ; 
	public static boolean keepOn ;
	public static boolean useTimeOfDay ; 
	
	//Show Sequence
	public static final ArrayList<ParralaxPageModel> showSequence_Parallax = new ArrayList<ParralaxPageModel>()
	{{
		this.add(Enum_PageModel_Day.SUNRISE.page);
		this.add(Enum_PageModel_Day.DAY.page);
		this.add(Enum_PageModel_Day.STATU_QUO.page);
		this.add(Enum_PageModel_Day.STATU_QUO.page);
		this.add(Enum_PageModel_Day.SUNSET.page);
		this.add(Enum_PageModel_Day.NIGHT.page);
		this.add(Enum_PageModel_Day.STATU_QUO.page);
		this.add(Enum_PageModel_Day.STATU_QUO.page);
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
		
		Parrallax_Heart.render(delta) ; 
		
	}
	
}
