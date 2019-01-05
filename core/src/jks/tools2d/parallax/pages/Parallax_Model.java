package jks.tools2d.parallax.pages;

import com.esotericsoftware.kryo.DefaultSerializer;

@DefaultSerializer(Parallax_Serializer.class)
public class Parallax_Model 
{
	public String region_Name ; 
	public int region_Position ; 
	public float ratioX ; 
	public float ratioY ; 
	
	public float speed ; 
	public float pad_X_Ratio ; 
	public float pad_Y_Ratio ; 
}