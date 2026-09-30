package jks.tools2d.parallax.editor.vue.edition.data;

import java.util.ArrayList;

public class Project_Data
{
	
	public WholePage_Editor saving ; 
	public ArrayList<Outside_Source> outsideInfos ; 
	public ParallaxDefaultValues defaults ;
	/** Export writes the atlas Nearest,Nearest without mipmaps, and the preview draws it so: sharp pixel art (d14). */
	public boolean pixelArt ;
	/** Export also writes the atlas as ETC2 (name.etc2.atlas, .zktx pages) for OpenGL ES 3 GPUs (d13). */
	public boolean etc2 ;
	
	public void prepareForSaving(WholePage_Editor model)
	{
		saving = model ; 
	}
}