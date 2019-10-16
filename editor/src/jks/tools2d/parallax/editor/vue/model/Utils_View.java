package jks.tools2d.parallax.editor.vue.model;

import jks.tools2d.parallax.editor.gvars.GVars_Heart_Editor;
import jks.tools2d.parallax.editor.gvars.GVars_UI;

public class Utils_View 
{

	public static OverlayModel getOverlay()
	{return GVars_Heart_Editor.vue.overlay ;}
	
	public static void setOverlay(OverlayModel overlay)
	{
		removeCurrentOverlay() ; 
		GVars_Heart_Editor.vue.overlay = overlay ; 
		GVars_UI.mainUi.addActor(overlay) ;
//		GVars_Interface.activedInterface(GVars_Heart_Editor.vue.overlay);
	}
	
	public static void removeCurrentOverlay()
	{
		if(GVars_Heart_Editor.vue.overlay != null)
		{
			GVars_Heart_Editor.vue.overlay.destroy();
			GVars_Heart_Editor.vue.overlay = null ; 
		}

	}
	
	public static void setFilter(AView_Model_Filter filter_CanBuild)
	{
		removeFilter() ;
		GVars_Heart_Editor.vue.filter = filter_CanBuild ;
	}

	public static void removeFilter() 
	{
		if(GVars_Heart_Editor.vue.filter != null)
		{GVars_Heart_Editor.vue.filter = null ;}
	}
}
