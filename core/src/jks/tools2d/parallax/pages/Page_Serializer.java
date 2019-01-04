package jks.tools2d.parallax.pages;

import java.util.ArrayList;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class Page_Serializer extends Serializer<Page_Model>
{

	@Override
	public void write(Kryo kryo, Output output, Page_Model object) 
	{
		output.writeString(object.atlasPath);
		output.writeBoolean(object.outside);
		kryo.writeObject(output, object.pageList);
	}
	
	@Override
	public Page_Model read(Kryo kryo, Input input, Class<? extends Page_Model> type) 
	{
		Page_Model page = new Page_Model() ; 
		page.atlasPath = input.readString() ; 
		page.outside = input.readBoolean() ; 
		page.pageList = kryo.readObject(input, ArrayList.class); 
		return page;
	}

}
