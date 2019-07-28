package jks.tools2d.parallax.editor.vue.edition.data;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class Position_Infos_Serializer extends Serializer<Position_Infos>
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
