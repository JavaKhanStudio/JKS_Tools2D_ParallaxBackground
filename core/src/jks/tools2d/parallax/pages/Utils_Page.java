package jks.tools2d.parallax.pages;

import java.io.InputStream;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.StreamUtils;
import com.esotericsoftware.kryo.io.Input;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.heart.GVars_Serialization;

public final class Utils_Page
{
	private Utils_Page()
	{}

	/** Reads a .plax file from the internal (assets) storage. */
	public static WholePage_Model loadPage(String internalPath)
	{return loadPage(Gdx.files.internal(internalPath));}

	public static WholePage_Model loadPage(FileHandle file)
	{return loadPage(file.read());}

	/** Reads a .plax file from a stream, closing it. */
	public static WholePage_Model loadPage(InputStream stream)
	{
		GVars_Serialization.init();
		Input input = new Input(stream);
		try
		{return GVars_Serialization.kryo.readObject(input, WholePage_Model.class);}
		finally
		{StreamUtils.closeQuietly(input);}
	}

	public static Parallax_Model buildFromPage(ParallaxLayer page, String regionName, int region_Position)
	{
		Parallax_Model model = new Parallax_Model();
		model.regionName = regionName;
		model.regionPosition = region_Position;
		model.flipX = page.isFlipX();
		model.flipY = page.isFlipY();
		model.parallaxScalingSpeedX = page.getParallaxSpeedRatioX();
		model.parallaxScalingSpeedY = page.getParallaxSpeedRatioY();
		model.speedXAtRest = page.getSpeedAtRest();
		model.sizeRatio = page.getSizeRatio();
		model.decal_X_Ratio = page.getDecalPercentX();
		model.decal_Y_Ratio = page.getDecalPercentY();
		model.padX = page.getPadX();
		model.padXFactor = page.getPadXFactor();
		model.padY = page.getPadY();
		model.padYFactor = page.getPadYFactor();
		model.mirror = page.isMirror();
		return model;
	}
}
