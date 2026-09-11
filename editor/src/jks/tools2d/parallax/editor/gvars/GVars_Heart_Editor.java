package jks.tools2d.parallax.editor.gvars;

import jks.tools2d.parallax.debug.GVars_Debug;
import jks.tools2d.parallax.debug.ShowFPS;
import jks.tools2d.parallax.editor.vue.model.AVue_Model;

public final class GVars_Heart_Editor
{
	/** Scroll speed applied while the arrow/WASD keys are held in the edition view. */
	public static float screenMovementSpeed = 1000.5f;
	public static AVue_Model vue;

	private GVars_Heart_Editor()
	{}

	public static void init()
	{
		GVars_UI.init();
		GVars_Serialization_Editor.init();
	}

	public static void changeVue(AVue_Model view, boolean cleanAll)
	{
		if (view == null)
			throw new IllegalArgumentException("view cannot be null");

		if (cleanAll && vue != null)
			vue.destroy();

		vue = view;
		vue.init();

		if (GVars_Debug.inDebug)
			GVars_UI.mainUi.addActor(new ShowFPS(100, 100, 4.5f));
	}
}
