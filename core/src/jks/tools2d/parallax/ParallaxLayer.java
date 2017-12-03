package jks.tools2d.parallax;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector2;

/**
 * 
 * This is an abstract class that encapsulates the logic of a Parallaxly Scrolling layer that ParallaxBackground renders. 
 * It stores the ratio at which the layer has to scroll relatively. You can extend this class and implement its internal modal and rendering logic. This has an advantage over using just TextureRegions as layers of ParallaxBackground. 
 * Also see {@link TextureRegionParallaxLayer} as it is the direct extension of this class and ready to use if you want to render single texture region per layer repeatedly. 
 * <p>
 * As an example, consider you need to render 20 colored dots per camera viewport width over background. The balls need to scroll with the world camera, but with the parallax effect . Now you can extend this class to create your own balls layer with internal logic to maintain the balls(logically) and render them.
 * @author Rahul Verma
 *
 */
public abstract class ParallaxLayer 
{
	

	protected Vector2 parallaxRatio;
	protected float decalX = 0 ; 
	
	protected float speed ; 
	
	protected boolean repeat_tileX = true ;
	protected boolean repeat_tileY = false ;
	
	/**
	 * returns the width of this layer. This width basically represents segment width of this layer after which it either repeats itself while rendering or just ceases to render further, depending upon the horizontal TileMode (see {@link #setTileModeX(TileMode)})
	 * @return width of the layer
	 */
	public abstract float getWidth();

	/**
	 * returns the height of this layer. This height basically represents segment height of this layer after which it either repeats itself while rendering or just ceases to render further, depending upon the vertical TileMode (see {@link #setTileModeY(TileMode)})
	 * @return returns the height of this layer
	 */
	public abstract float getHeight();




    /**
     * get the Vector2 that contains the parallax scrolling ratio of this layer in x and y direction.
     * @return parallax scrolling ratio
     */
	public Vector2 getParallaxRatio() 
	{return parallaxRatio;}


	/**
     * set the parallax scrolling ratio of this layer in x and y direction.
     * @return parallax scrolling ratio
     */
	public void setParallaxRatio(Vector2 parallaxRatio) 
	{
		if(this.parallaxRatio == null)
			this.parallaxRatio = new Vector2();
		this.parallaxRatio.set(parallaxRatio);
	}
	
	/**
     * set the parallax scrolling ratio of this layer in x and y direction.
     * @param ratioX scrolling ratio in x direction
     * @param ratioY scrolling ratio in y direction
     */
	public void setParallaxRatio(float ratioX, float ratioY) 
	{
		if(this.parallaxRatio == null)
			this.parallaxRatio = new Vector2();
		this.parallaxRatio.set(ratioX,ratioY);
	}

	
	/**
	 * draw this layer at specified position. Make sure that when you implement or extend this method you draw this layer within bounds returned by {@link #getWidth()} and {@link #getHeight()}. 
	 * @param batch the batch used for rendering
	 * @param x the x position of the lower left corner where rendering should be done
	 * @param y the y position of the lower left corner where rendering should be done
	 */
	public abstract void draw(Batch batch,float x, float y);

	
	public abstract void act(float delta) ;
	
	public boolean isRepeat_tileX() 
	{return repeat_tileX;}

	public void setRepeat_tileX(boolean repeat_tileX) 
	{this.repeat_tileX = repeat_tileX;}

	public boolean isRepeat_tileY() 
	{return repeat_tileY;}

	public void setRepeat_tileY(boolean repeat_tileY) 
	{this.repeat_tileY = repeat_tileY;}

	public float getCurrentX() 
	{return decalX;}

	public void setCurrentX(float currentX) 
	{this.decalX = currentX;}
	
	public float getSpeed() 
	{return speed;}

	public void setSpeed(float speed) 
	{this.speed = speed;}
	
	public float getDecalX() 
	{return decalX;}

	public void setDecalX(float decalX) 
	{this.decalX = decalX;}
	
}
