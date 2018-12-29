package jks.tools2d.parallax.heart;

import jks.tools2d.parallax.side.Enum_AstreType;
import jks.tools2d.parallax.side.SolarAstre;

import static jks.tools2d.parallax.heart.Parallax_Heart.*;

public class Parallax_Utils_Astre 
{

	static void drawAstre()
	{
		if(astres != null)
		{
			batch.setProjectionMatrix(Parallax_Heart.staticCamera.combined);
			astres.draw(Parallax_Heart.batch);
		}
	}

	public static void startAstre(Enum_AstreType current, float inXSecondes)
	{
		if(astres == null)
		{
			astres = new SolarAstre() ; 
		}
		
		switch(current)
		{
			case SUN :
			 	astres.startAstre(inXSecondes, true); break ; 
			case MOON :
				astres.startAstre(inXSecondes, false); break ;
			default:
				break; 
		}	
	}


}
