package jks.tools2d.parallax.pages;

import com.badlogic.gdx.graphics.Color;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class Page_Whole_Serializer extends Serializer<Page_Whole_Model>
{

	@Override
	public void write(Kryo kryo, Output output, Page_Whole_Model object) 
	{
		kryo.writeObject(output, object.colorSurronding);
		
		kryo.writeObject(output, object.topHalf_top);
		kryo.writeObject(output, object.topHalf_bottom);
		kryo.writeObject(output, object.bottomHalf);
		
		kryo.writeObject(output, object.pageModel);
	}
	

	@Override
	public Page_Whole_Model read(Kryo kryo, Input input, Class<? extends Page_Whole_Model> type) 
	{
		Page_Whole_Model page = new Page_Whole_Model() ; 
		page.colorSurronding = kryo.readObject(input,Color.class) ; 
		
		page.topHalf_top = kryo.readObject(input,Color.class) ; 
		page.topHalf_bottom = kryo.readObject(input,Color.class) ; 
		page.bottomHalf = kryo.readObject(input,Color.class) ; 
		
		page.pageModel = kryo.readObject(input,Page_Model.class) ; 
		return page;
	}

}
