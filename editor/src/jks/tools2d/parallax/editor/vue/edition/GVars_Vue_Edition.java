package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.vue.edition.Vue_Edition.parallax_Heart;

import java.util.ArrayList;
import java.util.HashMap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;

import jks.tools2d.libgdxutils.color.ExtendedColorPicker;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.pages.WholePage_Model;

public class GVars_Vue_Edition
{
	public static int size_Bloc_Selection ;
	public static int size_Bloc_Parallax ;
	public static int size_Height_Bloc_Parallax_Controle ;
	public static int sizeTabsBar ; 
	public static OrthographicCamera camera ;
	
	public static ArrayList<TextureRegion> allImage ;
	
	public static HashMap<TextureRegion,Position_Infos> imageRef  = new HashMap<TextureRegion, Position_Infos>() ;; 
	
	public static int parr_Size_X ; 
	public static int parr_Size_Y ; 
	public static int parr_Pos_X ; 
	public static int parr_Pos_Y ; 
	
	public static boolean isPause = true ; 
	
	public static int screenSize = 40 ; 
	
	public static float screenSpeed = 0; 
	
	public static ParallaxLayer currentlySelectedParallax ;
	
	public static TabbedPane tabbedPane ; 
	
	public static boolean inTextureSelection ; 
	public static ExtendedColorPicker colorPicked ; 
	
	public static ProjectInfos infos ; 
	
	public static Array<ParallaxLayer> trashedValues = new Array<ParallaxLayer>(); 
	public static Array<Integer> trashedValuesPosition = new Array<Integer>(); 
	
	public static ParallaxDefaultValues getDefaults()
	{return infos.defaults ;}
	
	public static void setDefaults(ParallaxDefaultValues Defaults)
	{infos.defaults = Defaults ;} 
	
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
		if(getDefaults().autoGoToSelected)
		{
			tabbedPane.switchTab(2);
		}
	}

	public static void setPage(WholePage_Model parallaxPage)
	{
		parallax_Heart.setPage(parallaxPage);

		for(int x = 0 ; x < parallaxPage.preloadValue.size() ; x++)
		{
			imageRef.put(parallaxPage.preloadValue.get(x).getTexRegion(),
					new Position_Infos(parallaxPage.pageModel.atlasPath,parallaxPage.pageModel.pageList.get(x))); 
		}
		
	}
	
	public static void setItems()
	{
		if(VE_Tab_TextureList_Adding.imageList == null)
		{
			System.out.println("Set Items mal call");
			return ;
		}
		
		TextureRegion[] stockArr = new TextureRegion[allImage.size()];
		for(int x=0 ; x < allImage.size() ; x++)
		{
			stockArr[x] = allImage.get(x) ; 
		}
		
		VE_Tab_TextureList_Adding.imageList.setItems(stockArr);	
	}

}
