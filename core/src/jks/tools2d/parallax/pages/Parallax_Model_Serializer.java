package jks.tools2d.parallax.pages;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class Parallax_Model_Serializer extends Serializer<Parallax_Model>
{

	@Override
	public void write(Kryo kryo, Output output, Parallax_Model object) 
	{
		output.writeString(object.regionName);
		output.writeInt(object.regionPosition);
		output.writeBoolean(object.flipX);
		
		output.writeFloat(object.parallaxScalingSpeedX);
		output.writeFloat(object.parallaxScalingSpeedY);
		
		output.writeFloat(object.speedXAtRest);
		output.writeFloat(object.sizeRatio);
		output.writeFloat(object.decal_X_Ratio);
		output.writeFloat(object.decal_Y_Ratio);
		
		output.writeFloat(object.padX);
		output.writeFloat(object.padXFactor);
		
		output.writeFloat(object.padY);
		output.writeFloat(object.padYFactor);
	}

	@Override
	public Parallax_Model read(Kryo kryo, Input input, Class<? extends Parallax_Model> type) 
	{
		Parallax_Model returning = new Parallax_Model();
		returning.regionName = input.readString() ; 
		returning.regionPosition = input.readInt() ;
		returning.flipX = input.readBoolean() ;
		
		returning.parallaxScalingSpeedX = input.readFloat() ;
		returning.parallaxScalingSpeedY = input.readFloat() ;
		
		returning.speedXAtRest = input.readFloat() ;
		returning.sizeRatio = input.readFloat() ;
		returning.decal_X_Ratio = input.readFloat() ;
		returning.decal_Y_Ratio = input.readFloat() ;
		
		returning.padX = input.readFloat() ;
		returning.padXFactor = input.readFloat() ;
		
		returning.padY = input.readFloat() ;
		returning.padYFactor = input.readFloat() ;
		
		return returning;
	}

}