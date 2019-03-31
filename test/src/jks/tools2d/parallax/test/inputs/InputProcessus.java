package jks.tools2d.parallax.test.inputs;

import com.badlogic.gdx.Input.Keys;

import jks.tools2d.parallax.heart.Parallax_Utils_Page;
import jks.tools2d.parallax.test.Enum_PageModel_Day;

import static jks.tools2d.parallax.test.inputs.GVars_Inputs.*;
import com.badlogic.gdx.InputAdapter;
import static jks.tools2d.parallax.gvars.GVars_Heart_Testing.parallax_Heart ;

public class InputProcessus extends InputAdapter 
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
				parallax_Heart.setPage(Enum_PageModel_Day.DAY.wholePage) ; 
				return true ;
			case '2' :
				parallax_Heart.setPage(Enum_PageModel_Day.NIGHT.wholePage) ; 
				return true ;
			case '3' :
				parallax_Heart.setPage(Enum_PageModel_Day.RAIN.wholePage) ; 
				return true ;
			case '4' :
				parallax_Heart.setPage(Enum_PageModel_Day.SUNRISE.wholePage) ; 
				return true ;
			case '5' :
				parallax_Heart.setPage(Enum_PageModel_Day.SUNSET.wholePage) ; 
				return true ;
			case '&' : 
				parallax_Heart.transfertIntoPage(Enum_PageModel_Day.DAY.wholePage,10);
		}
		return false;
	}
}
