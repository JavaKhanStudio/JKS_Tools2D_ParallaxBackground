package jks.tools2d.parallax.editor.vue.edition.data;

import java.util.ArrayList;

public class Project_Data
{
	
	public WholePage_Editor saving ; 
	public ArrayList<Outside_Source> outsideInfos ; 
	public ParallaxDefaultValues defaults ;
	
	public void prepareForSaving(WholePage_Editor model)
	{
		saving = model ; 
	}
}