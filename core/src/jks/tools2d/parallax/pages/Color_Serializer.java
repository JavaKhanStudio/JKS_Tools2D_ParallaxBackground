package jks.tools2d.parallax.pages;

import com.badlogic.gdx.graphics.Color;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

/** Writes colors as four floats in A, B, G, R order (historical .plax layout, keep it). */
public class Color_Serializer extends Serializer<Color>
{
	@Override
	public void write(Kryo kryo, Output output, Color color)
	{
		output.writeFloat(color.a);
		output.writeFloat(color.b);
		output.writeFloat(color.g);
		output.writeFloat(color.r);
	}

	@Override
	public Color read(Kryo kryo, Input input, Class<? extends Color> type)
	{
		Color color = new Color();
		color.a = input.readFloat();
		color.b = input.readFloat();
		color.g = input.readFloat();
		color.r = input.readFloat();
		return color;
	}
}
