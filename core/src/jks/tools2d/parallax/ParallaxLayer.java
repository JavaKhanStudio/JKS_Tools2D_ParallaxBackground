package jks.tools2d.parallax;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;

import jks.tools2d.parallax.heart.Gvars_Parallax;


public class ParallaxLayer
{
//	ParallaxLayer
	private TextureRegion texRegion;
	private float decalPosition_X = 0, decalPercent_Y = 0 ;
	private float region_Width,region_Height;
	private float sizeRatio =  1;
	
	protected Vector2 parallaxSpeedRatio;
	protected float currentDistanceX = 0, currentDistanceY = 0 ; 
	
	protected float padMin ; 
	protected float padFactor ; 

	protected float speedAtRest ; 
	
	protected boolean repeat_tileX = true ;
	protected boolean repeat_tileY = false ;
	
	protected boolean flipX = false ;
	protected boolean flipY = false ; 
	
	protected float currentX ; 
	protected float currentY ; 

	
	public ParallaxLayer(TextureRegion texRegion, float oneDimen, Vector2 parallaxScrollRatio, boolean isWidth)
	{
		this(texRegion, isWidth, oneDimen, parallaxScrollRatio, 1f) ; 
	}
	
	public ParallaxLayer(TextureRegion texRegion, boolean isWidth, float worldDimension, Vector2 parallaxScrollRatio, float sizeRatio)
	{
		this.texRegion = texRegion ;
		this.sizeRatio = sizeRatio ;
		
		if(isWidth)
		{
			setRegionWidth(worldDimension);
	    	setRegionHeight(Utils_Parralax.calculateOtherDimension(true, worldDimension, this.texRegion));	
		}
		else
		{
	    	setRegionHeight(worldDimension);
	    	setRegionWidth(Utils_Parralax.calculateOtherDimension(false, worldDimension, this.texRegion));
		}
		
		setParallaxSpeedRatio(parallaxScrollRatio);
	}
	

	public void draw(Batch batch, float x, float y) 
	{
		batch.draw
		(
			texRegion, 
			x, 
			y, 
			getRegionWidth(), 
			getRegionHeight()
		);
		
	}
	
	public void act(float delta) 
	{
		currentDistanceX += delta * speedAtRest ;
		
		if(Math.abs(currentDistanceX) >= getRegionWidth()) 
			currentDistanceX -= getRegionWidth();
	}

	public float getWidth() 
	{return getRegionWidth();}

	public float getHeight() 
	{return getRegionHeight();}
	
	public void setAllPad(float pad)
	{
		setDecalPercentX(pad);
		setDecalPercentY(pad);
	}

	public TextureRegion getTexRegion()
	{return texRegion;}

	public float getDecalPercentX() 
	{return decalPosition_X;}

	public void setDecalPercentX(float decalPercentX) 
	{
		this.currentDistanceX = this.currentDistanceX + (decalPercentX - this.decalPosition_X) * Gvars_Parallax.getWidthPercent(); 
		this.decalPosition_X = decalPercentX;
	}
	
	public float getDecalPercentY() 
	{return decalPercent_Y;}

	public void setDecalPercentY(float decalPercentY)
	{
		this.currentDistanceY = this.currentDistanceY + (decalPercentY - this.decalPercent_Y) * Gvars_Parallax.getHeightPercent() ; 
		this.decalPercent_Y = decalPercentY;
	}

	public float getRegionWidth() 
	{return region_Width * sizeRatio;}

	public float getRegionHeight() 
	{return region_Height * sizeRatio;}
	
	private void setRegionWidth(float width)
	{this.region_Width = width;}
	
	private void setRegionHeight(float height)
	{this.region_Height = height;}
	
	public Vector2 getParallaxSpeedRatio() 
	{return parallaxSpeedRatio;}

	public void setParallaxSpeedRatio(Vector2 parallaxRatio) 
	{
		if(this.parallaxSpeedRatio == null)
			this.parallaxSpeedRatio = new Vector2();
		this.parallaxSpeedRatio.set(parallaxRatio);
	}
	
	public boolean isRepeat_tileX() 
	{return repeat_tileX;}

	public void setRepeat_tileX(boolean repeat_tileX) 
	{this.repeat_tileX = repeat_tileX;}

	public boolean isRepeat_tileY() 
	{return repeat_tileY;}

	public void setRepeat_tileY(boolean repeat_tileY) 
	{this.repeat_tileY = repeat_tileY;}
	
	public float getSpeedAtRest() 
	{return speedAtRest;}

	public void setSpeedAtRest(float speed) 
	{this.speedAtRest = speed;}
	
	public float getCurrentDistanceX() 
	{return currentDistanceX ;}

	public void setCurrentDistanceX(float decalX) 
	{this.currentDistanceX = decalX;}
	
	public float getCurrentDistanceY()
	{return currentDistanceY;}

	public void setCurrentDistanceY(float decalY)
	{this.currentDistanceY = decalY;}
	
	public float getSizeRatio()
	{return sizeRatio;}

	public void setSizeRatio(float sizeRatio)
	{this.sizeRatio = sizeRatio;}

	public boolean isFlipX()
	{return flipX;}

	public void setFlipX(boolean flipX, boolean doTheFlip)
	{this.flipX = flipX; if(doTheFlip)texRegion.flip(true, false);}
	
	public boolean isFlipY()
	{return flipY;}

	public void setFlipY(boolean flipY, boolean doTheFlip)
	{this.flipY = flipY; if(doTheFlip)texRegion.flip(false, true);}

	public float getPadMin()
	{return padMin;}

	public void setPadMin(float padMin)
	{this.padMin = padMin;}

	public float getPadFactor()
	{return padFactor;}

	public void setPadFactor(float padFactor)
	{this.padFactor = padFactor;}

}