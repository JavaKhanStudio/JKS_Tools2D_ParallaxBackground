package jks.tools2d.parallax.editor.gvars;

import static jks.tools2d.parallax.editor.vue.Vue_Edition.parallax_Heart;

import java.util.ArrayList;
import java.util.HashMap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.color.ExtendedColorPicker;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.editor.vue.edition.VE_Center_ParallaxShow;
import jks.tools2d.parallax.editor.vue.edition.VE_Options;
import jks.tools2d.parallax.editor.vue.edition.VE_Tab_AControl;
import jks.tools2d.parallax.editor.vue.edition.VE_Tab_TextureList_Adding;
import jks.tools2d.parallax.editor.vue.edition.data.ParallaxDefaultValues;
import jks.tools2d.parallax.editor.vue.edition.data.Position_Infos;
import jks.tools2d.parallax.editor.vue.edition.data.Project_Data;
import jks.tools2d.parallax.editor.vue.edition.data.Project_Infos;
import jks.tools2d.parallax.editor.vue.edition.utils.WatchedImage;
import jks.tools2d.parallax.pages.WholePage_Model;

/** State of the edition view (one project open at a time). */
public final class GVars_Vue_Edition
{
	/**
	 * The four main tab titles take 314 px in the skin font, which does not grow with the window: any narrower and
	 * the tab bar wraps onto a second row that covers the top of the tab content (below a 1225 px wide window).
	 */
	private static final int MIN_LEFT_PANEL_WIDTH = 320;

	public static int size_Bloc_Selection_Parallax_Width;
	public static int size_Bloc_Parallax;
	public static int size_Height_Bloc_Parallax_Controle;
	public static int sizeTabsBar;

	/** Every image that can be added as a layer: the atlas regions, then the loose PNG files. */
	public static ArrayList<TextureRegion> allImage = new ArrayList<>();

	/** Where each image comes from, used when saving. */
	public static HashMap<TextureRegion, Position_Infos> imageRef = new HashMap<>();
	/** Layers currently drawing each image. */
	public static HashMap<TextureRegion, ArrayList<ParallaxLayer>> textureLink = new HashMap<>();
	/** Loose PNG files of the project, by path. */
	public static HashMap<String, TextureRegion> outsideTextureReserve = new HashMap<>();
	/** Loose PNG files reloaded when they change on disk, by path. */
	public static HashMap<String, WatchedImage> activeFileWatching = new HashMap<>();

	public static int parr_Size_X;
	public static int parr_Size_Y;
	public static int parr_Pos_X;
	public static int parr_Pos_Y;

	public static boolean isPause = true;

	public static ParallaxLayer currentlySelectedParallax;

	public static TabbedPane tabbedPane;

	/** Picker receiving the color under the mouse on the next click in the preview (eyedropper), or null. */
	public static ExtendedColorPicker colorPicked;

	public static Project_Infos projectInfos;
	public static Project_Data projectDatas;

	public static Array<ParallaxLayer> trashedValues = new Array<>();
	public static Array<Integer> trashedValuesPosition = new Array<>();

	/** Folder of the open project: atlases and relative image paths are resolved from it. */
	public static String relativePath;
	public static TextureAtlas atlas;
	/** Some layers of the opened file could not be loaded (missing atlas or loose image), so the editor lacks them. */
	public static boolean loadedIncompletely;

	public static boolean showParallaxFullScreen = false;

	public static VE_Center_ParallaxShow centerControl;
	public static VE_Tab_AControl tabControl;
	public static VE_Options optionsControl;

	public static float hideInterfaceTimmer;

	public static float timeForAutoSaveTimmer;
	public static final float timeForAutoSaveAt = 300;

	private GVars_Vue_Edition()
	{}

	/** Forgets everything about the previous project, releasing the watchers and loose textures it owned. */
	public static void clear()
	{
		for (WatchedImage watched : activeFileWatching.values())
			watched.cancel();
		for (TextureRegion region : outsideTextureReserve.values())
			region.getTexture().dispose();

		allImage.clear();
		imageRef.clear();
		textureLink.clear();
		outsideTextureReserve.clear();
		activeFileWatching.clear();
		trashedValues.clear();
		trashedValuesPosition.clear();
		currentlySelectedParallax = null;
		colorPicked = null;
		atlas = null;
		loadedIncompletely = false;
		showParallaxFullScreen = false;
		isPause = true;
		timeForAutoSaveTimmer = 0;
	}

	public static ParallaxDefaultValues getDefaults()
	{return projectDatas.defaults;}

	public static void setDefaults(ParallaxDefaultValues defaults)
	{projectDatas.defaults = defaults;}

	public static void buildSizes()
	{
		size_Bloc_Selection_Parallax_Width = Math.max((int) (Gdx.graphics.getWidth() / 3.9f), MIN_LEFT_PANEL_WIDTH);
		size_Bloc_Parallax = Gdx.graphics.getWidth() - size_Bloc_Selection_Parallax_Width;
		size_Height_Bloc_Parallax_Controle = (int) (Gdx.graphics.getHeight() / 5.5f);
		sizeTabsBar = Gdx.graphics.getWidth() / 40;
	}

	public static void selectLayer(ParallaxLayer layer)
	{
		currentlySelectedParallax = layer;
		if (getDefaults().autoGoToSelected)
			tabbedPane.switchTab(2);
	}

	/** Rebuilds the content of the visible tab after the layers or the selection changed. */
	public static void refreshActiveTab()
	{
		if (tabbedPane != null && tabbedPane.getActiveTab() != null)
			tabbedPane.getActiveTab().getContentTable();
	}

	public static void setPage(WholePage_Model parallaxPage)
	{
		parallax_Heart.setPage(parallaxPage);
		atlas = parallaxPage.getLoadedAtlas();

		for (int x = 0; x < parallaxPage.preloadValue.size(); x++)
		{
			ParallaxLayer layer = parallaxPage.preloadValue.get(x);
			String regionName = parallaxPage.pageModel.pageList.get(x).regionName;
			boolean isFromAtlas = outsideTextureReserve.get(regionName) == null;

			for (TextureRegion texture : layer.getTexRegion())
				imageRef.put(texture, new Position_Infos(isFromAtlas, regionName, parallaxPage.pageModel.pageList.get(x).regionPosition));

			addToLinks(layer);
		}
	}

	public static TextureAtlas getAtlas()
	{return atlas;}

	/** Pushes {@link #allImage} to the image list of the "Adding new" tab. */
	public static void setItems()
	{
		if (VE_Tab_TextureList_Adding.imageList == null)
			return;

		VE_Tab_TextureList_Adding.imageList.setItems(allImage.toArray(new TextureRegion[0]));
	}

	public static void addToLinks(ParallaxLayer layer)
	{textureLink.computeIfAbsent(layer.getTexRegion().get(0), k -> new ArrayList<>()).add(layer);}
}
