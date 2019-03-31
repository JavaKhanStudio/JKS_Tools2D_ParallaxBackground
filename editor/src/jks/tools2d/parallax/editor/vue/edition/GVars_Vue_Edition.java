package jks.tools2d.parallax.editor.vue.edition;

import java.util.ArrayList;
import java.util.HashMap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;

import jks.tools2d.libgdxutils.color.ExtendedColorPicker;
import jks.tools2d.parallax.ParallaxLayer;

public class GVars_Vue_Edition
{
	public static int size_Bloc_Selection ;
	public static int size_Bloc_Parallax ;
	public static int size_Height_Bloc_Parallax_Controle ;
	public static int sizeTabsBar ; 
	public static OrthographicCamera camera ;
	public static ArrayList<TextureRegion> allImage ;	
	public static HashMap<TextureRegion,TR_Infos> imageRef ; 
	
	public static int parr_Size_X ; 
	public static int parr_Size_Y ; 
	public static int parr_Pos_X ; 
	public static int parr_Pos_Y ; 
	
	public static boolean isPause = true ; 
	
	public static int screenSize = 40 ; 
	
	public static float screenSpeed = 0; 
	
	public static ParallaxLayer currentlySelectedParallax ;
	
	public static boolean autoGoToSelected = true; 
	
	public static TabbedPane tabbedPane ; 
	
	public static boolean inTextureSelection ; 
	public static ExtendedColorPicker colorPicked ; 
	
	public static void buildSizes()
	{
		size_Bloc_Selection = Gdx.graphics.getWidth()/4 ; 
		size_Bloc_Parallax = (Gdx.graphics.getWidth()/4) * 3 ;
		size_Height_Bloc_Parallax_Controle = Gdx.graphics.getHeight()/4 ; 
		sizeTabsBar = 30 ; 
	}

	public static void selectLayer(ParallaxLayer layer)
	{
		currentlySelectedParallax = layer ; 
		if(autoGoToSelected)
		{
			tabbedPane.switchTab(2);
		}
	}
}
