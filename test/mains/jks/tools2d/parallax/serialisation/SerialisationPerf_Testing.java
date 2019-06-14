package jks.tools2d.parallax.serialisation;

import static jks.tools2d.packing.serialisation.perf.TestUtils_Perf.*;
import static jks.tools2d.parallax.serialisation.TestNameEnum.* ; 


import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.esotericsoftware.kryo.Kryo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import jks.tools.GlobalTimmer;
import jks.tools2d.packing.serialisation.perf.Test_Perf_Complex;
import jks.tools2d.packing.serialisation.perf.Test_Perf_Complex_Serializer;
import jks.tools2d.packing.serialisation.perf.Test_Perf_Simple;
import jks.tools2d.packing.serialisation.perf.Test_Perf_Simple_Serializer;

public class SerialisationPerf_Testing 
{

	// LONG TEST
	//private static List<Integer> nbTraitementTest = Arrays.asList(1,100,1000,10000);  
	// SHORT TEST
	private static List<Integer> nbTraitementTest = Arrays.asList(1,10,100);  
	
	//private static TestNameEnum[] testToDo = TestNameEnum.values() ; 
	private static List<TestNameEnum> testToDo = Arrays.asList(kryoS,kryoSR); 
	
	
	private static HashMap<String,ArrayList<Long>> map = new HashMap<String,ArrayList<Long>>() ; 
	private static final String key = "key" ; 
	private static final int doEachTestXTime = 5 ; 
	
	static public void main (String[] args) throws Exception
    {
		System.out.println("- Preparation du test -");
		Kryo kryo = prepareKryo() ; 
		ObjectMapper mapper = prepareJson() ;
		
		GlobalTimmer.registerTime(key);

		for(Integer value : nbTraitementTest)
			prepareTest(value) ; 
		
		System.out.println("-- Execution des tests --");
		for(Integer value : nbTraitementTest)
			doTestXTime(doEachTestXTime,value, kryo, mapper) ; 
		
		lectureResultat() ; 
		
		System.out.println("Fin du test");
    }
	
	public static void prepareTest(int x)
	{
		for(TestNameEnum name : testToDo)
		{
			map.put("" + name + x, new ArrayList<Long>()) ; 
		}

	}
	
	
	public static void doTestXTime(int doTestXTime,int testXValue, Kryo kryo,ObjectMapper mapper)
	{
		System.out.println("-- Execution des avec : " + testXValue + " valeurs --");
		
		try 
		{
			for(int a = 0 ; a < doTestXTime ; a ++)
			{
				creatDirectory() ; 
				GlobalTimmer.resetTimmer(key) ; 
				for(TestNameEnum name : testToDo)
				{
					name.doXAction(testXValue, kryo, mapper); 
					map.get("" + name + testXValue).add(GlobalTimmer.getElapse(key, true)) ; 
				}
				
				/*
				doXSaveSimpleKryo(testXValue, kryo) ; 
				map.get("" + kryoS + testXValue).add(GlobalTimmer.getElapse(key, true)) ; 
				
				doXReadSimpleKryo(testXValue, kryo) ;
				map.get("" + kryoSR + testXValue).add(GlobalTimmer.getElapse(key, true)) ; 
				
				doXSaveComplexKryo(testXValue, kryo) ;
				map.get("" + kryoC + testXValue).add(GlobalTimmer.getElapse(key, true)) ; 
				
				doXReadComplexKryo(testXValue, kryo) ;
				map.get("" + kryoCR + testXValue).add(GlobalTimmer.getElapse(key, true)) ; 
			
				doXSaveSimpleJson(testXValue, mapper) ; 
				map.get("" + jackS + testXValue).add(GlobalTimmer.getElapse(key, true)) ; 
				
				doXReadSimpleJson(testXValue, mapper) ; 
				map.get("" + jackSR + testXValue).add(GlobalTimmer.getElapse(key, true)) ;
				
				doXSaveComplexJson(testXValue, mapper) ; 
				map.get("" + jackC + testXValue).add(GlobalTimmer.getElapse(key, true)) ; 
				
				doXReadComplexJson(testXValue, mapper) ; 
				map.get("" + jackCR + testXValue).add(GlobalTimmer.getElapse(key, true)) ;
				purge() ; 
				*/
			}
		} 
		catch (FileNotFoundException e) 
		{e.printStackTrace();} 
		
	}
	
	public static Kryo prepareKryo()
    {
    	Kryo kryo = new Kryo();
    	kryo.register(ArrayList.class) ; 
	    kryo.register(Test_Perf_Simple.class, new Test_Perf_Simple_Serializer());
	    kryo.register(Test_Perf_Complex.class, new Test_Perf_Complex_Serializer());
	    return kryo ;
    }
	
	private static ObjectMapper prepareJson() 
	{
		ObjectMapper objectMapper = new ObjectMapper() ; 
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false) ; 
		return objectMapper ; 
	}
	
	private static final boolean readAll = false ; 
	
	private static void lectureResultat()
	{
		System.out.println("--- Lecture des resultat ---");
		long time = 0 ; 
		ArrayList<Long> mapValue ; 
		for(Integer testNumber : nbTraitementTest)
			for(TestNameEnum name : testToDo)
			{
				time = 0 ; 
				mapValue = map.get("" + name + testNumber) ; 
				for(Long temps : mapValue)
				{
					if(readAll)
						System.out.println("Temps pour test individuel: " + name + testNumber + " : " + temps);
				
					time += temps ; 
				}
				
				if(mapValue.size() > 0)
					System.out.println("-- Temps pour test : " + name + testNumber + " : " + (time/mapValue.size() + "ms --" ));
			}
	}
	
}
