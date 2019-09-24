package jks.tools2d.libgdxutils.color.grayscale;

public class Utils_ColorMerge 
{
	
	private static float baseLevel = 0.5f; 
	
	
	public static int mergeColor(int pixelColor, float percent, int topColor, int bottomColor)
	{
		int r = (pixelColor>>24)&0xff ;
        int g = (pixelColor>>16)&0xff ;
        int b = (pixelColor>>8)&0xff ;
        final int a = pixelColor&0xff ;
        
        final float r_top = (topColor>>24)&0xff ;
        final float g_top = (topColor>>16)&0xff ;
        final float b_top = (topColor>>8)&0xff ;
        
        final int r_bot = (bottomColor>>24)&0xff ;
        final int g_bot = (bottomColor>>16)&0xff ;
        final int b_bot = (bottomColor>>8)&0xff ;

        
        float percentFromTop  = (1 - (percent/100f)) * baseLevel ;
        float percentFromBottom  = (percent/100f) * baseLevel ;

        
//        r = r/3 + (int)(r_top * percentFromTop)/3 + (int)(r_bot + percentFromBottom)/3  ;
//        g = g/3 + (int)(g_top * percentFromTop)/3 + (int)(g_bot + percentFromBottom)/3  ;
//        b = b/3 + (int)(b_top * percentFromTop)/3 + (int)(b_bot + percentFromBottom)/3  ;
        
        
//        r = r + (int)((r_top * percentFromTop)/3) + (int)((r_bot * percentFromBottom)/3) ;
//        g = g + (int)((g_top * percentFromTop)/3) + (int)((g_bot * percentFromBottom)/3) ;
//        b = b + (int)((b_top * percentFromTop)/3) + (int)((b_bot * percentFromBottom)/3) ;

//        r = r + (int)((r_top * percentFromTop)/2) ;
//        g = g + (int)((g_top * percentFromTop)/2) ;
//        b = b + (int)((b_top * percentFromTop)/2) ;
        
	      r = r + (int)((r_bot * percentFromBottom)/2) ;
	      g = g + (int)((g_bot * percentFromBottom)/2) ;
	      b = b + (int)((b_bot * percentFromBottom)/2) ;
        
        if(r > 255)
        	r = 255 ;
        if(g > 255)
        	g = 255 ;
        if(b > 255)
        	b = 255 ;
        
        return (r<<24) | (g<<16) | (b<<8) | a ;
	}
	
	public static int mergeColor2(int pixelColor, float percent, int topColor, int bottomColor)
	{
		int r = (pixelColor>>24)&0xff ;
        int g = (pixelColor>>16)&0xff ;
        int b = (pixelColor>>8)&0xff ;
        final int a = pixelColor&0xff ;
        
        final float r_top = (topColor>>24)&0xff ;
        final float g_top = (topColor>>16)&0xff ;
        final float b_top = (topColor>>8)&0xff ;
        
        final int r_bot = (bottomColor>>24)&0xff ;
        final int g_bot = (bottomColor>>16)&0xff ;
        final int b_bot = (bottomColor>>8)&0xff ;

        
        float percentFromTop  = (1 - (percent/100f)) * baseLevel ;
        float percentFromBottom  = (percent/100f) * baseLevel ;

        
//        r = r/3 + (int)(r_top * percentFromTop)/3 + (int)(r_bot + percentFromBottom)/3  ;
//        g = g/3 + (int)(g_top * percentFromTop)/3 + (int)(g_bot + percentFromBottom)/3  ;
//        b = b/3 + (int)(b_top * percentFromTop)/3 + (int)(b_bot + percentFromBottom)/3  ;
        
        
//        r = r + (int)((r_top * percentFromTop)/3) + (int)((r_bot * percentFromBottom)/3) ;
//        g = g + (int)((g_top * percentFromTop)/3) + (int)((g_bot * percentFromBottom)/3) ;
//        b = b + (int)((b_top * percentFromTop)/3) + (int)((b_bot * percentFromBottom)/3) ;

//        r = r + (int)((r_top * percentFromTop)/2) ;
//        g = g + (int)((g_top * percentFromTop)/2) ;
//        b = b + (int)((b_top * percentFromTop)/2) ;
        	
//          float avg = 1 + percentFromTop + percentFromBottom ; 
//	      r = (int)((r + (r_bot * percentFromBottom))/avg) ;
//	      g = (int)((g + (g_bot * percentFromBottom))/avg);
//	      b = (int)((b + (b_bot * percentFromBottom))/avg) ;
	      
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
