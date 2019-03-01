package jks.tools2d.parallax.editor.inputs;

import com.badlogic.gdx.Input.Keys;

import jks.tools2d.parallax.heart.Parallax_Utils_Page;

import static jks.tools2d.parallax.editor.inputs.GVars_Inputs.*;
import com.badlogic.gdx.InputAdapter;

public class EditorInputProcessus extends InputAdapter 
{
	@Override
	public boolean keyDown (int keycode) 
	{
		switch (keycode) 
		{
			case Keys.W:
			case Keys.UP:
			case Keys.SPACE:
				upPressed = true ; 
				return true;
			case Keys.A:
			case Keys.LEFT:
				leftPressed = true;
				return true;
			case Keys.D:
			case Keys.RIGHT:
				rightPressed = true;
				return true ; 
			case Keys.DOWN :
			case Keys.S :
				downPressed = true ;
				return true;
			case Keys.PLUS : 
			case Keys.Q : 
				zoomInPressed = true ;
				return true ; 
			case Keys.MINUS : 
			case Keys.E : 
				zoomOutPressed = true ;
				return true ; 
			
		}
		return false;
	}
	
	@Override
	public boolean keyUp (int keycode) 
	{
		switch (keycode) 
		{
			case Keys.W:
			case Keys.UP:
			case Keys.SPACE:
				upPressed = false ; 
				return true;
			case Keys.A:
			case Keys.LEFT:
				leftPressed = false;
				return true;
			case Keys.D:
			case Keys.RIGHT:
				rightPressed = false;
				return true;
			case Keys.DOWN :
			case Keys.S :
				downPressed = false ;
				return true ; 
			case Keys.PLUS : 
			case Keys.Q : 
				zoomInPressed = false ;
				return true ; 
			case Keys.MINUS : 
			case Keys.E : 
				zoomOutPressed = false ;
				return true ; 
		}
		return false;
	}
	
	
	public boolean keyTyped (char character) {
		switch (character) 
		{
			case '1' :
				return true ;
			case '2' :
				return true ;
			case '3' :
				return true ;
			case '4' :
				return true ;
			case '5' :
				return true ;
			case '&' : 
		}
		return false;
	}
}
