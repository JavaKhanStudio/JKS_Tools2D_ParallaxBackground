package jks.tools2d.parallax;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector2;


public abstract class ParallaxLayer 
{
	
	protected Vector2 parallaxRatio;
	protected float decalX = 0 ; 
	
	protected float speed ; 
	
	protected boolean repeat_tileX = true ;
	protected boolean repeat_tileY = false ;
	
	protected float currentX ; 
	protected float currentY ; 

	public abstract float getWidth();


	public abstract float getHeight();

	public Vector2 getParallaxRatio() 
	{return parallaxRatio;}

	public void setParallaxRatio(Vector2 parallaxRatio) 
	{
		if(this.parallaxRatio == null)
			this.parallaxRatio = new Vector2();
		this.parallaxRatio.set(parallaxRatio);
	}
	

	public void setParallaxRatio(float ratioX, float ratioY) 
	{
		if(this.parallaxRatio == null)
			this.parallaxRatio = new Vector2();
		this.parallaxRatio.set(ratioX,ratioY);
	}

	public abstract void draw(Batch batch,float x, float y);

	public void act(float delta)
	{
		if(getSpeed() != 0)
	    	currentX = -getWidth() * 1.25f ;
    	else
	    	currentX = 0 ;
	}
	
	public boolean isRepeat_tileX() 
	{return repeat_tileX;}

	public void setRepeat_tileX(boolean repeat_tileX) 
	{this.repeat_tileX = repeat_tileX;}

	public boolean isRepeat_tileY() 
	{return repeat_tileY;}

	public void setRepeat_tileY(boolean repeat_tileY) 
	{this.repeat_tileY = repeat_tileY;}
	
	public float getSpeed() 
	{return speed;}

	public void setSpeed(float speed) 
	{this.speed = speed;}
	
	public float getDecalX() 
	{return decalX;}

	public void setDecalX(float decalX) 
	{this.decalX = decalX;}
	
}