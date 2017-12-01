package jks.tools2d.parallax.test;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import jks.tools2d.parallax.ParallaxBackground;
import jks.tools2d.parallax.test.inputs.GVars_Inputs;

public class GVars_Heart 
{

	public static OrthographicCamera worldCamera;
	public static final float screenMovementSpeed = 1.0f;
	public static boolean autoMoveScreen = true ; 
	public static SpriteBatch batch;
	public static ParallaxBackground parallaxBackground;
	
	public static void render()
	{
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		
		float delta = Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f);
		
		if(autoMoveScreen)
			worldCamera.position.add(screenMovementSpeed, 0, 0);
		
		GVars_Inputs.updateInput(delta);
		
		batch.begin();
		parallaxBackground.act(delta) ; 
		parallaxBackground.draw(worldCamera, batch);
		batch.end();
	}
}
