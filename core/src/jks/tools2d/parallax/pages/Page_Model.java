package jks.tools2d.parallax.pages;

import java.util.ArrayList;

import com.esotericsoftware.kryo.DefaultSerializer;

@DefaultSerializer(Page_Serializer.class)
public class Page_Model 
{
	public String atlasPath ; 
	public boolean outside ; 
	public ArrayList<Parallax_Model> pageList ; 
	
	public Page_Model()
	{
		atlasPath = "" ; 
		outside = false ; 
		pageList = new ArrayList<Parallax_Model>() ; 
	}
	
	public Page_Model(String atlasPath,boolean outside, ArrayList<Parallax_Model> pageList)
	{
		this.atlasPath = atlasPath ; 
		this.outside = outside ; 
		this.pageList = pageList ; 
	}
}


