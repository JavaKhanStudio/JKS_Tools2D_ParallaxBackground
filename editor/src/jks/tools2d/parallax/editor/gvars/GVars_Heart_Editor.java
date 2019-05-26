package jks.tools2d.parallax.editor.gvars;

import jks.tools2d.parallax.debug.ShowFPS;
import jks.tools2d.parallax.editor.vue.model.AVue_Model;

public class GVars_Heart_Editor 
{
//	public static float screenMovementSpeed = 10.5f;
	public static float screenMovementSpeed = 1000.5f;
	public static boolean autoMoveScreen = true ; 
	public static boolean inDebug = false ; 
	public static AVue_Model vue;
	
	public static void init()
	{
		GVars_Ui.init();
		GVars_Kryo.init() ; 
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
		
		if(inDebug)
		{
			GVars_Ui.mainUi.addActor(new ShowFPS(100, 100,4.5f));
		}
	}
}