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
	
	public Enum_TransfertType transfertType ;
	public ArrayList<ParallaxLayer> transferLayers;
	public boolean inTransfer ; 

	private boolean repeatOnX,repeatOnY  ; 

	private float drawingHeight  ;
	
	float newLayer_transperencyLvl = 0 ;
	float oldLayer_transperencytLvl = 1 ; 
	float newLayer_transfertSpeed ; 
	float oldLayer_transfertSpeed ; 
	private int buffer = 1 ;
	
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
		transfertType = Enum_TransfertType.EACH_FRAME ;
		List<ParallaxLayer> newLayers ;
		
		newLayers = pageModel.getDrawing() ; 
		
		newLayer_transfertSpeed = 1/inXSecondes  ;
		oldLayer_transfertSpeed = 1/(inXSecondes) ; 
		
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
		
		transfertType = Enum_TransfertType.COLOR ;

		newLayer_transfertSpeed = 1/inXSecondes ;
		// TODO CHECK 
		transferLayers = layers ;
		set_newLayer_Color(color);
	}
	
	public void draw(OrthographicCamera worldCamera, Batch batch)
	{
		
		if(repeatOnY && repeatOnX)
			drawOnXY(worldCamera,batch) ; 
		else if(repeatOnY)
			drawOnY(worldCamera,batch) ; 
		else if(repeatOnX)
			drawOnX(worldCamera,batch) ; 
		
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
		}	
	}


	public void drawOnX(OrthographicCamera worldCamera, Batch batch)
	{
		ParallaxLayer layer ; 

		int transferPosition = 0; 
		
		if(transferLayers.size() > layers.size())
		{
			transferPosition = transferLayers.size() - layers.size(); 
			batch.setColor(1,1,1,newLayer_transperencyLvl);	
			for(int i = 0; i <= transferPosition; i++)
			{
				layer = transferLayers.get(i);
				if(layer.getTexRegion().size() == 1)
					drawLayoutOnX(layer, worldCamera, batch) ;
				else
					drawMultiLayoutOnX(layer, worldCamera, batch) ;
			}	
		}
		
		for(int i = 0; i < layers.size(); i++)
		{	
			batch.setColor(1,1,1,oldLayer_transperencytLvl);	
			layer = layers.get(i);
			drawLayoutOnX(layer, worldCamera, batch) ;
			
			if(transferLayers.size() > 0)
			{
				batch.setColor(1,1,1,newLayer_transperencyLvl);	
				layer = transferLayers.get(i + transferPosition);
				drawLayoutOnX(layer, worldCamera, batch) ;
			}
		}					  
	}
	
	private void drawLayoutOnX(ParallaxLayer layer,OrthographicCamera worldCamera,Batch batch)
	{
		layer.draw(batch, layer.currentDistanceX, drawingHeight + layer.currentDistanceY); 
		
		for(float a = layer.getTotalWidth(); a < worldCamera.viewportWidth - layer.currentDistanceX + (buffer * layer.getTotalWidth()); a+= layer.getTotalWidth())
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
	}
	
	private void drawMultiLayoutOnX(ParallaxLayer layer,OrthographicCamera worldCamera,Batch batch)
	{
		layer.draw(batch, layer.currentDistanceX, drawingHeight + layer.currentDistanceY); 
		
		for(float a = layer.getTotalWidth(); a < worldCamera.viewportWidth - layer.currentDistanceX + (buffer * layer.getTotalWidth()); a+= layer.getTotalWidth())
			layer.draw(batch, layer.currentDistanceX + a, drawingHeight + layer.currentDistanceY); 
		
		for(float a = 1 ; layer.currentDistanceX - a * layer.getTotalWidth()  > -(layer.getWidth()) ; a++)
			layer.draw(batch, layer.currentDistanceX - (a * layer.getTotalWidth()), drawingHeight + layer.currentDistanceY); 	
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
    		    
		}	
	}
	

	
	public void compute_Color_Transfert()
	{
		newLayer_transfertColor.a = newLayer_transperencyLvl;	
		
		if(newLayer_transperencyLvl >= transparencyPoint)
		{
			oldLayer_transfertColor.a = 1 - oldLayer_transperencytLvl ;
		}			
	}
	
	public void resetTransfert()
	{
		transferLayers = new ArrayList<ParallaxLayer>() ;
		
		oldLayer_transfertColor.a = 1 ; 
		newLayer_transfertColor.a = 0 ; 
		newLayer_transperencyLvl = 0 ; 
		oldLayer_transperencytLvl = 1 ; 
	}
	
	public void resetPositions()
	{
		layers.stream().forEach(x -> x.resetPosition()) ; 
		
	}

	
	public void act(float delta,float speedX,float speedY) 
	{
		layers.stream().forEach(x -> x.act(delta,speedX,speedY,repeatOnX,repeatOnY));

		if(!Enum_TransfertType.NONE.equals(transfertType))
		{
	    	compute_Color_Transfert() ;
			newLayer_transperencyLvl += delta * newLayer_transfertSpeed ; 
			oldLayer_transperencytLvl -= delta * oldLayer_transfertSpeed ; 
			
			if(newLayer_transperencyLvl > 1)
			{
				for(int a=0; a < layers.size() ; a++) 
				{transferLayers.get(a).setCurrentDistanceX(layers.get(a).getCurrentDistanceX());}
				
				layers = transferLayers ; 
				oldLayer_transfertColor = newLayer_transfertColor.cpy(); 
				transfertType = Enum_TransfertType.NONE ; 
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
	
	public boolean isRepeatOnX()
	{return repeatOnX;}

	public void setRepeatOnX(boolean repeatOnX) 
	{this.repeatOnX = repeatOnX;}

	public boolean isRepeatOnY() 
	{return repeatOnY;}

	public void setRepeatOnY(boolean repeatOnY) 
	{this.repeatOnY = repeatOnY;}

}