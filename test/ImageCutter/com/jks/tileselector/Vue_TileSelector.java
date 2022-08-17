package com.jks.tileselector;

import static com.jks.tileselector.vars.FVars_TileSelector.* ;
import static com.jks.tileselector.vars.GVars_TileSelector.* ;

import java.util.ArrayList;
import java.util.HashMap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;

import jks.tools2d.filechooser.FC_List;
import jks.tools2d.parallax.editor.gvars.GVars_UI;
import jks.tools2d.parallax.editor.vue.model.AVue_Model;

public class Vue_TileSelector extends AVue_Model 
{
	
	FC_List chooser ; 
	TextButton creatNew ; 
	int buttonSize ; 
	Texture texture ;
	public SpriteBatch batch;
	public ShapeRenderer shapeBatch;
	
	float proportionY ;
	boolean toResize ; 
	
//	ArrayList<Vector2> posToHighlight ; 
	HashMap<String,Vector2> posToHighlight ; 
	
	@Override
	public void init() 
	{	
		buildSelection(GVars_UI.baseSkin) ;	
		batch = new SpriteBatch() ;
		shapeBatch = new ShapeRenderer() ;
//		posToHighlight = new ArrayList<Vector2>() ;
		posToHighlight = new HashMap<String, Vector2>();
		Gdx.input.setInputProcessor(new IKM_TileSelector()) ; 	
	}
	
	public void buildSelection(Skin skin)
	{
		
	}
	

	@Override
	public void destroy() 
	{	
		GVars_UI.reset();
	}

	@Override
	public void restart() 
	{	
	}

	@Override
	public void update(float delta)
	{	
	}

	@Override
	public void render() 
	{
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		Gdx.gl.glEnable(GL20.GL_BLEND);
		Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		Gdx.gl.glBlendEquation(GL30.GL_MAX);
		
		if(toResize) {
			Gdx.graphics.setWindowedMode((int)(getHeight() + sizeBuffer),(int)(getWidth() + sizeBuffer)) ; 
			toResize = false ; 
		}
		
			
		batch.begin();
		batch.setColor(1, 1, 1, 0.5f);
		if(texture != null) {
			batch.draw(texture, sizeBuffer/2, sizeBuffer/2, sizeX, sizeY);
		}
		
		batch.end();
		
		shapeBatch.begin(ShapeType.Filled);
		shapeBatch.setColor(1, 0, 0, 0.2f);
		shapeBatch.rect(
					(getPosPosition().x * oneTileSize) - selectionSize()/2 + sizeBuffer/2, 
					Gdx.graphics.getHeight() - (getPosPosition().y * oneTileSize) - selectionSize()/2 - sizeBuffer/2, 
					selectionSize(), 
					selectionSize());
	
		shapeBatch.setColor(0, 1, 0, 0.2f);
		for(Vector2 pos: posToHighlight.values()) {
			shapeBatch.rect(
					(pos.x * oneTileSize) + sizeBuffer/2, 
					Gdx.graphics.getHeight() - (pos.y * oneTileSize) - sizeBuffer/2, 
					oneTileSize, oneTileSize);
		}

		
		shapeBatch.end();
		Gdx.gl.glDisable(GL20.GL_BLEND);
		
	}
	
	boolean leftPressed ; 
	boolean rightPressed ; 
	
	class IKM_TileSelector extends InputAdapter implements InputProcessor 
	{
		
		public boolean keyDown (int keycode) {
			switch(keycode) 
			{
				case Keys.A : 
				{
					System.out.println("My whole list");
					for(Vector2 pos : posToHighlight.values())
					{
						System.out.println(pos.cpy().add(relativePosX, relativePosY));
					}
					
				}
			}
			
			return false;
		}
		
		@Override
		public boolean touchDown (int posX, int posY, int pointer, int button) 
		{
		      if(button == Input.Buttons.LEFT) 
		    	 leftPressed = true ;
		      else if(button == Input.Buttons.RIGHT) 
		    	 rightPressed = true ;
		      
		      return true;
		}
		   
	    @Override
	    public boolean touchUp (int screenX, int screenY, int pointer, int button)
	    {
		  if(button == Input.Buttons.LEFT) 
	    	 leftPressed = false ;
	      else if(button == Input.Buttons.RIGHT) 
	    	 rightPressed = false ;
		   
	      return true;
	    }
		   
	    @Override
	    public boolean touchDragged (int screenX, int screenY, int pointer)
	    {
		 if(texture == null)
			 return false ; 
		   
//			 System.out.println(" Mouse - " + getRealPosition() + " Pos - " + getPosPosition() + " Final Pos - " + getRelativePosPosition());
			
		  if (leftPressed) 
	      {
	    	  
	    	  Vector2 currentPos = getPosPosition() ; 
	    	  int sizeSide = (currentMouseSelectionSize-1)/2 ;
	    	  
	    	  for(int x = -sizeSide ; x <= sizeSide ; x++) 
	    	  {
	    		  for(int y = -sizeSide ; y <= sizeSide ; y++) 
		    	  {
	    			  int currentX = (int) (x + currentPos.x) ;
	    			  int currentY = (int) (y + currentPos.y) ; 
	    			  
	    			  if(currentX < 0 || currentX > sizeX/oneTileSize - 1 ||
	    					 currentY - 1 < 0 || currentY > sizeY/oneTileSize)
	    				  continue ;

	    			  posToHighlight.put(currentX + "_" + currentY, new Vector2(currentX,currentY)) ;  
		    	  }    
	    	  }
	    	 
	      }
		  else if(rightPressed) 
		  {
			  Vector2 currentPos = getPosPosition() ; 
	    	  int sizeSide = (currentMouseSelectionSize-1)/2 ;
	    	  
	    	  for(int x = -sizeSide ; x <= sizeSide ; x++) 
	    	  {
	    		  for(int y = -sizeSide ; y <= sizeSide ; y++) 
		    	  {
	    			  int currentX = (int) (x + currentPos.x) ;
	    			  int currentY = (int) (y + currentPos.y) ; 
	    			  posToHighlight.remove(currentX + "_" + currentY) ;  
		    	  }    
	    	  }
		  }
	      return true;
	   }
		   	   
	}
	
	
	Vector2 getRealPosition()
	{
		return new Vector2(Gdx.input.getX() - sizeBuffer/2, Gdx.input.getY() - sizeBuffer/2) ;
	}
	
	Vector2 getPosPosition()
	{
		Vector2 ref = getRealPosition() ; 
		return new Vector2((int)ref.x/oneTileSize, (int)ref.y/oneTileSize) ;
	}
	
	Vector2 getRelativePosPosition() 
	{
		Vector2 ref = getPosPosition() ; 
		return new Vector2(ref.x + relativePosX, ref.y + relativePosY) ;
	}
	
	float selectionSize() {
		return oneTileSize * currentMouseSelectionSize ;
	}
	
	
//	public float getHeight() {
//		return Gdx.graphics.getHeight() - sizeBuffer ;
//	}
//	
//	public float getWidth() {
//		return proportionY * getHeight() ;
//	}
	
	public float getHeight() {
		return sizeX ;
	}
	
	public float getWidth() {
		return sizeY ;
	}

	@Override
	public void reciveFiles(String[] files)
	{
		System.out.println(files);
		
		String file = files[0] ;
		texture = new Texture(new FileHandle(file),true) ;
//		proportionY = (float)texture.getWidth()/(float)texture.getHeight() ;
		toResize = true ; 
	}

	@Override
	public void resize(int x, int y) 
	{

	}

}
