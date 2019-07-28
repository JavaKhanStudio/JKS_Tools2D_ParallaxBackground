package jks.tools2d.parallax.pages;

import java.util.ArrayList;

import com.esotericsoftware.kryo.DefaultSerializer;

@DefaultSerializer(Page_Model_Serializer.class)
public class Page_Model 
{
	public String atlasName ; 
	public boolean outside ;  
	
	public ArrayList<Parallax_Model> pageList ; 
	
	public Page_Model()
	{
		atlasName = "" ; 
		outside = false ; 
		pageList = new ArrayList<Parallax_Model>() ; 
	}
	
	public Page_Model(String atlasPath,boolean outside, ArrayList<Parallax_Model> pageList)
	{
		this.atlasName = atlasPath ; 
		this.outside = outside ; 
		this.pageList = pageList ; 
	}

	public String getAtlasName() 
	{return atlasName;}

	public void setAtlasName(String atlasName) 
	{this.atlasName = atlasName;}

	public boolean isOutside() 
	{return outside;}

	public void setOutside(boolean outside) 
	{this.outside = outside;}

	public ArrayList<Parallax_Model> getPageList() 
	{return pageList;}

	public void setPageList(ArrayList<Parallax_Model> pageList) 
	{this.pageList = pageList;}
	
}
