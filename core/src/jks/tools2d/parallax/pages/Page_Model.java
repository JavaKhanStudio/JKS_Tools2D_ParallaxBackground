package jks.tools2d.parallax.pages;

import java.util.ArrayList;

import com.esotericsoftware.kryo.DefaultSerializer;

@DefaultSerializer(Page_Serializer.class)
public class Page_Model 
{
	public String atlasPath ; 
	public boolean outside ; 
	public ArrayList<Parallax_Model> pageList ; 
}


