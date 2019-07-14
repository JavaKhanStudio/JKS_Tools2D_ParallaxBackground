package jks.tools2d.parallax.editor.vue.edition.data;

import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.esotericsoftware.kryo.DefaultSerializer;

import jks.tools2d.parallax.pages.Parallax_Model;

@DefaultSerializer(Position_Infos_Serializer.class)
public class Position_Infos
{
	public boolean fromAtlas ;
	public String url ; 
	public int position ; 
	
	//JSON constructor 
	public Position_Infos()
	{}
	
	public Position_Infos(boolean fromAtlas, String url, int position)
	{
		this.fromAtlas = fromAtlas ;
		this.url = url ; 
		this.position = position ; 
	}
	
	public Position_Infos(AtlasRegion region,int position)
	{
		fromAtlas = true ; 
		url = region.name ; 
		this.position = position ;
	}

	public Position_Infos(boolean fromAtlas, String atlasPath, Parallax_Model parallax_Model)
	{
		this.fromAtlas = fromAtlas ; 
		url = parallax_Model.regionName ; 
		position = parallax_Model.regionPosition ; 
	}	

	public String getPageName()
	{
		// TODO rendre compatible texture animee
		if(!fromAtlas)
			return url.substring(url.lastIndexOf("\\") + 1, url.lastIndexOf(".")) ;
		else
			return url ; 
	}
	
}