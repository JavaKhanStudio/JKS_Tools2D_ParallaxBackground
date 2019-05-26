package jks.tools2d.parallax.editor.vue.edition.data;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import jks.tools2d.parallax.pages.Parallax_Model;

public class ParallaxLayerBaseValues_Serializer extends Serializer<ParallaxDefaultValues>
{
	@Override
	public void write(Kryo kryo, Output output, ParallaxDefaultValues object) 
	{
		kryo.writeObject(output, object.defaultModel);
		kryo.writeObject(output, object.incrementValue);
		output.writeBoolean(object.addInFront);
		output.writeBoolean(object.increment);
		output.writeBoolean(object.autoGoToSelected);
		output.writeBoolean(object.alternateFlipX);
		output.writeBoolean(object.alternateFlipY);
	}

	@Override
	public ParallaxDefaultValues read(Kryo kryo, Input input, Class<? extends ParallaxDefaultValues> type) 
	{
		ParallaxDefaultValues returning = new ParallaxDefaultValues();
	
		returning.defaultModel = kryo.readObject(input,Parallax_Model.class) ; 
		returning.incrementValue = kryo.readObject(input,Parallax_Model.class) ; 
		
		returning.addInFront = input.readBoolean() ;
		returning.increment = input.readBoolean() ;
		returning.autoGoToSelected = input.readBoolean() ;
		returning.alternateFlipX = input.readBoolean() ;
		returning.alternateFlipY = input.readBoolean() ;
		
		return returning;
	}
}