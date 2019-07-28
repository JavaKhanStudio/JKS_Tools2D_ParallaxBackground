package jks.tools2d.parallax;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector3;

import jks.tools2d.parallax.pages.WholePage_Model;


public class ParallaxPageReader 
{
	public ArrayList<ParallaxLayer> layers;

	private boolean repeatOnX,repeatOnY  ; 

	private float drawingHeight  ;
	
	public ArrayList<ParallaxLayer> transferLayers;
	float newLayer_transfertLvl = 0 ;
	float oldLayer_transfertLvl = 0 ; 
	float newLayer_transfertSpeed ; 
	float oldLayer_transfertSpeed ; 
	boolean inTransfer ;
	
	private static final float transparencyPoint = 0.95f ;
	
	Color transfertColor_objectif = new Color(1,1,1,1);
	Color transfertColor_speed = new Color(1,1,1,1);
	
	Color oldLayer_transfertColor = new Color(1,1,1,1);
	Color newLayer_transfertColor = new Color(1,1,1,0); 
	
	boolean moveOnX = true; 
	
	private Vector3 cachedPos;
	
	public ParallaxPageReader()
	{
		initialize();
		cachedPos = new Vector3() ; 
	}
	
	
    private void initialize() 
    {
    	layers = new ArrayList<ParallaxLayer>();
    	transferLayers = new ArrayList<ParallaxLayer>();
	}
	
	public void addLayers(List<ParallaxLayer> newLayers) 
	{
		for(ParallaxLayer texture : newLayers) 
			this.layers.add(texture);
	}
	
	public void addLayersTransfert(WholePage_Model pageModel, float inXSecondes) 
	{
		inTransfer = true ; 
		List<ParallaxLayer> newLayers ;
		
		newLayers = pageModel.getDrawing() ; 
		
		newLayer_transfertSpeed = 1/inXSecondes  ;
		oldLayer_transfertSpeed = 1/(inXSecondes * (1-transparencyPoint)) ; 
		
		resetTransfert() ; 
		
		if(newLayers != null && !newLayers.isEmpty())
		{
			for(ParallaxLayer texture : newLayers) 
				this.transferLayers.add(texture);
		}
		else // Keep statu Quo
		{
			transferLayers = layers ;
			oldLayer_transfertSpeed = 0 ;
		}
	}
	
	public void addColorTransfert(Color color, float inXSecondes) 
	{
		if(color == null)
			return ; 
		
		inTransfer = true ; 
		newLayer_transfertSpeed = 1/inXSecondes ;
		
		transferLayers = layers ;
		set_newLayer_Color(color);
	}
	
	public void draw(OrthographicCamera worldCamera, Batch batch)
	{
		
		if(repeatOnY && repeatOnX)
		{
			drawOnXY(worldCamera,batch) ; 
		}
		else if(repeatOnY)
		{
			drawOnY(worldCamera,batch) ; 
		}
		else if(repeatOnX)
		{	
			drawOnX(worldCamera,batch) ; 
		}
		
		batch.setColor(1,1,1,1);	
	}
	
