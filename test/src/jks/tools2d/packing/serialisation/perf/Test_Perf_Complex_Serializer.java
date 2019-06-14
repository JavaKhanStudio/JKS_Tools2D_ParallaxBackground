package jks.tools2d.packing.serialisation.perf;

import java.util.ArrayList;
import java.util.List;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class Test_Perf_Complex_Serializer extends Serializer<Test_Perf_Complex>
{

	@Override
	public void write(Kryo kryo, Output output, Test_Perf_Complex object) 
	{
		output.writeInt(object.valueI1);
		output.writeInt(object.valueI2);
		output.writeString(object.valueS1);
		output.writeString(object.valueS2);
		kryo.writeObject(output, object.test1);
		kryo.writeObject(output, object.test2);
		kryo.writeObject(output, object.arrayValue);
		kryo.writeObject(output, object.arrayValueObject);
	}
	
	/*
	 * public int valueI1; 
	public int valueI2;
	
	public String valueS1; 
	public String valueS2; 
	
	public Test_Perf_Simple test1 ; 
	public Test_Perf_Simple test2 ; 
	
	public List<Integer> arrayValue ; 
	public List<Test_Perf_Simple> arrayValueObject ; (non-Javadoc)
	 * @see com.esotericsoftware.kryo.Serializer#read(com.esotericsoftware.kryo.Kryo, com.esotericsoftware.kryo.io.Input, java.lang.Class)
	 */

	@Override
	public Test_Perf_Complex read(Kryo kryo, Input input, Class<? extends Test_Perf_Complex> type) 
	{
		Test_Perf_Complex value = new Test_Perf_Complex() ; 
		value.valueI1 = input.readInt() ;
		value.valueI2 = input.readInt() ;
		value.valueS1 = input.readString() ;
		value.valueS2 = input.readString() ;
		value.test1 = kryo.readObject(input, Test_Perf_Simple.class); 
		value.test2 = kryo.readObject(input, Test_Perf_Simple.class); 
		value.arrayValue = kryo.readObject(input, ArrayList.class); 
		value.arrayValueObject = kryo.readObject(input, ArrayList.class); 
		return value ; 
	}

}
