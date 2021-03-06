package jks.tools2d.parallax.editor.gvars;

import jks.tools2d.parallax.debug.GVars_Debug;
import jks.tools2d.parallax.debug.ShowFPS;
import jks.tools2d.parallax.editor.vue.model.AVue_Model;

public class GVars_Heart_Editor 
{
	public static float screenMovementSpeed = 1000.5f;
	public static boolean autoMoveScreen = true ; 
	public static AVue_Model vue;
	
	public static void init()
	{
		GVars_UI.init();
		GVars_UI.resize() ; 
		GVars_Serialization_Editor.init() ; 
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
		
		if(GVars_Debug.inDebug)
		{
			GVars_UI.mainUi.addActor(new ShowFPS(100, 100,4.5f));
		}
	}
}