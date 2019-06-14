package jks.tools2d.parallax.side;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;

public class SquareBackground 
{
	public Color topColor; 
	public Color bottomColor ; 
	
	Vector3 bottom_ColorSpeed ; 
	Color bottom_ColorObjectif ; 
	
	Vector3 top_ColorSpeed ; 
	Color top_ColorObjectif ;
	
	boolean inTransfert ; 
	public boolean visible = true; 
	float transfertTimmer ;
	float transfertStop ; 
	
	float width ; 
	float height ; 
	float posX ; 
	float posY ; 
	
	
	
	public SquareBackground(Color Top, Color bottom, float posY,boolean top)
	{
		topColor = Top ; 
		bottomColor = bottom ; 
		
		setHeight(top,posY) ;
		posX = 0 ; 
		width = Gdx.graphics.getWidth() ; 
		
	}
	
	public void setHeight(boolean top,float posY)
	{
		if(top)
		{
			height = Gdx.graphics.getHeight() - posY ; 	
			this.posY = posY ; 
		}
		else
		{
			this.height = Gdx.graphics.getHeight() - posY ; 	
			this.posY = 0 ; 
		}	
	}
	
	public float getHeight()
	{return height ;}

	
	public void transfertInto(Color top_transfert, Color bottom_transfert, float inXSecondes)
	{
		if(top_transfert == null || bottom_transfert == null)
		{
			inTransfert = false ; 
			return ; 
		}
		
		top_ColorObjectif = top_transfert ;
		bottom_ColorObjectif = bottom_transfert ;
		
		
		top_ColorSpeed = new Vector3(
										(top_ColorObjectif.r-topColor.r) * (1/inXSecondes), 
										(top_ColorObjectif.g-topColor.g) * (1/inXSecondes), 
										(top_ColorObjectif.b-topColor.b) * (1/inXSecondes)
									) ;
		bottom_ColorSpeed = new Vector3(
										(bottom_ColorObjectif.r-bottomColor.r) * (1/inXSecondes), 
										(bottom_ColorObjectif.g-bottomColor.g) * (1/inXSecondes), 
										(bottom_ColorObjectif.b-bottomColor.b) * (1/inXSecondes)
										) ;
		
		inTransfert = true ; 
		transfertStop = inXSecondes ;
	}
	
	public void act(float delta)
	{
		if(inTransfert)
		{		
			topColor.add(top_ColorSpeed.x * delta,top_ColorSpeed.y * delta,top_ColorSpeed.z * delta,0) ;
			bottomColor.add(bottom_ColorSpeed.x * delta,bottom_ColorSpeed.y * delta,bottom_ColorSpeed.z * delta,0) ;
			transfertTimmer += delta ; 

			if(transfertTimmer >= transfertStop)
				resetTransfert() ; 		
		}	
	}
	
	public void resetTransfert()
	{
		inTransfert = false ; 		
		transfertTimmer = 0 ; 
	}
	
	public void draw(ShapeRenderer render)
	{
		if(visible)
			render.rect(posX, posY, width, height, bottomColor, bottomColor, topColor, topColor);
	}
	
}