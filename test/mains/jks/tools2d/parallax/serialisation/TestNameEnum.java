package jks.tools2d.parallax.serialisation;

import java.io.FileNotFoundException;

import com.esotericsoftware.kryo.Kryo;
import com.fasterxml.jackson.databind.ObjectMapper;

import jks.tools2d.packing.serialisation.perf.TestUtils_Perf;

public enum TestNameEnum 
{
	kryoS,kryoSR,
	jackS,jackSR,
	kryoC,kryoCR,
	jackC,jackCR
	;
	
	
	public void doXAction(int x,Kryo kryo,ObjectMapper mapper) throws FileNotFoundException
	{
		switch(this)
		{
			case kryoS : 
			{TestUtils_Perf.doXSaveSimpleKryo(x, kryo); break ;}
			case kryoSR : 
			{TestUtils_Perf.doXReadSimpleKryo(x, kryo); break ;}
			case kryoC : 
			{TestUtils_Perf.doXSaveComplexKryo(x, kryo); break ;}
			case kryoCR : 
			{TestUtils_Perf.doXReadComplexKryo(x, kryo); break ;}
			case jackS : 
			{TestUtils_Perf.doXSaveSimpleJson(x, mapper); break ;}
			case jackSR : 
			{TestUtils_Perf.doXReadSimpleJson(x, mapper);  break ;}
			case jackC : 
			{TestUtils_Perf.doXSaveComplexJson(x, mapper);  break ;}
			case jackCR : 
			{TestUtils_Perf.doXReadComplexJson(x, mapper);  break ;}
			
		}
	}
}
