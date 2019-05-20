package jks.tools2d.parallax.editor.gvars;

import java.util.ArrayList;

import com.badlogic.gdx.graphics.Color;
import com.esotericsoftware.kryo.Kryo;

import jks.tools2d.parallax.editor.vue.edition.data.ParallaxDefaultValues;
import jks.tools2d.parallax.editor.vue.edition.data.Position_Infos;
import jks.tools2d.parallax.editor.vue.edition.data.Project_Data;
import jks.tools2d.parallax.editor.vue.edition.data.WholePage_Editor;
import jks.tools2d.parallax.pages.Color_Serializer;
import jks.tools2d.parallax.pages.Page_Model;
import jks.tools2d.parallax.pages.Parallax_Model;
import jks.tools2d.parallax.pages.WholePage_Model;

public class GVars_Kryo 
{

	public static Kryo kryo ;

	public static void init() 
	{
		kryo = new Kryo();
	    kryo.register(Color.class, new Color_Serializer());
	    kryo.register(Parallax_Model.class) ; 
	    kryo.register(Page_Model.class) ;
	    kryo.register(ArrayList.class) ; 
	    kryo.register(WholePage_Model.class) ;
	    kryo.register(WholePage_Editor.class) ;
	    kryo.register(ParallaxDefaultValues.class) ; 
	    kryo.register(Position_Infos.class) ; 
	    kryo.register(Project_Data.class) ; 	
	}

}
