package jks.tools2d.packing.serialisation;

import static jks.tools2d.packing.serialisation.FVars_SerialisationName.colorName;

import java.io.File;
import java.io.IOException;

import com.badlogic.gdx.graphics.Color;
import com.fasterxml.jackson.databind.ObjectMapper;

import jks.tools2d.parallax.editor.gvars.FVars_Extensions;
import jks.tools2d.parallax.gvars.FVars_Path; 

public class TestUtils_JSON 
{

	static public void testColor(ObjectMapper mapper)
    {
    	Color color = new Color();
    	color.a = 0.5f ; 
    	color.b = 0.6f ;
    	color.g = 0.7f ;
    	color.r = 0.8f ;
    	
    	String path = FVars_Path.path + colorName + FVars_Extensions.JSON ; 
    	
    	try 
    	{
			mapper.writeValue(new File(path), color);
			Color colorLoaded = mapper.readValue(new File(path), Color.class) ; 
			System.out.println("testColor work " + colorLoaded.a + " " + colorLoaded.b + " " + colorLoaded.g + " " + colorLoaded.r + " " );
    	} 
    	catch (IOException e) 
    	{	
			e.printStackTrace();
		}
    }
	
	static public void testResilience(ObjectMapper mapper) 
	{
		Test_Bake valueBake = new Test_Bake() ; 
		valueBake.valueC1 = Color.BLACK ; valueBake.valueC2 = Color.BLUE ;
		valueBake.valueI1 = 1 ; valueBake.valueI2 = 2 ; 
		valueBake.valueS1 = "Test1" ; valueBake.valueS2 = "Test2" ; 
		Test_HalfBake valueHalfBake = new Test_HalfBake() ; 
		valueHalfBake.valueC1 = Color.BROWN ; 
		valueHalfBake.valueI1 = 3 ; 
		valueHalfBake.valueS1 = "Test3" ; 
		
		String pathBake = FVars_Path.path + "Crach" + FVars_Extensions.JSON ; 
		String pathHalf = FVars_Path.path + "CrachHalf" + FVars_Extensions.JSON ; 
		
		try 
    	{
			mapper.writeValue(new File(pathBake), valueBake);
			mapper.writeValue(new File(pathHalf), valueHalfBake);
			
			Test_Bake testNormal = mapper.readValue(new File(pathBake), Test_Bake.class) ; 
			Test_Bake testCrach = mapper.readValue(new File(pathHalf), Test_Bake.class) ; 
			
			Test_HalfBake testHalfNormal = mapper.readValue(new File(pathHalf), Test_HalfBake.class) ; 
			Test_HalfBake testHalfCrach = mapper.readValue(new File(pathBake), Test_HalfBake.class) ; 
			
			System.out.println("testResilience work :" 
			+ " On normale " + testNormal.valueS1 
			+ " On crach " + testCrach.valueS1 
			+ " On crach noValue " + testCrach.valueS2 
			+ " On normale " + testHalfNormal.valueS1 
			+ " On crach " + testHalfCrach.valueS1 
					);
	    	
			
		} 
    	catch (IOException e) 
    	{	
			e.printStackTrace();
		}
	}
	
}
