package jks.tools2d.libgdxutils;

import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.Align;

import jks.tools2d.parallax.editor.gvars.GVars_UI;

public class Utils_ComponentBuilding
{
	
	Button buildButton (String text, boolean toggle,float sizeX, float sizeY)
	{
		Button button = new Button(GVars_UI.baseSkin);
		button.setSize(sizeX, sizeY);
		button.pad(sizeY,sizeX,sizeY,sizeX);
		button.setClip(true);
		
		Label label = new Label(text,GVars_UI.baseSkin);
		label.setAlignment(Align.center);
		
		button.add(label);
		
		return button;
	}
}
