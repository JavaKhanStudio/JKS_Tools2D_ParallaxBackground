package jks.tools2d.parallax.editor.vue.edition.data;

import java.util.ArrayList;

public class Project_Data
{
	
	public WholePage_Editor saving ; 
	public ArrayList<Outside_Source> outsideInfos ; 
	public ParallaxDefaultValues defaults ;
	/** Export writes the atlas Nearest,Nearest without mipmaps, and the preview draws it so: sharp pixel art (d14). */
	public boolean pixelArt ;
	
	public void prepareForSaving(WholePage_Editor model)
	{
		saving = model ; 
	}
}