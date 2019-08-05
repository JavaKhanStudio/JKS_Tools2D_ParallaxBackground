package jks.tools2d.parallax.heart;

import java.util.ArrayList;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.esotericsoftware.kryo.Kryo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import jks.tools2d.parallax.pages.Color_Serializer;
import jks.tools2d.parallax.pages.Page_Model;
import jks.tools2d.parallax.pages.Parallax_Model;
import jks.tools2d.parallax.pages.WholePage_Model;

public class GVars_Serialization 
{

	public static Kryo kryo ;
	public static ObjectMapper objectMapper ;
	
	public static void init() 
	{
		prepareKryo() ; 
		prepareJson() ; 
	}

	public static Kryo prepareKryo()
	{
		if(kryo == null)
			kryo = new Kryo();
		
	    kryo.register(Color.class, new Color_Serializer());
	    kryo.register(Parallax_Model.class) ; 
	    kryo.register(Page_Model.class) ;
	    kryo.register(ArrayList.class) ; 
	    kryo.register(WholePage_Model.class) ; 
	    return kryo ; 
	}
	
	public static ObjectMapper prepareJson() 
	{
		if(objectMapper == null)
			objectMapper = new ObjectMapper() ; 
		
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false) ; 
		objectMapper.addMixInAnnotations(TextureRegion.class, MyMixInForIgnoreType.class);
		return objectMapper ; 
	}
}
