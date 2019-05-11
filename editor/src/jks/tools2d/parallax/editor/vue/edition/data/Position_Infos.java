package jks.tools2d.parallax.editor.vue.edition.data;

import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

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
		url = parallax_Model.region_Name ; 
		position = parallax_Model.region_Position ; 
	}	

}




class Position_Infos_Serializer extends Serializer<Position_Infos>
{
	@Override
	public void write(Kryo kryo, Output output, Position_Infos object)
	{
		output.writeBoolean(object.fromAtlas);
		output.writeString(object.url);
		output.writeInt(object.position);
	}

	@Override
	public Position_Infos read(Kryo kryo, Input input, Class<? extends Position_Infos> type)
	{
		return new Position_Infos(input.readBoolean(),input.readString(),input.readInt()) ; 
	}
}