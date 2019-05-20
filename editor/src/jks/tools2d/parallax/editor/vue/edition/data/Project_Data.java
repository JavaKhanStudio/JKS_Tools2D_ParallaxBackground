package jks.tools2d.parallax.editor.vue.edition.data;

import static jks.tools2d.parallax.editor.vue.edition.Vue_Edition.parallax_Heart;

import java.util.ArrayList;

import com.esotericsoftware.kryo.DefaultSerializer;

@DefaultSerializer(Project_Data_Serializer.class)
public class Project_Data
{
	public WholePage_Editor saving ; 
	public ArrayList<Position_Infos> outsideInfos ; 
	public ParallaxDefaultValues defaults ;
	
	public void prepareForSaving(WholePage_Editor model)
	{
		saving = model ; 
		outsideInfos = UtilsSaving.buildSavingOutsideValues(parallax_Heart.parallaxPage.layers) ; 
	}
}