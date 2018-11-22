package jks.tools2d.parallax;

import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

import jks.tools2d.parallax.side.SolarAstre;


public class ParallaxPage 
{
	public Array<ParallaxLayer> layers;
	
	private static final float startNewOpacity = 1f ; 
	
	private Matrix4 cachedProjectionView;
	private Vector3 cachedPos;
	private float cachedZoom;
	private float drawingHeight  ;
	
	public Array<ParallaxLayer> transferLayers;
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

	
	/**
	 * Create a ParallaxBackground without any layers
	 */
	public ParallaxPage()
	{
		initialize();
	}
	

	/**
	 * Create a ParallaxBackground instance with the layers added
	 * @param layers layers to be added to the parallaxBackground
	 */
	public ParallaxPage(ParallaxLayer... layers)
	{
		initialize();
		this.layers.addAll(layers);
	}
	

	
    private void initialize() 
    {
    	layers = new Array<ParallaxLayer>();
    	transferLayers = new Array<ParallaxLayer>();
		cachedPos = new Vector3();
		cachedProjectionView = new Matrix4();
	}
	
	/**
	 * Add the layers to the {@link #layers} array. These layers are rendered over the layers previously in the layers array
	 * @param layers layers to be added to the parallaxBackground
	 */
	public void addLayers(ParallaxLayer... layers)
	{
		this.layers.addAll(layers);
	}
	
	public void addLayers(List<TextureRegionParallaxLayer> newLayers) 
	{
		for(TextureRegionParallaxLayer texture : newLayers) 
			this.layers.add(texture);
	}
	
	public void addLayersTransfert(List<TextureRegionParallaxLayer> newLayers, float inXSecondes) 
	{
		inTransfer = true ; 
		newLayer_transfertSpeed = 1/inXSecondes  ;
		oldLayer_transfertSpeed = 1/(inXSecondes * (1-transparencyPoint)) ; 
		
		resetTransfert() ; 
		
		if(newLayers != null && !newLayers.isEmpty())
		{
			for(TextureRegionParallaxLayer texture : newLayers) 
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
	
	/**
	 * render the layers held by this module. Of course the layers are rendered in parallax scrolling manner. The worldCamera and batch provided are unaffected by the method
	 * @param worldCamera The Orthographic WorldCamera , all layers are rendered relative to its position.
	 * @param batch The batch which is used to render the layers.
	 */
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
		
		for(int i = 0; i < layers.size; i++)
		{
			layer = layers.get(i);
			origCameraPos = new Vector2(cachedPos.x,cachedPos.y);
			
			worldCamera.position.set(origCameraPos.scl(layer.getParallaxRatio()),cachedPos.z);
		    worldCamera.update();
		    batch.setProjectionMatrix(worldCamera.combined);
 
		    if(inTransfer)
		    	compute_Color_Transfert(batch) ;
		    
		    if(layer.getSpeed() != 0)
		    	currentX = -layer.getWidth() * 1.25f ;
	    	else
		    	currentX = 0 ;
		    
		    do
			{
		    	currentY =  (!layer.isRepeat_tileY() ? 0 : ((int)((worldCamera.position.y-worldCamera.viewportHeight*.5f*worldCamera.zoom) / layer.getHeight())) * layer.getHeight())-(((1-layer.getParallaxRatio().y)%1)*worldCamera.viewportHeight*.5f);

	            do
	            {
	            	if(! ((worldCamera.position.x - currentViewportWidth - layer.getCurrentX() > currentX + layer.getWidth())
            		   || (worldCamera.position.x + currentViewportWidth - layer.getCurrentX() < currentX )  
            		   || (worldCamera.position.y - currentViewportHeight > currentY + layer.getHeight()) 
            		   || (worldCamera.position.y + currentViewportHeight < currentY)))
	               {	            		
	            		
	            		batch.setColor(oldLayer_transfertColor);
	            		
	            		layer.draw(batch, currentX + layer.decalX, currentY + drawingHeight); 
	            		
	            		if(inTransfer)
	            		{
	            			batch.setColor(newLayer_transfertColor);
	            			transferLayers.get(i).draw(batch, currentX + layer.decalX, currentY + drawingHeight);
	            		}
	               }
	                   
	               currentY += layer.getHeight();
	               
	               if(!layer.isRepeat_tileY())
		        	     break;
	               
	            } while(currentY < worldCamera.position.y + currentViewportHeight);
		            	
	            currentX += layer.getWidth();
	           
	            if(!layer.repeat_tileX)
	        	    break;
		            
		    } while(currentX + layer.getCurrentX() < worldCamera.position.x + currentViewportWidth);
		}
		
		worldCamera.combined.set(cachedProjectionView);
		worldCamera.position.set(cachedPos);
		worldCamera.zoom = cachedZoom;
		worldCamera.update();
		batch.setColor(1,1,1,1);	
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
		transferLayers = new Array<ParallaxLayer>() ;
		
		oldLayer_transfertColor.a = 1 ; 
		newLayer_transfertColor.a = 0 ; 
		newLayer_transfertLvl = 0 ; 
		oldLayer_transfertLvl = 0 ; 
	}
	
	public void act(float delta) 
	{
		for(int a=0; a < layers.size ; a++) 
		{layers.get(a).act(delta);}
		
		if(inTransfer)
		{
			
			newLayer_transfertLvl += delta * newLayer_transfertSpeed ; 
			
			if(newLayer_transfertLvl >= transparencyPoint)
				oldLayer_transfertLvl += delta * oldLayer_transfertSpeed ; 
			
			if(newLayer_transfertLvl > 1)
			{
				for(int a=0; a < layers.size ; a++) 
				{transferLayers.get(a).setDecalX(layers.get(a).getDecalX());}
				
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

	public void transfertTo(Array<ParallaxLayer> transferLayers)
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
