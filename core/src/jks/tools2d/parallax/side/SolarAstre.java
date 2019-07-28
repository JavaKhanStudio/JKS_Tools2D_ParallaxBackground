package jks.tools2d.parallax.side;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import jks.tools2d.parallax.heart.Parallax_Heart;

public class SolarAstre 
{

	TextureRegion sun_image ;
	TextureRegion moon_image ; 
	
	float soleil_TempsParcour ; 
	float lune_TempsParcour ; 
	
	float elipse_height_Min ; 
	
	float elipse_height ; 
	float b ; 
	
	float elipse_width ; 
	float a ; 
	float aRoot ; 
	float decalX ;  
	
	
	float sun_speedX ; 
	float sun_currentX ; 
	
	float moon_speedX ; 
	float moon_currentX ; 
	
	float timeByCycle ; 
	
	float sun_size ;
	float moon_size ;
	
	boolean sun_running = false ; 
	boolean moon_running = false ; 
	
	public SolarAstre(TextureRegion sunPath, TextureRegion moonPath)
	{
		sun_image = sunPath ; 
		moon_image = moonPath ; 
	}
	
	public SolarAstre()
	{
		
//		Parallax_Heart.manager.load("astre/divers.atlas", TextureAtlas.class);
//		Parallax_Heart.manager.finishLoadingAsset("astre/divers.atlas");
		
//		TextureAtlas atlas  = Parallax_Heart.manager.get("astre/divers.atlas", TextureAtlas.class) ; 
//		sun_image = atlas.findRegion("Sun") ; 
//		moon_image = atlas.findRegion("Moon") ; 
		
		moon_size = Gdx.graphics.getWidth() * 0.04f ; 
		sun_size = Gdx.graphics.getWidth() * 0.125f ;
		
		elipse_width = Gdx.graphics.getWidth() * 1.1f ;
		a = elipse_width/2 ; 
		aRoot = a * a ; 
		
		elipse_height = Gdx.graphics.getHeight() * 0.6f ; 
		b = elipse_height/2 ;
		
		elipse_height_Min = Gdx.graphics.getHeight()/2 ;	
	}
	
	public void startAstre(float inXSec,boolean isDay)
	{
		if(isDay)
		{
			sun_speedX = elipse_width/inXSec ; 
			sun_currentX = Gdx.graphics.getWidth() * -0.1f ;
			sun_running = true ;
		}	
		else
		{
			moon_speedX = elipse_width/inXSec ; 
			moon_currentX = Gdx.graphics.getWidth() * -0.1f ;
			moon_running = true ;
		}
	}
	
	
	public void act(float delta)
	{
		if(sun_running)
			sun_currentX += sun_speedX * delta ;				
		
		if(moon_running)
			moon_currentX += moon_speedX * delta ;
	}
	
	public float getY(float currentX)
	{
		float relativeX = currentX - a;
		return (float) (b * Math.sqrt(1 - (relativeX * relativeX)/aRoot)) + elipse_height_Min ;
	}
	
	public void draw(Batch batch)
	{
		if(sun_running)
			batch.draw(sun_image, sun_currentX - (sun_size/2), getY(sun_currentX), sun_size,sun_size);
		if(moon_running)
			batch.draw(moon_image, moon_currentX - (moon_size/2),  getY(moon_currentX), moon_size,moon_size);
	}
}
