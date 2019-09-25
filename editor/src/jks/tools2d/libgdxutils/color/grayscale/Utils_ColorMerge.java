package jks.tools2d.libgdxutils.color.grayscale;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Pixmap.Format;

public class Utils_ColorMerge 
{
	
	public enum Direction 
	{
		FromTop,FromBottom ; 
	}


	private static float baseLevel = 0.7f; 
	
	public static Color evalColor(Pixmap pixmap, float scanPercent, Direction direction)
	{
		int totalRed = 0 ; 
		int totalGreen = 0 ; 
		int totalBlue = 0 ; 
		
		int count = 0 ; 
		switch(direction)
		{
			case FromTop : 
			{
				float nbPercentToCheck = scanPercent/100 * pixmap.getHeight(); 
				for (int x = 0; x < pixmap.getWidth(); x++) 
			    {
			        for (int y = 0; y < nbPercentToCheck; y++) 
			        {
			        	int pixColor = pixmap.getPixel(x, y) ; 
			        	totalRed += (pixColor>>24)&0xff ;
			        	totalGreen += (pixColor>>16)&0xff ;
			        	totalBlue += (pixColor>>8)&0xff ;
			        	count ++ ; 
			        }
			    }
			}
			case FromBottom :
			{
				float nbPercentToCheck = scanPercent/100 * pixmap.getHeight(); 
				for (int x = 0; x < pixmap.getWidth(); x++) 
			    {
			        for (int y = pixmap.getHeight(); y >  pixmap.getHeight() - nbPercentToCheck; y--) 
			        {
			        	int pixColor = pixmap.getPixel(x, y) ; 
			        	totalRed += (pixColor>>24)&0xff ;
			        	totalGreen += (pixColor>>16)&0xff ;
			        	totalBlue += (pixColor>>8)&0xff ;
			        	count ++ ; 
			        }
			    }
			}
		}
		
		
		
		totalRed /= count ; 
		totalGreen /= count ; 
		totalBlue /= count ; 

		System.out.println(totalRed + " " + totalGreen + " " + totalBlue);
		return new Color((totalRed<<24) | (totalGreen<<16) | (totalBlue<<8) | 255) ;
	}

	
	public static Texture buildTexture(Pixmap pixmapRef, Color top, Color bottom) 
	{
		Pixmap newPixmap = new Pixmap(pixmapRef.getWidth(),pixmapRef.getHeight(), Format.RGB888) ;
	    
		for (int x = 0; x < pixmapRef.getWidth(); x++) 
	    {
	        for (int y = 0; y < pixmapRef.getHeight(); y++) 
	        {
	        	newPixmap.drawPixel(x, y, 
	        			Utils_ColorMerge.mergeColorTopAndBot(pixmapRef.getPixel(x, y),
	        					((float)y/pixmapRef.getHeight() * 100), 
	        					top.toIntBits(),bottom.toIntBits()));
	        	
	  
	        }
	    }
		  
		return new Texture(newPixmap);
	}
	
	
	public static int mergeColorTopAndBot(int pixelColor, float percent, int topColor, int bottomColor)
	{
		
		if(pixelColor == -256)
    		return -256; 
		
		int r = (pixelColor>>24)&0xff ;
        int g = (pixelColor>>16)&0xff ;
        int b = (pixelColor>>8)&0xff ;
        final int a = pixelColor&0xff ;
        
        //ABGR
        final float r_top = topColor&0xff ;
        final float g_top = (topColor>>8)&0xff ;
        final float b_top = (topColor>>16)&0xff ;
        
        final int r_bot = bottomColor&0xff ;
        final int g_bot = (bottomColor>>8)&0xff ;
        final int b_bot = (bottomColor>>16)&0xff ;

        
        float percentFromTop  = (1 - (percent/100f)) * baseLevel ;
        float percentFromBottom  = (percent/100f) * baseLevel ;


        float avg = 1 + percentFromTop + percentFromBottom ; 
        r = (int)((r + (r_top * percentFromTop) + (r_bot * percentFromBottom))/avg) ;
        g = (int)((g + (g_top * percentFromTop) + (g_bot * percentFromBottom))/avg);
        b = (int)((b + (b_top * percentFromTop) + (b_bot * percentFromBottom))/avg) ;
     
        if(r > 255)
        	r = 255 ;
        if(g > 255)
        	g = 255 ;
        if(b > 255)
        	b = 255 ;
        
        return (r<<24) | (g<<16) | (b<<8) | a ;
	}
	
}
