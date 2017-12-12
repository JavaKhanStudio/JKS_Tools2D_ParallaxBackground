package jks.tools2d.parallax.side;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;

public class SquareBackground 
{
	Color bottom_backColor ; 
	Color top_backColor; 
	
	Vector3 bottom_ColorSpeed ; 
	Color bottom_ColorObjectif ; 
	
	Vector3 top_ColorSpeed ; 
	Color top_ColorObjectif ;
	
	boolean inTransfert ; 
	float transfertTimmer ;
	float transfertStop ; 
	
	float width ; 
	float height ; 
	float posX ; 
	float posY ; 
	
	
	
	public SquareBackground(Color Top, Color bottom, float posY)
	{
		bottom_backColor = bottom ; 
		top_backColor = Top ; 
		
		this.posY = posY ; 
		posX = 0 ; 
		width = Gdx.graphics.getWidth() ; 
		height = Gdx.graphics.getHeight() - posY ; 	
	}
	
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
										(top_ColorObjectif.r-top_backColor.r) * (1/inXSecondes), 
										(top_ColorObjectif.g-top_backColor.g) * (1/inXSecondes), 
										(top_ColorObjectif.b-top_backColor.b) * (1/inXSecondes)
									) ;
		bottom_ColorSpeed = new Vector3(
										(bottom_ColorObjectif.r-bottom_backColor.r) * (1/inXSecondes), 
										(bottom_ColorObjectif.g-bottom_backColor.g) * (1/inXSecondes), 
										(bottom_ColorObjectif.b-bottom_backColor.b) * (1/inXSecondes)
										) ;
		
		inTransfert = true ; 
		transfertStop = inXSecondes ;
	}
	
	public void act(float delta)
	{
		if(inTransfert)
		{		
			top_backColor.add(top_ColorSpeed.x * delta,top_ColorSpeed.y * delta,top_ColorSpeed.z * delta,0) ;
			bottom_backColor.add(bottom_ColorSpeed.x * delta,bottom_ColorSpeed.y * delta,bottom_ColorSpeed.z * delta,0) ;
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
		render.rect(posX, posY, width, height, bottom_backColor, bottom_backColor, top_backColor, top_backColor);
	}
	
}
