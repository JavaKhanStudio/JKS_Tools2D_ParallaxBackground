package jks.tools2d.libgdxutils.color.grayscale;

import com.badlogic.gdx.graphics.Pixmap;

import jks.tools2d.parallax.editor.vue.edition.utils.Utils_Texture;

public enum Enum_ColorIsolation
{
    RED,
    GREEN,
    BLUE,
    GRAY,
    GRAY_DARK
    ;

    static final float percentFull = 1.1f ;
    static final float percentHalf = 1.15f ;
    static final float percentTier = 1.2f ;

    static final float percentFullGreen = 1.2f ;
    static final float percentHalfGreen = 1.25f ;
    static final float percentTierGreen = 1.35f ;

    static final float halfPower = 0.98f ;
    static final float tierPower = 0.95f ;
    // R G B
    public int buildFromInteger(int value)
    {
    		
    	final int a = value&0xff ;

    	if(a < 200)
    		return (0<<24) | (0<<16) | (0<<8) | 0 ; 
    	
    	
        final int r = (value>>24)&0xff ;
        final int g = (value>>16)&0xff ;
        final int b = (value>>8)&0xff ;
       

    
        int avg = (r + g + b)/3 ;

        switch (this)
        {
            case RED:
            {
                if(r < g || r < b)
                    break ;

                if(r > avg * percentFull)
                    return value ;
                else if(r > avg * percentHalf)
                    return (avg<<24) | ((int)((avg * halfPower))<<16) | (int)(avg * halfPower)<<8 | a ;
                else if(r > avg * percentTier)
                    return (avg<<24) | ((int)((avg * tierPower))<<16) | (int)(avg * tierPower)<<8 | a ;

                break ;
            }
            case GREEN:
            {
                if(g < b || g < r)
                    break ;

                if(g > avg * percentFullGreen)
                    return value ;
                else if(g > avg * percentHalfGreen)
                    return ((int)((avg * halfPower))<<24) | (avg<<16) | (int)(avg * halfPower)<<8 | a ;
                else if(g > avg * percentTierGreen)
                    return ((int)((avg * tierPower))<<16) | (avg<<16) | (int)(avg * tierPower)<<8 | a ;

                break ;
            }
            case BLUE:
            {
                if(b < g || b < r)
                    break ;

                if(b > avg * percentFull)
                    return value ;
                else if(b > avg * percentHalf)
                    return ((int)((avg * halfPower))<<24) | ((int)((avg * halfPower))<<16) | avg<<8 | a ;
                else if(b > avg * percentTier)
                    return ((int)((avg * tierPower))<<24) | ((int)((avg * tierPower))<<16) | avg<<8 | a ;

                break ;
            }
            case GRAY_DARK :
            	avg /= 3 ;
            	if(avg < 0)
            		avg = 0 ; 
            	
            case GRAY :
        	default :      		 
        }
        
        return (avg<<24) | (avg<<16) | (avg<<8) | a ;
    }
    
    public Pixmap rebuildPixmap(Pixmap sourcePixmap)
    {
    	
    	Pixmap grayPixmap = Utils_Texture.buildPixmapCopy(sourcePixmap) ;
    	
    	for (int x = 0; x < sourcePixmap.getWidth(); x++) 
	    {
	        for (int y = 0; y < sourcePixmap.getHeight(); y++) 
	        {
	        	grayPixmap.drawPixel(x, y,this.buildFromInteger(sourcePixmap.getPixel(x, y))) ; 
	        }
	    }
    	
    	return grayPixmap ; 
    }
    
    public int buildFromInteger2(int value)
    {
    	if(value == -256)
    		return -256; 
    	
    	
        final int r = (value>>24)&0xff ;
        final int g = (value>>16)&0xff ;
        final int b = (value>>8)&0xff ;
        final int a = value&0xff ;
        
        final int avg = (r + g + b)/3 ;

//        int nightmare = (76 * r + 150 * g + 29 * b + a);
//        int nightmare2 = (int)((76 + avg) + (150 + avg) + (29 + avg));
        int gray =  (76 * r + 150 * g + 29 * b) >> 8;
        //return (avg<<24) | (avg<<16) | (avg<<8) | a ;
        return (gray<<24) | (gray<<16) | (gray<<8) | a ;
    }
 
}
