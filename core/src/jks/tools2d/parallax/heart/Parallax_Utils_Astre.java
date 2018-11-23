package jks.tools2d.parallax.heart;

import jks.tools2d.parallax.side.Enum_AstreType;

public class Parallax_Utils_Astre 
{

	static void drawAstre(float delta)
	{
		if(Parallax_Heart.astres != null)
		{
			Parallax_Heart.batch.setProjectionMatrix(Parallax_Heart.staticCamera.combined);
			Parallax_Heart.astres.act(delta);
			Parallax_Heart.astres.draw(Parallax_Heart.batch);
		}
	}

	public static void startAstre(Enum_AstreType current)
	{
		switch(current)
		{
			case SUN :
			 	Parallax_Heart.astres.startAstre(Parallax_Heart.transfertTime * 3.7f, true); break ; 
			case MOON :
				Parallax_Heart.astres.startAstre(Parallax_Heart.transfertTime * 3.0f, false); break ;
			default:
				break; 
		}	
	}


}
