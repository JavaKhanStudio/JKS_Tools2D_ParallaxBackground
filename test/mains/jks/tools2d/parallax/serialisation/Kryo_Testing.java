package jks.tools2d.parallax.serialisation;

import static jks.tools2d.packing.serialisation.TestUtils_Kryo.testColor;
import static jks.tools2d.packing.serialisation.TestUtils_Kryo.testPage;
import static jks.tools2d.packing.serialisation.TestUtils_Kryo.testParallaxModel;
import static jks.tools2d.packing.serialisation.TestUtils_Kryo.testWholePage;

import java.util.ArrayList;

import com.badlogic.gdx.graphics.Color;
import com.esotericsoftware.kryo.Kryo;

import jks.tools2d.parallax.pages.Color_Serializer;
import jks.tools2d.parallax.pages.Page_Model;
import jks.tools2d.parallax.pages.Parallax_Model;
import jks.tools2d.parallax.pages.WholePage_Model; 

public class Kryo_Testing 
{
	
    static public void main (String[] args) throws Exception
    {
	      Kryo kryo = prepareKryo() ; 
	      
	      System.out.println("hellow kryo");
	      testColor(kryo);
	      testParallaxModel(kryo) ; 
	      testPage(kryo) ; 
	      testWholePage(kryo);
	      System.out.println("Test Finish");
	 }
    
    public static Kryo prepareKryo()
    {
    	Kryo kryo = new Kryo();
	      kryo.register(Color.class, new Color_Serializer());
	      kryo.register(Parallax_Model.class) ; 
	      kryo.register(Page_Model.class) ;
	      kryo.register(ArrayList.class) ; 
	      kryo.register(WholePage_Model.class) ; 
	      return kryo ;
    }
   
}