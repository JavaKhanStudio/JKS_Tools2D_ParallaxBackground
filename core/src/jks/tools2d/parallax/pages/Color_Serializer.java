package jks.tools2d.parallax.pages;

import com.badlogic.gdx.graphics.Color;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class Color_Serializer extends Serializer<Color>
{

	@Override
	public void write(Kryo kryo, Output output, Color object) 
	{
		output.writeFloat(object.a);
		output.writeFloat(object.b);
		output.writeFloat(object.g);
		output.writeFloat(object.r);	
	}

	@Override
	public Color read(Kryo kryo, Input input, Class<? extends Color> type) 
	{
		Color color = new Color();
		color.a = input.readFloat() ;
		color.b = input.readFloat() ;
		color.g = input.readFloat() ;
		color.r = input.readFloat() ;
		return color;
	}
	
}
