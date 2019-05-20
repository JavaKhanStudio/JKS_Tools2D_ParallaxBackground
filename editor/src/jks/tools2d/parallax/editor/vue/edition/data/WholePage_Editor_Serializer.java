package jks.tools2d.parallax.editor.vue.edition.data;

import java.util.ArrayList;

import com.badlogic.gdx.graphics.Color;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import jks.tools2d.parallax.pages.Page_Model;

public class WholePage_Editor_Serializer extends Serializer<WholePage_Editor>
{

	@Override
	public void write(Kryo kryo, Output output, WholePage_Editor object) 
	{
		kryo.writeObject(output, object.topHalf_top);
		kryo.writeObject(output, object.topHalf_bottom);
		kryo.writeObject(output, object.bottomHalf_top);
		kryo.writeObject(output, object.bottomHalf_bottom);
		
		kryo.writeObject(output, object.pageModel);
		kryo.writeObject(output, object.inside);
		
	}	

	@Override
	public WholePage_Editor read(Kryo kryo, Input input, Class<? extends WholePage_Editor> type) 
	{
		WholePage_Editor page = new WholePage_Editor() ; 
		
		page.topHalf_top = kryo.readObject(input,Color.class) ; 
		page.topHalf_bottom = kryo.readObject(input,Color.class) ; 
		
		page.bottomHalf_top = kryo.readObject(input,Color.class) ; 
		page.bottomHalf_bottom = kryo.readObject(input,Color.class) ; 
		
		page.pageModel = kryo.readObject(input,Page_Model.class) ; 
		page.inside = kryo.readObject(input,ArrayList.class) ; 
		return page;
	}

}