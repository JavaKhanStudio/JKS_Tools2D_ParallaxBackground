package jks.tools2d.libgdxutils.color.grayscale;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;

public class ColorExtractor 
{
	int red ;
	int green ;
	int blue ; 
	
	int nbColorAdded ; 
	
	
	public ColorExtractor(int pixColor)
	{
		addColor(pixColor) ; 
	}
	
	public ColorExtractor(Pixmap pixmap,float scanPercent, Direction direction) 
	{
		switch(direction)
		{
			case FromTop : 
			{
				float nbPercentToCheck = scanPercent/100 * pixmap.getHeight(); 
				for (int x = 0; x < pixmap.getWidth(); x++) 
			    {
			        for (int y = 0; y < nbPercentToCheck; y++) 
			        {
			        	addByPower(pixmap.getPixel(x, y),PowerType.ByHalfIsFull) ;
			        }
			    }
				
				finalize(PowerType.ByHalfIsFull) ; 
				break ; 
			}
			case FromBottom :
			{
				float nbPercentToCheck = scanPercent/100 * pixmap.getHeight(); 
				for (int x = 0; x < pixmap.getWidth(); x++) 
			    {
			        for (int y = pixmap.getHeight(); y >  pixmap.getHeight() - nbPercentToCheck; y--) 
			        {
			        	addByPower(pixmap.getPixel(x, y),PowerType.ByHalfIsFull) ;
			        }
			    }
				break ; 
			}

		}
	}
	
	final int margin = 10 ; 
	
	private void finalize(PowerType powerType) 
	{
		switch(powerType)
		{
			case ByHalfIsFull : 
			{
//				if(red > blue + margin &&)
				break ; 
			}
			case None :
			default : 
				break ; 
		}
	}

	final int half = 126 ; 
	
	private void addByPower(int pixcolor, PowerType powerType)
	{
    	int partiallRed = (pixcolor>>24)&0xff ; 
		int partialGreen = (pixcolor>>16)&0xff ; 
		int partialBlue = (pixcolor>>8)&0xff ; 
    	
		switch(powerType)
		{
			case ByHalfIsFull :
			{
				if(partiallRed > half)
					red += 255 ;
				else 
					red += partiallRed ; 
				
				if(partialGreen > half)
					green += 255;
				else 
					green += partialGreen ; 
					
				if(partialBlue > half)
					blue += 255 ; 
				else 
					blue += partialBlue ;
				break ;
			}
			case None : 
			{
				red += partiallRed ;
		    	green += partialGreen;
		    	blue += partialBlue ;
		    	break ;
			}
		}
    	
    	nbColorAdded ++ ; 
	}
	
	private void addBylist()
	{
		
	}
	
	private void addColor(int pixColor) 
	{
		red += (pixColor>>24)&0xff ;
		green += (pixColor>>16)&0xff ;
		blue += (pixColor>>8)&0xff ;
	}
	
	enum PowerType
	{
		ByHalfIsFull, None
	}
	
	public Color getColor()
	{
		red /= nbColorAdded ; 
		green /= nbColorAdded ; 
		blue /= nbColorAdded ; 

		return new Color((red<<24) | (green<<16) | (blue<<8) | 255) ;
	}
}
