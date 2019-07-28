package jks.tools2d.packing.serialisation.perf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Test_Perf_Complex 
{

	public int valueI1; 
	public int valueI2;
	
	public String valueS1; 
	public String valueS2; 
	
	public Test_Perf_Simple test1 ; 
	public Test_Perf_Simple test2 ; 
	
	public ArrayList<Integer> arrayValue ; 
	public ArrayList<Test_Perf_Simple> arrayValueObject ; 
	
	public Test_Perf_Complex()
	{}

	public static Test_Perf_Complex buildOne(int x) 
	{
		Test_Perf_Complex complex = new Test_Perf_Complex() ; 
		complex.valueI1 = x ; 
		complex.valueI2 = x * 2 ; 
		complex.valueS1 = "test" ; 
		complex.valueS2 = "more Test" ;
		complex.test1 = Test_Perf_Simple.buildOne(x) ; 
		complex.test2 = Test_Perf_Simple.buildOne(x * 2) ; 
		complex.arrayValue =  new ArrayList<Integer>(Arrays.asList(1,2,3,4,5,6,7,8,9,10)) ; 
		complex.arrayValueObject = new ArrayList<Test_Perf_Simple>(Arrays.asList(Test_Perf_Simple.buildOne(x) , Test_Perf_Simple.buildOne(x + 1), Test_Perf_Simple.buildOne(x + 2), Test_Perf_Simple.buildOne(x + 3))) ; 
		return complex;
	}
	
	public void action()
	{
		valueI1 += valueI2 ; 
		valueS1 += valueS2 ; 
	}
	
}
