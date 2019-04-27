package jks.tools2d.parallax.pages;

import com.badlogic.gdx.graphics.Color;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class WholePage_Serializer extends Serializer<WholePage_Model>
{

	@Override
	public void write(Kryo kryo, Output output, WholePage_Model object) 
	{
		kryo.writeObject(output, object.topHalf_top);
		kryo.writeObject(output, object.topHalf_bottom);
		kryo.writeObject(output, object.bottomHalf_top);
		kryo.writeObject(output, object.bottomHalf_bottom);
		
		kryo.writeObject(output, object.pageModel);
	}	

	@Override
	public WholePage_Model read(Kryo kryo, Input input, Class<? extends WholePage_Model> type) 
	{
		WholePage_Model page = new WholePage_Model() ; 
		
		page.topHalf_top = kryo.readObject(input,Color.class) ; 
		page.topHalf_bottom = kryo.readObject(input,Color.class) ; 
		
		page.bottomHalf_top = kryo.readObject(input,Color.class) ; 
		page.bottomHalf_bottom = kryo.readObject(input,Color.class) ; 
		
		page.pageModel = kryo.readObject(input,Page_Model.class) ; 
		return page;
	}

}