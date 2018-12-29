package jks.tools2d.parallax.mains;

import java.util.HashMap;

public class GlobalTimmer
{

	public static HashMap<String,Long> timeHolder ;
	
	
	public static void registerTime(String key)
	{
		if(timeHolder == null)
		{timeHolder = new  HashMap<String,Long>() ;}
		
		timeHolder.put(key, System.currentTimeMillis()) ;
	}
	
	public static Long getElapse(String key, String state)
	{return getElapse(key,state, false);}
	
	public static Long getElapse(String key, String state, boolean reset)
	{
		long finishTime = System.currentTimeMillis();
		long took = (finishTime-timeHolder.get(key)) ; 
		System.out.println(key + " at state " + state + " took: " + took+ " ms");
		
		if(reset)
			timeHolder.put(key, finishTime) ;
		
		return took ; 
	}
	
	
}
