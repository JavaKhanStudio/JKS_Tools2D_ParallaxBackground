package jks.tools2d.parallax.editor.texturepacking;

import com.badlogic.gdx.tools.texturepacker.TexturePacker;
import com.badlogic.gdx.tools.texturepacker.TexturePacker.Settings;

import jks.tools2d.parallax.ParallaxLayer;

public class Utils_TexturePacking 
{
	
	
	Utils_TexturePacking()
	{
		
	}
	
	
	public static void packCurrentTexture()
	{
		Settings settings = new Settings() ; 
		settings.maxWidth = 32768 ;
		settings.maxHeight = 32768 ;
		TexturePacker packer = new TexturePacker(settings);
//		packer.addImage(file.file());

	
//		packer.pack(filePath, "testingPackage");
		
	}
}
