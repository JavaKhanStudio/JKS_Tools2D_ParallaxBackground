package jks.tools2d.parallax;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;


public class ParallaxBackground 
{
	

	public Array<ParallaxLayer> layers;
	private Matrix4 cachedProjectionView;
	private Vector3 cachedPos;
	private float cachedZoom;
	private final static float half = 0.5f ;
	
	/**
	 * Create a ParallaxBackground without any layers
	 */
	public ParallaxBackground()
	{
		initialize();
	}
	

	/**
	 * Create a ParallaxBackground instance with the layers added
	 * @param layers layers to be added to the parallaxBackground
	 */
	public ParallaxBackground(ParallaxLayer... layers)
	{
		initialize();
		this.layers.addAll(layers);
	}
	
    private void initialize() 
    {
    	layers = new Array<ParallaxLayer>();
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
		int drawIteration = 0; 
		
		for(int i = 0; i < layers.size; i++)
		{
			layer = layers.get(i);
			origCameraPos = new Vector2(cachedPos.x,cachedPos.y);
			
			worldCamera.position.set(origCameraPos.scl(layer.getParallaxRatio()),cachedPos.z);
		    worldCamera.update();
		    batch.setProjectionMatrix(worldCamera.combined);
		    
//		    currentX = (layer.isRepeat_tileX() ? 0 : ((int)((worldCamera.position.x-worldCamera.viewportWidth*.5f*worldCamera.zoom) / layer.getWidth())) * layer.getWidth())-(Math.abs((1-layer.getParallaxRatio().x)%1)*worldCamera.viewportWidth*.5f);
		    
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
	            	   layer.draw(batch, currentX + layer.decalX, currentY); drawIteration ++ ;
	               }
	                   
	            	currentY += layer.getHeight();
	               
	               if(!layer.isRepeat_tileY())
		        	     break;
	               
	            } while(currentY < worldCamera.position.y + currentViewportHeight);
		            	
	            currentX += layer.getWidth();
	           
	            if(!layer.repeat_tileX)
	        	    break;
		            
		    } while(currentX + layer.getCurrentX() < worldCamera.position.x + currentViewportWidth);
		    
		    drawIteration = 0 ; 
		}
		
		worldCamera.combined.set(cachedProjectionView);
		worldCamera.position.set(cachedPos);
		worldCamera.zoom = cachedZoom;
		worldCamera.update();
		batch.setProjectionMatrix(worldCamera.combined);    
	}
	
	public void act(float delta) 
	{
		for(int a=0; a < layers.size ; a++) 
		{layers.get(a).act(delta);}
	}
	

}
