package jks.tools2d.parallax.heart;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;

import jks.tools2d.parallax.ParallaxPage;
import jks.tools2d.parallax.side.Enum_AstreType;

public class Parralax_Utils_Astre 
{

	static void drawAstre(float delta)
	{
		if(Parrallax_Heart.astres != null)
		{
			Parrallax_Heart.batch.setProjectionMatrix(Parrallax_Heart.staticCamera.combined);
			Parrallax_Heart.astres.act(delta);
			Parrallax_Heart.astres.draw(Parrallax_Heart.batch);
		}
	}

	public static void startAstre(Enum_AstreType current)
	{
		switch(current)
		{
			case SUN :
			 	Parrallax_Heart.astres.startAstre(Parrallax_Heart.transfertTime * 3.7f, true); break ; 
			case MOON :
				Parrallax_Heart.astres.startAstre(Parrallax_Heart.transfertTime * 3.0f, false); break ;
			default:
				break; 
		}	
	}


}
