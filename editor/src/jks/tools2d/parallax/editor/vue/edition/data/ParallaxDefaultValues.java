package jks.tools2d.parallax.editor.vue.edition.data;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.pages.Parallax_Model;

/**
 * Settings given to a newly added layer. When {@link #increment} is on, they move after each addition so a stack of
 * layers gets a depth effect: speeds are multiplied by the increment, the other values are added.
 */
public class ParallaxDefaultValues
{
	public Parallax_Model defaultModel;
	public Parallax_Model incrementValue;
	public boolean addInFront;
	public boolean increment;
	public boolean autoGoToSelected;
	public boolean alternateFlipX;
	public boolean alternateFlipY;

	public ParallaxDefaultValues()
	{
		increment = true;
		autoGoToSelected = false;
		setIncrementFrontToBack();
	}

	/** New layers go behind the others, each one slower and lower than the previous. */
	public void setIncrementBackToFront()
	{
		addInFront = false;

		defaultModel = new Parallax_Model();
		incrementValue = new Parallax_Model();

		defaultModel.sizeRatio = 1;
		defaultModel.parallaxScalingSpeedX = 0.1f;
		defaultModel.parallaxScalingSpeedY = 0.06f;
		defaultModel.decal_Y_Ratio = 0;

		incrementValue.parallaxScalingSpeedX = 0.75f;
		incrementValue.parallaxScalingSpeedY = 0.75f;
		incrementValue.decal_X_Ratio = 10;
		incrementValue.decal_Y_Ratio = 10;
		incrementValue.sizeRatio = 0;
	}

	/** New layers go in front of the others, each one faster and lower than the previous. */
	public void setIncrementFrontToBack()
	{
		addInFront = true;

		defaultModel = new Parallax_Model();
		incrementValue = new Parallax_Model();

		defaultModel.sizeRatio = 1;
		defaultModel.parallaxScalingSpeedX = 0.01f;
		defaultModel.parallaxScalingSpeedY = 0.01f;
		defaultModel.decal_Y_Ratio = 85;

		incrementValue.parallaxScalingSpeedX = 1.25f;
		incrementValue.parallaxScalingSpeedY = 1.25f;
		incrementValue.decal_X_Ratio = 10;
		incrementValue.decal_Y_Ratio = -8;
		incrementValue.sizeRatio = 0;
	}

	public void doIncrement(boolean positive)
	{
		if (alternateFlipX)
			defaultModel.flipX = !defaultModel.flipX;
		if (alternateFlipY)
			defaultModel.flipY = !defaultModel.flipY;

		float sign = positive ? 1 : -1;

		defaultModel.parallaxScalingSpeedX *= positive ? incrementValue.parallaxScalingSpeedX : 1 / incrementValue.parallaxScalingSpeedX;
		defaultModel.parallaxScalingSpeedY *= positive ? incrementValue.parallaxScalingSpeedY : 1 / incrementValue.parallaxScalingSpeedY;

		defaultModel.speedXAtRest += sign * incrementValue.speedXAtRest;
		defaultModel.sizeRatio = Math.max(0.01f, defaultModel.sizeRatio + sign * incrementValue.sizeRatio);

		defaultModel.decal_X_Ratio += sign * incrementValue.decal_X_Ratio;
		defaultModel.decal_Y_Ratio += sign * incrementValue.decal_Y_Ratio;

		defaultModel.padX += sign * incrementValue.padX;
		defaultModel.padXFactor += sign * incrementValue.padXFactor;
	}

	public boolean isAlternateFlipX()
	{return alternateFlipX;}

	public void setAlternateFlipX(boolean alternateFlipX)
	{this.alternateFlipX = alternateFlipX;}

	public boolean isAlternateFlipY()
	{return alternateFlipY;}

	public void setAlternateFlipY(boolean alternateFlipY)
	{this.alternateFlipY = alternateFlipY;}

	/** Makes the given layer the starting point of the next additions. */
	public void copyValue(ParallaxLayer newValue)
	{
		defaultModel.flipX = newValue.isFlipX();
		defaultModel.flipY = newValue.isFlipY();

		defaultModel.parallaxScalingSpeedX = newValue.getParallaxSpeedRatioX();
		defaultModel.parallaxScalingSpeedY = newValue.getParallaxSpeedRatioY();
		defaultModel.speedXAtRest = newValue.getSpeedAtRest();
		defaultModel.sizeRatio = newValue.getSizeRatio();
		defaultModel.decal_X_Ratio = newValue.getDecalPercentX();
		defaultModel.decal_Y_Ratio = newValue.getDecalPercentY();
		defaultModel.padX = newValue.getPadX();
		defaultModel.padXFactor = newValue.getPadXFactor();
		defaultModel.padY = newValue.getPadY();
		defaultModel.padYFactor = newValue.getPadYFactor();
	}
}