	private void drawOnXY(OrthographicCamera worldCamera, Batch batch) 
	{
		ParallaxLayer layer ; 
		for(int i = 0; i < layers.size(); i++)
		{
			layer = layers.get(i);
			batch.setColor(oldLayer_transfertColor);
    		
    		layer.draw(batch, layer.currentDistanceX, drawingHeight + layer.currentDistanceY); 
    		
    		for(float a = layer.getTotalWidth(); a < worldCamera.viewportWidth - layer.currentDistanceX ; a+= layer.getTotalWidth())
    			layer.draw(batch, layer.currentDistanceX + a, drawingHeight + layer.currentDistanceY); 
    		
    		for(float a = 1 ; layer.currentDistanceX - a * layer.getTotalWidth() > -layer.getTotalWidth() ; a++)
    			layer.draw(batch, layer.currentDistanceX - a * layer.getTotalWidth(), drawingHeight + layer.currentDistanceY); 
    		
    		for(float a = layer.getTotalHeight(); a < worldCamera.viewportHeight - layer.currentDistanceY ; a+= layer.getTotalHeight())
    		{
    			layer.draw(batch, layer.currentDistanceX , drawingHeight  + a + layer.currentDistanceY); 
    			for(float b = layer.getTotalWidth(); b < worldCamera.viewportWidth - layer.currentDistanceX ; b+= layer.getTotalWidth())
        			layer.draw(batch, layer.currentDistanceX + b, drawingHeight  + a + layer.currentDistanceY); 
    			for(float b = 1 ; layer.currentDistanceX - b * layer.getTotalWidth() > -layer.getWidth() ; b++)
	    			layer.draw(batch, layer.currentDistanceX - b * layer.getTotalWidth(), drawingHeight  + a + layer.currentDistanceY); 	    		
    		}
		
			for(float a = 1 ; layer.currentDistanceY - (a * layer.getTotalHeight()) > -layer.getHeight() ; a++)
			{
				layer.draw(batch, layer.currentDistanceX, drawingHeight + layer.currentDistanceY - (a * layer.getTotalHeight())); 
				for(float b = layer.getTotalWidth(); b < worldCamera.viewportWidth - layer.currentDistanceX ; b+= layer.getTotalWidth())
	    			layer.draw(batch, layer.currentDistanceX + b, drawingHeight + layer.currentDistanceY - (a * layer.getTotalHeight())); 
	    		
	    		for(float b = 1 ; layer.currentDistanceX - b * layer.getTotalWidth() > -layer.getWidth() ; b++)
	    			layer.draw(batch, layer.currentDistanceX - b * layer.getTotalWidth(), drawingHeight + layer.currentDistanceY - (a * layer.getTotalHeight())); 	    		
			}

    		if(inTransfer)
		    	compute_Color_Transfert(batch) ;	    		    
		}	
	}


	public void drawOnX(OrthographicCamera worldCamera, Batch batch)
	{
		ParallaxLayer layer ; 
		for(int i = 0; i < layers.size(); i++)
		{
			layer = layers.get(i);
			batch.setColor(oldLayer_transfertColor);
    		
    		layer.draw(batch, layer.currentDistanceX, drawingHeight + layer.currentDistanceY); 
    		
    		for(float a = layer.getTotalWidth(); a < worldCamera.viewportWidth - layer.currentDistanceX ; a+= layer.getTotalWidth())
    			layer.draw(batch, layer.currentDistanceX + a, drawingHeight + layer.currentDistanceY); 
    		
    		for(float a = 1 ; layer.currentDistanceX - a * layer.getTotalWidth()  > -(layer.getWidth()) ; a++)
    			layer.draw(batch, layer.currentDistanceX - (a * layer.getTotalWidth()), drawingHeight + layer.currentDistanceY); 
    		
    		if(layer.isMirror)
    		{
    			layer.draw(batch, layer.currentDistanceX, drawingHeight + layer.currentDistanceY + layer.padY + layer.getHeight(),true); 
        		
        		for(float a = layer.getTotalWidth(); a < worldCamera.viewportWidth - layer.currentDistanceX ; a+= layer.getTotalWidth())
        			layer.draw(batch, layer.currentDistanceX + a, drawingHeight + layer.currentDistanceY + layer.padY + layer.getHeight(),true); 
        		
        		for(float a = 1 ; layer.currentDistanceX - a * layer.getTotalWidth()  > -(layer.getWidth()) ; a++)
        			layer.draw(batch, layer.currentDistanceX - (a * layer.getTotalWidth()), drawingHeight + layer.currentDistanceY + layer.padY + layer.getHeight(),true); 
        	}
    		
    		
    		if(inTransfer)
		    	compute_Color_Transfert(batch) ;	    		    
		}	
	}
	
