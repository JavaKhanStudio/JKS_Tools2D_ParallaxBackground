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

	public Position_Infos(String atlasPath, Parallax_Model parallax_Model)
	{
		fromAtlas = true ; 
		url = parallax_Model.regionName ; 
		position = parallax_Model.regionPosition ; 
	}	

}


