package jks.tools2d.parallax.editor.vue.edition;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import jks.tools2d.parallax.pages.Parallax_Model;

@DefaultSerializer(ParallaxLayerBaseValues_Serializer.class)
public class ParallaxDefaultValues
{
	
	public Parallax_Model defaultModel;
	public Parallax_Model incrementValue ; 
	public boolean addAsLast ; 
	public boolean increment ;
	public boolean autoGoToSelected ;
	public boolean alternateFlipX ; 
	public boolean alternateFlipY ; 
	
	
	public ParallaxDefaultValues()
	{
		defaultModel = new Parallax_Model() ;
		defaultModel.sizeRatio = 1 ; 
		defaultModel.parallaxScalingSpeedX = 0.01f ;
		defaultModel.parallaxScalingSpeedY = 0.01f ; 
		defaultModel.decal_Y_Ratio = 85 ;
		
		incrementValue = new Parallax_Model() ;
		incrementValue.decal_X_Ratio = 10 ; 
		incrementValue.decal_Y_Ratio = -8 ;
		incrementValue.parallaxScalingSpeedX = 0.003f ; 
		incrementValue.parallaxScalingSpeedY = 0.00025f ;
		incrementValue.sizeRatio = 0 ; 

		increment = true ; 
		autoGoToSelected = false ; 
	}
	
	public void doIncrement(boolean positive)
	{
		if(alternateFlipX)
			defaultModel.flipX = !defaultModel.flipX ;			
		if(alternateFlipY)
			defaultModel.flipX = !incrementValue.flipY ;  
 
		defaultModel.parallaxScalingSpeedX += (positive ? 1 : -1)* incrementValue.parallaxScalingSpeedX ; 
		defaultModel.parallaxScalingSpeedY += (positive ? 1 : -1)*incrementValue.parallaxScalingSpeedY ; 
		
		defaultModel.speed += (positive ? 1 : -1)*incrementValue.speed ; 
		defaultModel.sizeRatio += (positive ? 1 : -1)*incrementValue.sizeRatio ; 
		if(defaultModel.sizeRatio <= 0)
			defaultModel.sizeRatio = 0.01f ; 
		
		defaultModel.decal_X_Ratio += (positive ? 1 : -1)*incrementValue.decal_X_Ratio ; 
		defaultModel.decal_Y_Ratio += (positive ? 1 : -1)*incrementValue.decal_Y_Ratio ; 
		
		defaultModel.padMin += (positive ? 1 : -1)*incrementValue.padMin ; 
		defaultModel.padFactor += (positive ? 1 : -1)*incrementValue.padFactor ;
	}

	public boolean isAlternateFlipX()
	{
		return alternateFlipX;
	}

	public void setAlternateFlipX(boolean alternateFlipX)
	{
		this.alternateFlipX = alternateFlipX;
	}

	public boolean isAlternateFlipY()
	{
		return alternateFlipY;
	}

	public void setAlternateFlipY(boolean alternateFlipY)
	{
		this.alternateFlipY = alternateFlipY;
	} 
	
}

//public Parallax_Model defaultModel;
//public Parallax_Model incrementValue ; 
//public boolean addAsLast ; 
//public boolean increment ;
//public boolean autoGoToSelected ;
//public boolean alternateFlipX ; 
//public boolean alternateFlipY ; 

class ParallaxLayerBaseValues_Serializer extends Serializer<ParallaxDefaultValues>
{
	@Override
	public void write(Kryo kryo, Output output, ParallaxDefaultValues object) 
	{
		kryo.writeObject(output, object.defaultModel);
		kryo.writeObject(output, object.incrementValue);
		output.writeBoolean(object.addAsLast);
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
		
		returning.addAsLast = input.readBoolean() ;
		returning.increment = input.readBoolean() ;
		returning.autoGoToSelected = input.readBoolean() ;
		returning.alternateFlipX = input.readBoolean() ;
		returning.alternateFlipY = input.readBoolean() ;
		
		return returning;
	}
}