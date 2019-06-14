package jks.tools2d.parallax.serialisation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import static jks.tools2d.packing.serialisation.TestUtils_JSON.* ;

public class Json_Testing 
{
	static public void main (String[] args) throws Exception
    {
		ObjectMapper mapper = prepareJson() ; 
	      
	    System.out.println("hellow Json");
	    testColor(mapper) ; 
	    testResilience(mapper) ; 
	    System.out.println("Test Finish");
	}

	private static ObjectMapper prepareJson() 
	{
		ObjectMapper objectMapper = new ObjectMapper() ; 
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false) ; 
		return objectMapper ; 
	}
}
