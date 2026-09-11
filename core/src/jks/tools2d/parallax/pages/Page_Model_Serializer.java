package jks.tools2d.parallax.pages;

import java.util.ArrayList;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class Page_Model_Serializer extends Serializer<Page_Model>
{
	@Override
	public void write(Kryo kryo, Output output, Page_Model page)
	{
		output.writeString(page.atlasName);
		output.writeBoolean(page.outside);
		kryo.writeObject(output, page.pageList);
	}

	@Override
	@SuppressWarnings("unchecked")
	public Page_Model read(Kryo kryo, Input input, Class<? extends Page_Model> type)
	{
		Page_Model page = new Page_Model();
		page.atlasName = input.readString();
		page.outside = input.readBoolean();
		page.pageList = kryo.readObject(input, ArrayList.class);
		return page;
	}
}
