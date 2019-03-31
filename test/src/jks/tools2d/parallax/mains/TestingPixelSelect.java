package jks.tools2d.parallax.mains;

import java.nio.ByteBuffer;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType; 


public class TestingPixelSelect extends ApplicationAdapter
{
	
	public Color color ; 
	ShapeRenderer render ; 
	
	@Override
	public void create () 
	{
		color = new Color() ; 
		InputProcessor input = buildClickProcessor() ; 	
		Gdx.input.setInputProcessor(new InputMultiplexer(input));
		render = new ShapeRenderer() ;
	}
	
	InputProcessor buildClickProcessor()
	{
		
		return new InputProcessor()
		{
			
			@Override
			public boolean touchUp(int screenX, int screenY, int pointer, int button)
			{
				return false;
			}
			
			@Override
			public boolean touchDragged(int screenX, int screenY, int pointer)
			{
				return false;
			}
			
			@Override
			public boolean touchDown(int screenX, int screenY, int pointer, int button)
			{
				Pixmap map = getFrameBufferPixmap(screenX, screenY, 1, 1) ; 
				color = new Color(map.getPixel(0, 0)) ;
				return false;
			}
			
			@Override
			public boolean scrolled(int amount)
			{
				return false;
			}
			
			@Override
			public boolean mouseMoved(int screenX, int screenY)
			{
				return true;
			}
			
			@Override
			public boolean keyUp(int keycode)
			{
				return false;
			}
			
			@Override
			public boolean keyTyped(char character)
			{
				
				return false;
			}
			
			@Override
			public boolean keyDown(int keycode)
			{
				// TODO Auto-generated method stub
				return false;
			}
		};
	}
	
	public static Pixmap getFrameBufferPixmap (int x, int y, int w, int h) 
	{
		Gdx.gl.glPixelStorei(GL30.GL_PACK_ALIGNMENT, 2);
		final Pixmap pixmap = new Pixmap(w, h, Format.RGBA8888);
		
		ByteBuffer pixels = pixmap.getPixels();
		Gdx.gl.glReadPixels(x, Gdx.graphics.getHeight() - y, w, h, GL30.GL_RGBA, GL30.GL_UNSIGNED_BYTE, pixels);

		return pixmap;
	}
	 
	float size = 100 ; 
	
	@Override
	public void render () 
	{
	    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
	    render.begin(ShapeType.Filled);
	    render.setColor(Color.BLUE);
	    render.rect(0, 0, size, size);
	    render.setColor(Color.RED);
	    render.rect(size, 0, size, size);
	    render.setColor(Color.CYAN);
	    render.rect(0, size, size, size);
	    render.setColor(color);
	    render.rect(size, size, size, size);
	    render.end();
	}
	
	@Override
	public void dispose() 
	{
		super.dispose();
	}
	
}
