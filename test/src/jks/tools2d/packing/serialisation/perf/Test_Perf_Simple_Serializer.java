package jks.tools2d.packing.serialisation.perf;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class Test_Perf_Simple_Serializer extends Serializer<Test_Perf_Simple>
{

	@Override
	public void write(Kryo kryo, Output output, Test_Perf_Simple object) 
	{
		output.writeInt(object.valueI1);
		output.writeInt(object.valueI2);
	}

	@Override
	public Test_Perf_Simple read(Kryo kryo, Input input, Class<? extends Test_Perf_Simple> type)
	{
		Test_Perf_Simple simple = new Test_Perf_Simple() ; 
		simple.valueI1 = input.readInt() ;
		simple.valueI2 = input.readInt() ;
		return simple ; 
	}

}
