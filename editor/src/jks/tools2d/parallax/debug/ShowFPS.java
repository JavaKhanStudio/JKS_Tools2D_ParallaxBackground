package jks.tools2d.parallax.debug;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;

import jks.tools2d.parallax.editor.gvars.GVars_Ui;

public class ShowFPS extends Table
{
	Label fpsLabel ; 
	
	public ShowFPS(float posX, float posY,float scale)
	{
		fpsLabel = new Label("", GVars_Ui.baseSkin);
		fpsLabel.setFontScale(scale);
		this.setPosition(posX, posY);
		this.add(fpsLabel);
	}
	
	@Override
	public void draw(Batch batch, float parentAlpha)
	{	
		fpsLabel.setText(Integer.toString(Gdx.graphics.getFramesPerSecond()));
		super.draw(batch, parentAlpha);
	}
}