	public void drawOnY(OrthographicCamera worldCamera, Batch batch)
	{
		ParallaxLayer layer ; 
		for(int i = 0; i < layers.size(); i++)
		{
			layer = layers.get(i);
			batch.setColor(oldLayer_transfertColor);
    		
    		layer.draw(batch, layer.currentDistanceX, drawingHeight + layer.currentDistanceY); 
    		
    		for(float a = layer.getTotalHeight() ; a < worldCamera.viewportHeight - layer.currentDistanceY ; a+= layer.getTotalHeight())
    			layer.draw(batch, layer.currentDistanceX , drawingHeight  + a + layer.currentDistanceY); 
    		
    		for(float a = 1 ; layer.currentDistanceY - a * layer.getTotalHeight() > -layer.getHeight() ; a++)
    			layer.draw(batch, layer.currentDistanceX, drawingHeight + layer.currentDistanceY - a * layer.getTotalHeight()); 
    		
    		if(layer.isMirror)
    		{
    			layer.draw(batch, layer.currentDistanceX + layer.padX + layer.getWidth(), drawingHeight + layer.currentDistanceY,false); 
        		
        		for(float a = layer.getTotalHeight() ; a < worldCamera.viewportHeight - layer.currentDistanceY ; a+= layer.getTotalHeight())
        			layer.draw(batch, layer.currentDistanceX + layer.padX + layer.getWidth(), drawingHeight  + a + layer.currentDistanceY,false); 
        		
        		for(float a = 1 ; layer.currentDistanceY - a * layer.getTotalHeight() > -layer.getHeight() ; a++)
        			layer.draw(batch, layer.currentDistanceX + layer.padX + layer.getWidth(), drawingHeight + layer.currentDistanceY - a * layer.getTotalHeight(),false); 
        	}
    		
    		if(inTransfer)
		    	compute_Color_Transfert(batch) ;	    		    
		}	
	}
	

	
	public void compute_Color_Transfert(Batch batch)
	{
		newLayer_transfertColor.a = newLayer_transfertLvl;	
		
		if(newLayer_transfertLvl >= transparencyPoint)
		{
			oldLayer_transfertColor.a = 1 - oldLayer_transfertLvl ;
		}
			
	}
	
	public void resetTransfert()
	{
		transferLayers = new ArrayList<ParallaxLayer>() ;
		
		oldLayer_transfertColor.a = 1 ; 
		newLayer_transfertColor.a = 0 ; 
		newLayer_transfertLvl = 0 ; 
		oldLayer_transfertLvl = 0 ; 
	}
	
	public void resetPositions()
	{
		layers.stream().forEach(x -> x.resetPosition()) ; 
		
	}

	
	public void act(float delta,float speedX,float speedY) 
	{
		layers.stream().forEach(x -> x.act(delta,speedX,speedY,repeatOnX,repeatOnY));

		if(inTransfer)
		{
			newLayer_transfertLvl += delta * newLayer_transfertSpeed ; 
			
			if(newLayer_transfertLvl >= transparencyPoint)
				oldLayer_transfertLvl += delta * oldLayer_transfertSpeed ; 
			
			if(newLayer_transfertLvl > 1)
			{
				for(int a=0; a < layers.size() ; a++) 
				{transferLayers.get(a).setCurrentDistanceX(layers.get(a).getCurrentDistanceX());}
				
				layers = transferLayers ; 
				oldLayer_transfertColor = newLayer_transfertColor.cpy(); 
				inTransfer = false ; 
				resetTransfert() ; 
			}
		}
	}
	
	public float getDrawingHeight() 
	{return drawingHeight;}

	public void setDrawingHeight(float drawingHeight) 
	{this.drawingHeight = drawingHeight;}

	public void transfertTo(ArrayList<ParallaxLayer> transferLayers)
	{this.transferLayers = transferLayers ;}
	
	public Color get_newLayer_Color() 
	{return newLayer_transfertColor;}

	public void set_newLayer_Color(Color color) 
	{this.newLayer_transfertColor = color;}
	
	public Color get_oldLayer_Color() 
	{return oldLayer_transfertColor;}

	public void set_oldLayer_Color(Color color) 
	{this.oldLayer_transfertColor = color;}
	
	public boolean isInTransfer() 
	{return inTransfer;}

	public void setInTransfer(boolean inTransfer) 
	{this.inTransfer = inTransfer;}

	public boolean isRepeatOnX()
	{return repeatOnX;}

	public void setRepeatOnX(boolean repeatOnX) 
	{this.repeatOnX = repeatOnX;}

	public boolean isRepeatOnY() 
	{return repeatOnY;}

	public void setRepeatOnY(boolean repeatOnY) 
	{this.repeatOnY = repeatOnY;}

}