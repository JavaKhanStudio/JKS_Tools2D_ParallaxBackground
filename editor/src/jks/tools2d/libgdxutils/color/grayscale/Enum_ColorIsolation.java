package jks.tools2d.libgdxutils.color.grayscale;


public enum Enum_ColorIsolation
{
    RED,
    GREEN,
    BLUE,
    GRAY,
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
    	if(value == -256)
    		return -256; 
    	
    	
        final int r = (value>>24)&0xff ;
        final int g = (value>>16)&0xff ;
        final int b = (value>>8)&0xff ;
        final int a = value&0xff ;

    
        final int avg = (r + g + b)/3 ;

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
        }

        return (avg<<24) | (avg<<16) | (avg<<8) | a ;
    }
//    public int buildFromInteger(int value)
//    {
//    	if(value == -256)
//    		return -256; 
//    	
//    	
//        final int a = (value>>24)&0xff ;
//        final int r = (value>>16)&0xff ;
//        final int g = (value>>8)&0xff ;
//        final int b = value&0xff ;
//
//        System.out.println(a);
//        System.out.println(r);
//        System.out.println(g);
//        System.out.println(b);
//        final int avg = (r + g + b)/3 ;
//
//        switch (this)
//        {
//            case RED:
//            {
//                if(r < g || r < b)
//                    break ;
//
//                if(r > avg * percentFull)
//                    return value ;
//                else if(r > avg * percentHalf)
//                    return (a<<24) | (avg<<16) | ((int)((avg * halfPower))<<8) | (int)(avg * halfPower) ;
//                else if(r > avg * percentTier)
//                    return (a<<24) | (avg<<16) | ((int)((avg * tierPower))<<8) | (int)(avg * tierPower) ;
//
//                break ;
//            }
//            case GREEN:
//            {
//                if(g < b || g < r)
//                    break ;
//
//                if(g > avg * percentFullGreen)
//                    return value ;
//                else if(g > avg * percentHalfGreen)
//                    return (a<<24) | ((int)((avg * halfPower))<<16) | (avg<<8) | (int)(avg * halfPower)  ;
//                else if(g > avg * percentTierGreen)
//                    return (a<<24) | ((int)((avg * tierPower))<<16) | (avg<<8) | (int)(avg * tierPower) ;
//
//                break ;
//            }
//            case BLUE:
//            {
//                if(b < g || b < r)
//                    break ;
//
//                if(b > avg * percentFull)
//                    return value ;
//                else if(b > avg * percentHalf)
//                    return (a<<24) | ((int)((avg * halfPower))<<16) | ((int)((avg * halfPower))<<8) | avg ;
//                else if(b > avg * percentTier)
//                    return (a<<24) | ((int)((avg * tierPower))<<16) | ((int)((avg * tierPower))<<8) | avg ;
//
//                break ;
//            }
//        }
//
//        return (avg<<24) | (avg<<16) | (avg<<8) | avg ;
//    }


}
