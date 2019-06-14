package jks.tools2d.packing.serialisation.perf;

import static jks.tools2d.packing.serialisation.FVars_SerialisationName.formatKryo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestUtils_Perf 
{

	private static final String path = "perfTest" ;
	
	private static final String nameKryoSimple = "SimpleKryo" ; 
	private static final String nameKryoComplex = "ComplexKryo" ; 
	
	private static final String nameJsonSimple = "SimpleJson" ; 
	private static final String nameJsonComplex = "ComplexJson" ; 
	
	public static void doXSaveSimpleKryo(int x, Kryo kryo) throws FileNotFoundException
	{
		while(0 != x)
		{
			Output output = new Output(new FileOutputStream(path + "/" + nameKryoSimple + x + formatKryo)) ; 
			kryo.writeObject(output, Test_Perf_Simple.buildOne(x));
			output.close();
			x -- ; 
		}
	}
	
	public static void doXReadSimpleKryo(int x, Kryo kryo) throws FileNotFoundException
	{
		while(0 != x)
		{
			Input input = new Input(new FileInputStream(path + "/" + nameKryoSimple + x + formatKryo));
			Test_Perf_Simple model = kryo.readObject(input, Test_Perf_Simple.class);
	    	model.action();
	    	input.close();   
	    	x -- ; 
		}
		
	}
	
	public static void doXSaveComplexKryo(int x, Kryo kryo) throws FileNotFoundException
	{
		while(0 != x)
		{
			Output output = new Output(new FileOutputStream(path + "/" + nameKryoComplex + x + formatKryo)) ; 
			kryo.writeObject(output, Test_Perf_Complex.buildOne(x));
			output.close();
			x -- ; 
		}
	}
	
	public static void doXReadComplexKryo(int x, Kryo kryo) throws FileNotFoundException
	{
		while(0 != x)
		{
			Input input = new Input(new FileInputStream(path + "/" + nameKryoComplex + x + formatKryo));
			Test_Perf_Complex model = kryo.readObject(input, Test_Perf_Complex.class);
	    	model.action();
	    	input.close();  
	    	x -- ; 
		}
	}
	
	public static void doXSaveSimpleJson(int x, ObjectMapper mapper)
	{
		
	}
	
	public static void doXReadSimpleJson(int x, ObjectMapper mapper)
	{
		
	}
	
	public static void doXSaveComplexJson(int x, ObjectMapper mapper)
	{
		
	}
	
	public static void doXReadComplexJson(int x, ObjectMapper mapper)
	{
		
	}
	
	public static void creatDirectory()
	{
		new File(path).mkdirs();
	}
	
	public static void purge()
	{
		try 
		{
			FileUtils.deleteDirectory(new File(path));
		}
		catch (IOException e) 
		{
			e.printStackTrace();
		}
	}
	
	
}
