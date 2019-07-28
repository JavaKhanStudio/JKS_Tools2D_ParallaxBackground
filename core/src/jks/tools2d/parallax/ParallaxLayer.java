package jks.tools2d.parallax;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.fasterxml.jackson.annotation.JsonIgnoreType;

import jks.tools2d.parallax.heart.Gvars_Parallax;
import jks.tools2d.parallax.pages.Parallax_Model;


@JsonIgnoreType
public class ParallaxLayer implements Cloneable
{
//	ParallaxLayer
	private TextureRegion texRegion;
	//private TextureRegion changingRegion ; 

	private float decalPosition_X = 0, decalPercent_Y = 0 ;
	private float region_Width,region_Height;
	private float sizeRatio =  1;
	
	protected float parallaxSpeedRatioX;
	protected float parallaxSpeedRatioY;
	
	protected float currentDistanceX = 0, currentDistanceY = 0 ; 

	protected float padX ; 
	protected float padXFactor ; 
	
	protected float padY ; 
	protected float padYFactor ; 

	protected float speedXAtRest ; 
	protected float speedYAtRest ; 
	
	protected boolean repeat_tileX = true ;
	protected boolean repeat_tileY = false ;
	
	protected boolean flipX = false ;
	protected boolean flipY = false ; 
	
	protected float currentX ; 
	protected float currentY ; 
	
	protected boolean isMirror ; 


	public ParallaxLayer(TextureRegion texRegion, boolean isWidth, float worldDimension, float parallaxScrollRatioX, float parallaxScrollRatioY, float sizeRatio)
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
		
		setParallaxSpeedRatioX(parallaxScrollRatioX);
		setParallaxSpeedRatioY(parallaxScrollRatioY);
	}
	
	public void setUpEverything(Parallax_Model model)
	{
		setFlipX(model.flipX);
		setFlipY(model.flipY);
		setDecalPercentX(model.decal_X_Ratio);
		setDecalPercentY(model.decal_Y_Ratio);
		setSizeRatio(model.sizeRatio);
		setSpeedAtRest(model.speedXAtRest);
		setParallaxSpeedRatioX(model.parallaxScalingSpeedX) ; 
		setParallaxSpeedRatioY( model.parallaxScalingSpeedY) ; 
		setPadX(model.padX);
		setPadXFactor(model.padXFactor);
		setPadY(model.padY);
		setPadYFactor(model.padYFactor);
	}
	
	public void resetPosition() 
	{
		currentDistanceX = 0 ; 
		currentDistanceY = 0 ;
		this.currentDistanceX = this.decalPosition_X * Gvars_Parallax.getWidthPercent(); 
		this.currentDistanceY = this.decalPercent_Y * Gvars_Parallax.getHeightPercent() ; 
		
	}

	public void draw(Batch batch, float x, float y) 
	{
		batch.draw
		(
			texRegion, 
			x + (flipX ? getRegionWidth() : 0), 
			y + (flipY ? getRegionHeight() : 0), 
			getRegionWidth() * (flipX ? -1 : 1), 
			getRegionHeight() * (flipY ? -1 : 1)
		);
	}
	
	public void draw(Batch batch, float x, float y, boolean onX) 
	{
		if(onX)
		{
			batch.draw
			(
				texRegion, 
				x + (flipX ? getRegionWidth() : 0), 
				y + (!flipY ? getRegionHeight() : 0), 
				getRegionWidth() * (flipX ? -1 : 1), 
				getRegionHeight() * (!flipY ? -1 : 1)
			);
		}
		else
		{
			batch.draw
			(
				texRegion, 
				x + (!flipX ? getRegionWidth() : 0), 
				y + (flipY ? getRegionHeight() : 0), 
				getRegionWidth() * (!flipX ? -1 : 1), 
				getRegionHeight() * (flipY ? -1 : 1)
			);
		}
		
	}
	
	public ParallaxLayer clone()
	{
		ParallaxLayer o = null;
		
		try 
		{o = (ParallaxLayer) super.clone();} 
		catch(CloneNotSupportedException cnse) 
		{cnse.printStackTrace(System.err);}
	
		return o;
	}
	
	public void act(float delta, float speedX, float speedY, boolean onX, boolean onY) 
	{
//		if(changingRegion != null)
//		{
//			texRegion = changingRegion ; 
//			changingRegion = null ; 
//		}
		
		currentDistanceY += delta * -(speedYAtRest + speedY) * parallaxSpeedRatioY ; 
		currentDistanceX += delta * -(speedXAtRest + speedX) * parallaxSpeedRatioX ; 
		
		if(Math.abs(currentDistanceX) >= getTotalWidth() && onX) 
			currentDistanceX -= getTotalWidth() * (currentDistanceX > 0 ? 1 : -1);
		
		if(Math.abs(currentDistanceY) >= getTotalHeight() && onY) 
			currentDistanceY -= getTotalHeight() * (currentDistanceY > 0 ? 1 : -1);
	}

	public float getWidth() 
	{return getRegionWidth();}

	public float getHeight() 
	{return getRegionHeight();}
	
	public float getTotalWidth() 
	{return getRegionWidth() + padX;}

	public float getTotalHeight() 
	{return getRegionHeight() + padY;}

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
	
	
	public boolean isRepeat_tileX() 
	{return repeat_tileX;}

	public void setRepeat_tileX(boolean repeat_tileX) 
	{this.repeat_tileX = repeat_tileX;}

	public boolean isRepeat_tileY() 
	{return repeat_tileY;}

	public void setRepeat_tileY(boolean repeat_tileY) 
	{this.repeat_tileY = repeat_tileY;}
	
	public float getSpeedAtRest() 
	{return speedXAtRest;}

	public void setSpeedAtRest(float speed) 
	{this.speedXAtRest = speed;}
	
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

	public void setFlipX(boolean flipX)
	{this.flipX = flipX;}
	
	public boolean isFlipY()
	{return flipY;}

	public void setFlipY(boolean flipY)
	{this.flipY = flipY;}

	
	public void setTexRegion(TextureRegion texRegion) 
	{this.texRegion = texRegion;}
	
	public float getPadX() 
	{return padX;}


	public void setPadX(float padX) 
	{this.padX = padX;}


	public float getPadXFactor() 
	{return padXFactor;}


	public void setPadXFactor(float padXFactor) 
	{this.padXFactor = padXFactor;}


	public float getPadY() 
	{return padY;}


	public void setPadY(float padY) 
	{this.padY = padY;}


	public float getPadYFactor() 
	{return padYFactor;}


	public void setPadYFactor(float padYFactor) 
	{this.padYFactor = padYFactor;}
	
	public float getParallaxSpeedRatioX() 
	{return parallaxSpeedRatioX;}

	public void setParallaxSpeedRatioX(float parallaxSpeedRatioX) 
	{this.parallaxSpeedRatioX = parallaxSpeedRatioX;}

	public float getParallaxSpeedRatioY()
	{return parallaxSpeedRatioY;}

	public void setParallaxSpeedRatioY(float parallaxSpeedRatioY) 
	{this.parallaxSpeedRatioY = parallaxSpeedRatioY;}
	
	public boolean isMirror() 
	{return isMirror;}

	public void setMirror(boolean isMirror) 
	{this.isMirror = isMirror;}

}