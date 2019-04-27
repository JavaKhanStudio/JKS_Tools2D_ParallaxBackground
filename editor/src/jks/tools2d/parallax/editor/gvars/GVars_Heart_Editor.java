package jks.tools2d.parallax.editor.gvars;

import java.util.ArrayList;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.esotericsoftware.kryo.Kryo;

import jks.tools2d.parallax.editor.vue.model.AVue_Model;
import jks.tools2d.parallax.pages.Color_Serializer;
import jks.tools2d.parallax.pages.Page_Model;
import jks.tools2d.parallax.pages.Parallax_Model;
import jks.tools2d.parallax.pages.WholePage_Model;

public class GVars_Heart_Editor 
{
	public static float screenMovementSpeed = 10.5f;
	public static boolean autoMoveScreen = true ; 
	public static AVue_Model vue;
//	public static Batch batch ; 
//	public static ShapeRenderer shapeRender ; 
	public static boolean debug ;
	public static Kryo kryo ;
	
	
	public static void init()
	{
		GVars_Ui.init();
		kryo = new Kryo();
	    kryo.register(Color.class, new Color_Serializer());
	    kryo.register(Parallax_Model.class) ; 
	    kryo.register(Page_Model.class) ;
	    kryo.register(ArrayList.class) ; 
	    kryo.register(WholePage_Model.class) ; 
	}


	public static void changeVue(AVue_Model View,boolean cleanAll) 
	{
		if(cleanAll) 
		{
			if(vue != null)
				vue.destroy();
		}
		if (View != null) 
		{
			vue = View;
			vue.init();
		} 
		else 
		{System.out.println("Aucune view?");}
	}
}
