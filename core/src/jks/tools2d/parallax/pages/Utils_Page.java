package jks.tools2d.parallax.pages;

import com.badlogic.gdx.Gdx;
import com.esotericsoftware.kryo.io.Input;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.heart.GVars_Serialization;

public class Utils_Page
{
	public static WholePage_Model loadPage(String internalPath)
	{
		GVars_Serialization.init();
		Input input = new Input(Gdx.files.internal(internalPath).read());
		WholePage_Model page = GVars_Serialization.kryo.readObject(input,WholePage_Model.class) ; 
		return page ;
	}
	
	
	public static Parallax_Model buildFromPage(ParallaxLayer page,String regionName, int region_Position)
	{
		Parallax_Model model = new Parallax_Model() ; 
		model.regionName = regionName ; 
		model.regionPosition = region_Position ; 
		model.flipX = (page.isFlipX());
		model.flipY = (page.isFlipY());
		model.parallaxScalingSpeedX = (page.getParallaxSpeedRatioX());
		model.parallaxScalingSpeedY = (page.getParallaxSpeedRatioY());
		model.speedXAtRest = (page.getSpeedAtRest());
		model.sizeRatio = (page.getSizeRatio());
		model.decal_X_Ratio = (page.getDecalPercentX());
		model.decal_Y_Ratio = (page.getDecalPercentY());
		model.padX = page.getPadX() ; 
		model.padXFactor = page.getPadXFactor() ; 
		model.padY = page.getPadY() ; 
		model.padYFactor = page.getPadYFactor() ; 
		
		return model ; 
	}
	
}
