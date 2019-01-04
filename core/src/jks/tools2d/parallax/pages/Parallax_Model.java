package jks.tools2d.parallax.pages;

import com.esotericsoftware.kryo.DefaultSerializer;

@DefaultSerializer(Parallax_Serializer.class)
public class Parallax_Model 
{
	public String region_Name ; 
	public String region_Position ; 
	public float ratioX ; 
	public float ratioY ; 
	
	public float speed ; 
	public float pad_Bottom_Ratio ; 
}


/*
TextureRegionParallaxLayer mountainsLayerA = new TextureRegionParallaxLayer(
mountainsRegion.get(0), 
worldWidth, 
new Vector2(.006f,.006f), 
true);
		mountainsLayerA.setPadBottom(worldHeight*.12f);

*/