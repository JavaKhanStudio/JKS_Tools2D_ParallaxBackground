package jks.tools2d.packing.serialisation.perf;

public class Test_Perf_Simple 
{
	public int valueI1 ; 
	public int valueI2 ; 
	
	public Test_Perf_Simple()
	{}
	
	public static Test_Perf_Simple buildOne(int x)
	{
		Test_Perf_Simple object = new Test_Perf_Simple() ; 
		object.valueI1 = x ; 
		object.valueI2 = x * 2 ; 
		return object ; 
	}
	
	public void action()
	{valueI1 += valueI2 ;}
}
