package jks.tools2d.parallax.editor.vue.edition.data;

import com.esotericsoftware.kryo.DefaultSerializer;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.pages.Parallax_Model;

//@DefaultSerializer(ParallaxLayerBaseValues_Serializer.class)
public class ParallaxDefaultValues
{
	
	public Parallax_Model defaultModel;
	public Parallax_Model incrementValue ; 
	public boolean addInFront ; 
	public boolean increment ;
	public boolean autoGoToSelected ;
	public boolean alternateFlipX ; 
	public boolean alternateFlipY ; 
	
	
	public ParallaxDefaultValues()
	{
		increment = true ; 
		autoGoToSelected = false ; 
		setIncrementFrontToBack() ;
	}
	
	public void setIncrementBackToFront()
	{
		addInFront = false ;
		
		defaultModel = new Parallax_Model() ;
		defaultModel.sizeRatio = 1 ; 
		defaultModel.parallaxScalingSpeedX = 0.1f ;
		defaultModel.parallaxScalingSpeedY = 0.06f ; 
		defaultModel.decal_Y_Ratio = 0 ;
		
		incrementValue = new Parallax_Model() ;
		incrementValue.decal_X_Ratio = 10 ; 
		incrementValue.decal_Y_Ratio = 10 ;
		incrementValue.parallaxScalingSpeedX = -0.02f ; 
		incrementValue.parallaxScalingSpeedY = -0.00025f ;
		incrementValue.sizeRatio = -0.1f ; 
	}
	
	public void setIncrementFrontToBack()
	{
		addInFront = true ; 
		
		defaultModel = new Parallax_Model() ;
		defaultModel.sizeRatio = 1 ; 
		defaultModel.parallaxScalingSpeedX = 0.01f ;
		defaultModel.parallaxScalingSpeedY = 0.01f ; 
		defaultModel.decal_Y_Ratio = 85 ;
		
		incrementValue = new Parallax_Model() ;
		incrementValue.decal_X_Ratio = 10 ; 
		incrementValue.decal_Y_Ratio = -8 ;
		incrementValue.parallaxScalingSpeedX = 0.003f ; 
		incrementValue.parallaxScalingSpeedY = 0.00025f ;
		incrementValue.sizeRatio = 0 ; 
	}
	
	
	public void doIncrement(boolean positive)
	{
		if(alternateFlipX)
			defaultModel.flipX = !defaultModel.flipX ;			
		if(alternateFlipY)
			defaultModel.flipX = !incrementValue.flipY ;  
 
		defaultModel.parallaxScalingSpeedX += (positive ? 1 : -1)* incrementValue.parallaxScalingSpeedX ; 
		defaultModel.parallaxScalingSpeedY += (positive ? 1 : -1)*incrementValue.parallaxScalingSpeedY ; 
		
		defaultModel.speed += (positive ? 1 : -1)*incrementValue.speed ; 
		defaultModel.sizeRatio += (positive ? 1 : -1)*incrementValue.sizeRatio ; 
		if(defaultModel.sizeRatio <= 0)
			defaultModel.sizeRatio = 0.01f ; 
		
		defaultModel.decal_X_Ratio += (positive ? 1 : -1)*incrementValue.decal_X_Ratio ; 
		defaultModel.decal_Y_Ratio += (positive ? 1 : -1)*incrementValue.decal_Y_Ratio ; 
		
		defaultModel.padX += (positive ? 1 : -1) * incrementValue.padX ; 
		defaultModel.padXFactor += (positive ? 1 : -1) * incrementValue.padXFactor ;
	}

	public boolean isAlternateFlipX()
	{
		return alternateFlipX;
	}

	public void setAlternateFlipX(boolean alternateFlipX)
	{
		this.alternateFlipX = alternateFlipX;
	}

	public boolean isAlternateFlipY()
	{
		return alternateFlipY;
	}

	public void setAlternateFlipY(boolean alternateFlipY)
	{
		this.alternateFlipY = alternateFlipY;
	} 
	
	public void copyValue(ParallaxLayer newValue)
	{
//		/*
		alternateFlipX = newValue.isFlipX(); 
		alternateFlipY = newValue.isFlipY(); 
		
		defaultModel.parallaxScalingSpeedX = newValue.getParallaxSpeedRatioX() ; 
		defaultModel.parallaxScalingSpeedY = newValue.getParallaxSpeedRatioY() ; 
		defaultModel.speed = newValue.getSpeedAtRest() ; 
		defaultModel.sizeRatio = newValue.getSizeRatio() ; 
		defaultModel.decal_X_Ratio = newValue.getDecalPercentX() ; 
		defaultModel.decal_Y_Ratio = newValue.getDecalPercentY() ; 
		defaultModel.padX = newValue.getPadX() ; 
		defaultModel.padXFactor = newValue.getPadXFactor() ;
		defaultModel.padY = newValue.getPadY() ; 
		defaultModel.padYFactor = newValue.getPadYFactor() ; 
//		*/
	}
}