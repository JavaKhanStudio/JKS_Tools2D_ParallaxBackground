package jks.tools2d.parallax.editor.gvars;

import static jks.tools2d.parallax.editor.vue.edition.Vue_Edition.parallax_Heart;

import java.util.ArrayList;
import java.util.HashMap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;

import jks.tools2d.filewatch.FileWatching_Image;
import jks.tools2d.libgdxutils.color.ExtendedColorPicker;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.editor.vue.edition.VE_Center_ParallaxShow;
import jks.tools2d.parallax.editor.vue.edition.VE_Options;
import jks.tools2d.parallax.editor.vue.edition.VE_Tab_AControl;
import jks.tools2d.parallax.editor.vue.edition.VE_Tab_TextureList_Adding;
import jks.tools2d.parallax.editor.vue.edition.data.ParallaxDefaultValues;
import jks.tools2d.parallax.editor.vue.edition.data.Position_Infos;
import jks.tools2d.parallax.editor.vue.edition.data.Project_Data;
import jks.tools2d.parallax.editor.vue.edition.data.Project_Infos;
import jks.tools2d.parallax.pages.WholePage_Model;

public class GVars_Vue_Edition
{
	public static int size_Bloc_Selection_Parallax ;
	public static int size_Bloc_Parallax ;
	public static int size_Height_Bloc_Parallax_Controle ;
	public static int sizeTabsBar ; 
	public static OrthographicCamera camera ;
	
	public static ArrayList<TextureRegion> allImage ;
	
	public static HashMap<TextureRegion,Position_Infos> imageRef  = new HashMap<TextureRegion, Position_Infos>() ; 
	public static HashMap<TextureRegion,ArrayList<ParallaxLayer>> textureLink = new HashMap<TextureRegion,ArrayList<ParallaxLayer>>() ; 
	public static HashMap<String,TextureRegion> outsideTextureReserve = new HashMap<String, TextureRegion>(); 
	
	public static int parr_Size_X ; 
	public static int parr_Size_Y ; 
	public static int parr_Pos_X ; 
	public static int parr_Pos_Y ; 
	
	public static boolean isPause = true ; 
	
	public static int screenSize = 40 ; 
	
	public static ParallaxLayer currentlySelectedParallax ;
	
	public static TabbedPane tabbedPane ; 
	
	public static boolean inTextureSelection ; 
	public static ExtendedColorPicker colorPicked ; 
	
	public static Project_Infos projectInfos ;
	public static Project_Data projectDatas ; 
	
	public static Array<ParallaxLayer> trashedValues = new Array<ParallaxLayer>(); 
	public static Array<Integer> trashedValuesPosition = new Array<Integer>(); 
	
	public static String relativePath ;
	public static TextureAtlas atlas;
	
	public static HashMap<TextureRegion,FileWatching_Image> activeFileWatching = new  HashMap<TextureRegion,FileWatching_Image>();
	public static ArrayList<FileWatching_Image> textureChange = new  ArrayList<FileWatching_Image>();
	
	public static boolean showParallaxFullScreen = false ; 
	
	public static VE_Center_ParallaxShow centerControl ;
	public static VE_Tab_AControl tabControl ; 
	public static VE_Options optionsControl ; 
	
	public static float hideInterfaceTimmer ;
	
	public static float timeForAutoSaveTimmer ; 
	public static final float timeForAutoSaveAt = 300; 
	
	public static ParallaxDefaultValues getDefaults()
	{return projectDatas.defaults ;}
	
	public static void setDefaults(ParallaxDefaultValues Defaults)
	{projectDatas.defaults = Defaults ;} 
	
	public static void buildSizes()
	{
		size_Bloc_Selection_Parallax = Gdx.graphics.getWidth()/4 ; 
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
			TextureRegion texture = parallaxPage.preloadValue.get(x).getTexRegion() ; 
			
			boolean isFromAtlas = outsideTextureReserve.get(parallaxPage.pageModel.pageList.get(x).regionName) == null; 
			
			imageRef.put(texture,
					new Position_Infos(isFromAtlas, parallaxPage.pageModel.atlasName,parallaxPage.pageModel.pageList.get(x))); 
			
			GVars_Vue_Edition.addToLinks(parallaxPage.preloadValue.get(x));
		}		
	}
	
	public static TextureAtlas getAtlas()
	{
		return atlas ;
	}
	
	public static void setItems()
	{
		if(VE_Tab_TextureList_Adding.imageList == null)
		{
			System.out.println("Set Items badly call");
			return ;
		}
		
		TextureRegion[] stockArr = new TextureRegion[allImage.size()];
		for(int x=0 ; x < allImage.size() ; x++)
		{
			stockArr[x] = allImage.get(x) ; 
		}
		
		VE_Tab_TextureList_Adding.imageList.setItems(stockArr);	
	}
	
	public static void addToLinks(ParallaxLayer layer)
	{
		ArrayList<ParallaxLayer> linkList = textureLink.get(layer.getTexRegion()) ; 
		if(linkList == null)
		{
			linkList = new ArrayList<ParallaxLayer>() ; 
			linkList.add(layer) ; 
			textureLink.put(layer.getTexRegion(), linkList) ; 
		}
		
		linkList.add(layer) ; 
	}
	
}