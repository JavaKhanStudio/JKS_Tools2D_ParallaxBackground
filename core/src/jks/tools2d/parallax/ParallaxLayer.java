package jks.tools2d.parallax;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;

import jks.tools2d.parallax.heart.Gvars_Parallax;


public class ParallaxLayer
{
//	ParallaxLayer
	private TextureRegion texRegion;
	private float padPosition_X = 0, padPosition_Y = 0 ;
	private float region_Width,region_Height;
	private float sizeRatio =  1;
	
	protected Vector2 parallaxRatio;
	protected float decalX = 0, decalY = 0 ; 

	protected float speed ; 
	
	protected boolean repeat_tileX = true ;
	protected boolean repeat_tileY = false ;
	
	protected boolean flipX = false ; 
	
	protected float currentX ; 
	protected float currentY ; 

	
	public ParallaxLayer(TextureRegion texRegion, float oneDimen, Vector2 parallaxScrollRatio, boolean isWidth)
	{
		this(texRegion, oneDimen, parallaxScrollRatio, 1f, isWidth) ; 
	}
	
	public ParallaxLayer(TextureRegion texRegion, float oneDimen, Vector2 parallaxScrollRatio, float sizeRatio, boolean isWidth)
	{
		this.texRegion = texRegion ;
		this.sizeRatio = sizeRatio ;
		
		if(isWidth)
		{
			setRegionWidth(oneDimen);
	    	setRegionHeight(Utils_Parralax.calculateOtherDimension(true, oneDimen, this.texRegion));	
		}
		else
		{
	    	setRegionHeight(oneDimen);
	    	setRegionWidth(Utils_Parralax.calculateOtherDimension(false, oneDimen, this.texRegion));
		}
		
		setParallaxRatio(parallaxScrollRatio);
	}
	

	public void draw(Batch batch, float x, float y) 
	{
		batch.draw(
				texRegion, 
				x, 
				y, 
				getRegionWidth(), 
				getRegionHeight()
		);
		
//		* (flipX ? - 1 : 1)
	}
	
	public void act(float delta) 
	{
		decalX += delta * speed ;
		
		if(Math.abs(decalX) >= getRegionWidth()) 
			decalX -= getRegionWidth();
	}

	public float getWidth() 
	{return getRegionWidth();}

	public float getHeight() 
	{return getRegionHeight();}
	
	public void setAllPad(float pad)
	{
		setPadPositionX(pad);
		setPadPositionY(pad);
	}

	public TextureRegion getTexRegion()
	{return texRegion;}

	public float getPadPositionX() 
	{return padPosition_X;}

	public void setPadPositionX(float padX) 
	{
		this.decalX = this.decalX + (padX - this.padPosition_X) * Gvars_Parallax.getWidthPercent(); 
		this.padPosition_X = padX;
	}
	
	public float getPadPositionY() 
	{return padPosition_Y;}

	public void setPadPositionY(float padY)
	{
		this.decalY = this.decalY + (padY - this.padPosition_Y) * Gvars_Parallax.getHeightPercent() ; 
		this.padPosition_Y = padY;
	}

	public float getRegionWidth() 
	{return region_Width * sizeRatio;}

	public float getRegionHeight() 
	{return region_Height * sizeRatio;}
	
	private void setRegionWidth(float width)
	{this.region_Width = width;}
	
	private void setRegionHeight(float height)
	{this.region_Height = height;}
	
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
	{return decalX ;}

	public void setDecalX(float decalX) 
	{this.decalX = decalX;}
	
	public float getDecalY()
	{return decalY;}

	public void setDecalY(float decalY)
	{this.decalY = decalY;}
	
	public float getSizeRatio()
	{return sizeRatio;}

	public void setSizeRatio(float sizeRatio)
	{this.sizeRatio = sizeRatio;}

	public boolean isFlipX()
	{return flipX;}

	public void setFlipX(boolean flipX)
	{this.flipX = flipX; texRegion.flip(true, false);}

}