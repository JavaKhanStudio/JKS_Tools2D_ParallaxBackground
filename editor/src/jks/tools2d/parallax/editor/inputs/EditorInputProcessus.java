package jks.tools2d.parallax.editor.inputs;

import static jks.tools2d.parallax.editor.inputs.GVars_Inputs.downPressed;
import static jks.tools2d.parallax.editor.inputs.GVars_Inputs.leftPressed;
import static jks.tools2d.parallax.editor.inputs.GVars_Inputs.rightPressed;
import static jks.tools2d.parallax.editor.inputs.GVars_Inputs.selectedItem;
import static jks.tools2d.parallax.editor.inputs.GVars_Inputs.upPressed;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;

import jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition;

/** Keyboard scrolling of the preview (arrows/WASD, space = up) and mouse wheel on the hovered slider. */
public class EditorInputProcessus extends InputAdapter
{
	@Override
	public boolean mouseMoved(int screenX, int screenY)
	{
		GVars_Vue_Edition.hideInterfaceTimmer = 0;
		return false;
	}

	@Override
	public boolean scrolled(float amountX, float amountY)
	{
		if (selectedItem == null || amountY == 0)
			return false;

		selectedItem.scrolled(amountY);
		return true;
	}

	@Override
	public boolean keyDown(int keycode)
	{return setDirection(keycode, true);}

	@Override
	public boolean keyUp(int keycode)
	{return setDirection(keycode, false);}

	private static boolean setDirection(int keycode, boolean pressed)
	{
		switch (keycode)
		{
			case Keys.W:
			case Keys.UP:
			case Keys.SPACE:
				upPressed = pressed;
				return true;
			case Keys.A:
			case Keys.LEFT:
				leftPressed = pressed;
				return true;
			case Keys.D:
			case Keys.RIGHT:
				rightPressed = pressed;
				return true;
			case Keys.S:
			case Keys.DOWN:
				downPressed = pressed;
				return true;
			default:
				return false;
		}
	}
}
