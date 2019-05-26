package jks.tools2d.parallax;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

import jks.tools2d.parallax.pages.WholePage_Model;


public class ParallaxPageReader 
{
	public ArrayList<ParallaxLayer> layers;

	private Matrix4 cachedProjectionView;
	private float cachedZoom;
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
	
	/**
	 * Create a ParallaxBackground without any layers
	 */
	public ParallaxPageReader()
	{
		initialize();
		cachedPos = new Vector3() ; 
	}
	

	/**
	 * Create a ParallaxBackground instance with the layers added
	 * @param layers layers to be added to the parallaxBackground
	 */
	public ParallaxPageReader(ParallaxLayer... layers)
	{
		initialize();
		Collections.addAll(this.layers, layers);
		cachedPos = new Vector3() ; 
	}
	

	
    private void initialize() 
    {
    	layers = new ArrayList<ParallaxLayer>();
    	transferLayers = new ArrayList<ParallaxLayer>();
		cachedProjectionView = new Matrix4();
	}
	
	/**
	 * Add the layers to the {@link #layers} array. These layers are rendered over the layers previously in the layers array
	 * @param layers layers to be added to the parallaxBackground
	 */
	public void addLayers(ParallaxLayer... layers)
	{
		Collections.addAll(this.layers, layers);
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
	
	// TODO POSSIBLE OPTI
	///*
	public void draw(OrthographicCamera worldCamera, Batch batch)
	{
		cachedProjectionView.set(worldCamera.combined);
		cachedZoom = worldCamera.zoom;
		
		ParallaxLayer layer ; 
		Vector2 origCameraPos ; 
		
		float currentViewportDecal = (moveOnX ? worldCamera.viewportHeight : worldCamera.viewportWidth) * (worldCamera.zoom/2);

		for(int i = 0; i < layers.size(); i++)
		{
			layer = layers.get(i);
			batch.setColor(oldLayer_transfertColor);
    		
    		layer.draw(batch, layer.currentDistanceX, drawingHeight + layer.currentDistanceY); 
    		
    		for(float a = layer.getWidth(); a < worldCamera.viewportWidth - layer.currentDistanceX ; a+= layer.getWidth())
    			layer.draw(batch, layer.currentDistanceX + a, drawingHeight + layer.currentDistanceY); 
    		
    		for(float a = 1 ; layer.currentDistanceX - a * layer.getWidth() > -layer.getWidth() ; a++)
    			layer.draw(batch, layer.currentDistanceX - a * layer.getWidth(), drawingHeight + layer.currentDistanceY); 
    		
    		
    		if(inTransfer)
		    	compute_Color_Transfert(batch) ;	    		    
		}
		
		batch.setColor(1,1,1,1);	
	}
	//*/
	
	/*
	public void draw(OrthographicCamera worldCamera, Batch batch)
	{
		cachedProjectionView.set(worldCamera.combined);
		cachedPos.set(worldCamera.position);
		cachedZoom = worldCamera.zoom;
		
		ParallaxLayer layer ; 
		Vector2 origCameraPos ; 
		
		float currentX  ; 
		float currentY ;
		float currentViewportWidth = worldCamera.viewportWidth * (worldCamera.zoom/2) ;
		float currentViewportHeight =  worldCamera.viewportHeight * (worldCamera.zoom/2);
		
		for(int i = 0; i < layers.size(); i++)
		{
			layer = layers.get(i);
			origCameraPos = new Vector2(cachedPos.x,cachedPos.y);
			
			worldCamera.position.set(origCameraPos.scl(layer.getParallaxSpeedRatio()),cachedPos.z);
		    worldCamera.update();
		    batch.setProjectionMatrix(worldCamera.combined);
 
		    if(inTransfer)
		    	compute_Color_Transfert(batch) ;
		    
		    currentX = layer.currentX ;
		    currentY = layer.currentY ; 
		    		    
		    do
			{
		    	currentY =  (!layer.isRepeat_tileY() ? 0 : ((int)((worldCamera.position.y-worldCamera.viewportHeight*.5f*worldCamera.zoom) / layer.getHeight())) * layer.getHeight())-(((1-layer.getParallaxSpeedRatio().y)%1)*worldCamera.viewportHeight*.5f);
		    	
	            do
	            {
	            	if(! ((worldCamera.position.x - currentViewportWidth - layer.getCurrentDistanceX() >= currentX + layer.getWidth())
            		   || (worldCamera.position.x + currentViewportWidth - layer.getCurrentDistanceX() <= currentX )  
            		   || (worldCamera.position.y - currentViewportHeight - layer.getCurrentDistanceY() > currentY + layer.getHeight() * 1.35f) 
            		   || (worldCamera.position.y + currentViewportHeight - layer.getCurrentDistanceY() < currentY)))
	               {	            		
	            		
	            		batch.setColor(oldLayer_transfertColor);
	            		
	            		layer.draw(batch, currentX + layer.currentDistanceX, currentY + drawingHeight + layer.currentDistanceY); 
	            		
	            		if(inTransfer)
	            		{
	            			batch.setColor(newLayer_transfertColor);
	            			transferLayers.get(i).draw(batch, currentX + layer.currentDistanceX, currentY + drawingHeight);
	            		}
	               }
	                   
	               currentY += layer.getHeight();
	               
	               if(!layer.isRepeat_tileY())
		        	     break;
	               
	            } while(currentY < worldCamera.position.y + currentViewportHeight);
		            	
	            currentX += layer.getWidth();
	           
	            if(!layer.repeat_tileX)
	        	    break;
		            
		    } while(currentX + layer.getCurrentDistanceX() < worldCamera.position.x + currentViewportWidth);
		}
		
		worldCamera.combined.set(cachedProjectionView);
		worldCamera.position.set(cachedPos);
		worldCamera.zoom = cachedZoom;
		worldCamera.update();
		batch.setColor(1,1,1,1);	
	}
//	*/
	
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
	
	
	public void act(float delta,float speed) 
	{
		layers.stream().forEach(x -> x.act(delta,speed));

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

}