package jks.tools2d.packing.serialisation;

import java.io.FileInputStream;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;

import com.badlogic.gdx.graphics.Color;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import jks.tools2d.parallax.data.Enum_ColdNight;
import jks.tools2d.parallax.editor.gvars.FVars_Extensions;
import jks.tools2d.parallax.gvars.FVars_Path;
import jks.tools2d.parallax.pages.Page_Model;
import jks.tools2d.parallax.pages.Parallax_Model;
import jks.tools2d.parallax.pages.WholePage_Model;
import static jks.tools2d.packing.serialisation.FVars_SerialisationName.* ; 

public class TestUtils_Kryo 
{

	static public void testColor(Kryo kryo) throws FileNotFoundException
    {
    	Color color = new Color();
    	color.a = 0.5f ; 
    	color.b = 0.6f ;
    	color.g = 0.7f ;
    	color.r = 0.8f ;
    	
    	Output output = new Output(new FileOutputStream(FVars_Path.path + colorName + formatKryo));
    	kryo.writeObject(output, color);
    	output.close();

    	Input input = new Input(new FileInputStream(FVars_Path.path + colorName + formatKryo));
    	Color colorLoaded = kryo.readObject(input, Color.class);
    	input.close();   
    	System.out.println("testColor work " + colorLoaded.a + " " + colorLoaded.b + " " + colorLoaded.g + " " + colorLoaded.r + " " );     
    }
    
    static public void testParallaxModel(Kryo kryo) throws FileNotFoundException
    {
    	Parallax_Model parallaxPage = buildPageModel() ; 
    	
    	
    	Output output = new Output(new FileOutputStream(FVars_Path.path + parallaxName + formatKryo));
    	kryo.writeObject(output, parallaxPage);
    	output.close();

    	Input input = new Input(new FileInputStream(FVars_Path.path + parallaxName + formatKryo));
    	Parallax_Model model = kryo.readObject(input, Parallax_Model.class);
    	input.close();   
    	System.out.println("testParallaxModel work " + parallaxPage.region_Name + " " + 
			parallaxPage.region_Position + " " + 
			parallaxPage.parallaxScalingSpeedX + " " + 
			parallaxPage.parallaxScalingSpeedY + " " + 
			parallaxPage.speed + " " + 
			parallaxPage.decal_Y_Ratio + " ");
    }
    
    public static Parallax_Model buildPageModel()
    {
    	Parallax_Model parallaxPage = new Parallax_Model() ; 
    	parallaxPage.region_Name = "region_Name" ; 
    	parallaxPage.region_Position = 1 ; 
    	parallaxPage.parallaxScalingSpeedX = 1 ; 
    	parallaxPage.parallaxScalingSpeedY = 2 ; 
    	parallaxPage.speed = 3 ; 
    	parallaxPage.decal_Y_Ratio = 4 ; 
    	return parallaxPage ; 
    }

    static public void testPage(Kryo kryo) throws FileNotFoundException
    {
    	Page_Model page = buildPage() ; 
    	
    	Output output = new Output(new FileOutputStream(FVars_Path.path + pageName + formatKryo));
    	kryo.writeObject(output, page);
    	output.close();

    	Input input = new Input(new FileInputStream(FVars_Path.path + pageName + formatKryo));
    	Page_Model model = kryo.readObject(input, Page_Model.class);
    	input.close();   
    	System.out.println("testPage work " + " Number :" + page.pageList.size());
    }
    
    static public Page_Model buildPage()
    {
    	Page_Model page = new Page_Model() ; 
    	page.atlasName = "atlasPath" ;
    	page.outside = false ; 
    	page.pageList = new ArrayList<Parallax_Model>() ; 
    	page.pageList.add(buildPageModel()) ; 
    	page.pageList.add(buildPageModel()) ; 
    	page.pageList.add(buildPageModel()) ; 
    	return page ; 
    }
    
    static public void testWholePage(Kryo kryo) throws FileNotFoundException
    {
    	WholePage_Model wholePage = Enum_ColdNight.COLD_NIGHT.wholePage ; 
    	wholePage.cleanPath();
    	Output output = new Output(new FileOutputStream(FVars_Path.path + pageWholeName + "." + FVars_Extensions.PARALLAX));
    	kryo.writeObject(output, wholePage);
    	output.close();

    	Input input = new Input(new FileInputStream(FVars_Path.path + pageWholeName + "." + FVars_Extensions.PARALLAX));
    	WholePage_Model model = kryo.readObject(input, WholePage_Model.class);
    	System.out.println("testWholePage work " + " Number :" + model.bottomHalf_top);
   }
      
   static public class SomeClass 
   {
      String value;
   }
}
