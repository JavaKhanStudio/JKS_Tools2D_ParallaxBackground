package jks.tools2d.parallax.editor.inputs;

import static jks.tools2d.parallax.editor.gvars.GVars_Heart_Editor.screenMovementSpeed;
import static jks.tools2d.parallax.editor.vue.Vue_Edition.parallax_Heart;

import jks.tools2d.libgdxutils.SelectableItem;

public final class GVars_Inputs
{
	/** Widget under the mouse that receives the mouse wheel. */
	public static SelectableItem selectedItem;

	public static boolean upPressed, downPressed, leftPressed, rightPressed;

	private GVars_Inputs()
	{}

	public static void quitSelectedItem(SelectableItem item)
	{
		if (selectedItem == item)
		{
			selectedItem.quit();
			selectedItem = null;
		}
	}

	public static void reset()
	{
		selectedItem = null;
		upPressed = downPressed = leftPressed = rightPressed = false;
	}

	/** Turns the held direction keys into a scroll of the preview for this frame. */
	public static void updateInput()
	{
		if (leftPressed)
			parallax_Heart.screenSpeedConsumableX = -screenMovementSpeed;

		if (rightPressed)
			parallax_Heart.screenSpeedConsumableX = screenMovementSpeed;

		if (upPressed)
			parallax_Heart.screenSpeedConsumableY = screenMovementSpeed;

		if (downPressed)
			parallax_Heart.screenSpeedConsumableY = -screenMovementSpeed;
	}
}
