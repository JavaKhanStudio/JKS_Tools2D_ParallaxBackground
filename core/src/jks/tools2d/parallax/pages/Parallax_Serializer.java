package jks.tools2d.parallax.pages;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class Parallax_Serializer extends Serializer<Parallax_Model>
{

	@Override
	public void write(Kryo kryo, Output output, Parallax_Model object) 
	{
		output.writeString(object.region_Name);
		output.writeString(object.region_Position);
		
		output.writeFloat(object.ratioX);
		output.writeFloat(object.ratioY);
		
		output.writeFloat(object.speed);
		output.writeFloat(object.pad_Bottom_Ratio);
	}

	@Override
	public Parallax_Model read(Kryo kryo, Input input, Class<? extends Parallax_Model> type) 
	{
		Parallax_Model returning = new Parallax_Model();
		returning.region_Name = input.readString() ; 
		returning.region_Position = input.readString() ;
		
		returning.ratioX = input.readFloat() ;
		returning.ratioY = input.readFloat() ;
		
		returning.speed = input.readFloat() ;
		returning.pad_Bottom_Ratio = input.readFloat() ;
		return returning;
	}

}
