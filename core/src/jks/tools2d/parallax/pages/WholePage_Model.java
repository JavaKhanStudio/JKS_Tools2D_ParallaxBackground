package jks.tools2d.parallax.pages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.utils.GdxRuntimeException;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.heart.Gvars_Parallax;
import jks.tools2d.parallax.side.SquareBackground;

/**
 * A complete parallax: the gradient background colors plus the layers. This is what .plax/.jplax files contain.
 * <p>
 * Translatable by GWT: how it is written lives in {@code WholePage_Model_Serializer} and {@code Json_MixIns}.
 */
public class WholePage_Model
{
	public Color topHalf_top;
	public Color topHalf_bottom;
	public float topHalfSize;

	public Color bottomHalf_top;
	public Color bottomHalf_bottom;
	public float bottomHalfSize;

	public boolean repeatOnX = true;
	public boolean repeatOnY = false;

	public Page_Model pageModel;

	public List<ParallaxLayer> preloadValue;

	private HashMap<String, AtlasRegion> loadedRegion;
	private TextureAtlas loadedAtlas;
	/** True when {@link #loadedAtlas} was created by this page rather than handed in or owned by the AssetManager. */
	private boolean ownsAtlas;

	public WholePage_Model()
	{
		topHalf_top = new Color(Color.WHITE);
		topHalf_bottom = new Color(Color.WHITE);
		bottomHalf_top = new Color(Color.WHITE);
		bottomHalf_bottom = new Color(Color.WHITE);
		topHalfSize = 0.5f;
		bottomHalfSize = 0.5f;

		pageModel = new Page_Model();
	}

	public WholePage_Model(String atlasPath, Color topHalf_top, Color topHalf_bottom, Color bottomHalf_top, Color bottomHalf_bottom)
	{
		this(atlasPath);
		this.topHalf_top = topHalf_top;
		this.topHalf_bottom = topHalf_bottom;
		this.bottomHalf_top = bottomHalf_top;
		this.bottomHalf_bottom = bottomHalf_bottom;
	}

	public WholePage_Model(String atlasPath)
	{
		this();
		pageModel.atlasName = atlasPath;
	}

	/**
	 * Layers built from an internal atlas, loaded through {@link Gvars_Parallax#getManager()}, for the default world
	 * size.
	 */
	public List<ParallaxLayer> getDrawing()
	{return getDrawing(null, Gvars_Parallax.getWorldWidth(), Gvars_Parallax.getWorldHeight());}

	/**
	 * Layers built from an atlas found in {@code relativePath} (internal loading when the path is empty), for the
	 * default world size.
	 */
	public List<ParallaxLayer> getDrawing(String relativePath)
	{return getDrawing(relativePath, Gvars_Parallax.getWorldWidth(), Gvars_Parallax.getWorldHeight());}

	/**
	 * Layers built for a world of that size, from an atlas found in {@code relativePath} (internal loading when the path
	 * is empty). They are built once: later calls return the same layers.
	 */
	public List<ParallaxLayer> getDrawing(String relativePath, float worldWidth, float worldHeight)
	{
		if (preloadValue == null)
		{
			if (relativePath == null || relativePath.isEmpty())
				preload(worldWidth, worldHeight);
			else
				preload(relativePath, worldWidth, worldHeight);
		}

		return preloadValue;
	}

	/** @see SquareBackground */
	public SquareBackground buildTopSquareBackground(float screenPercentage)
	{return new SquareBackground(topHalf_top.cpy(), topHalf_bottom.cpy(), screenPercentage, true);}

	public SquareBackground buildBottomSquareBackground(float screenPercentage)
	{return new SquareBackground(bottomHalf_top.cpy(), bottomHalf_bottom.cpy(), screenPercentage, false);}

	public void preload()
	{preload(Gvars_Parallax.getWorldWidth(), Gvars_Parallax.getWorldHeight());}

	public void preload(float worldWidth, float worldHeight)
	{
		AssetManager manager = Gvars_Parallax.getManager();
		manager.load(pageModel.atlasName, TextureAtlas.class);
		manager.finishLoadingAsset(pageModel.atlasName);
		useAtlas(manager.get(pageModel.atlasName, TextureAtlas.class), false, worldWidth, worldHeight);
	}

	public void preload(String relativePath)
	{preload(relativePath, Gvars_Parallax.getWorldWidth(), Gvars_Parallax.getWorldHeight());}

	public void preload(String relativePath, float worldWidth, float worldHeight)
	{
		if (pageModel.atlasName != null)
			useAtlas(new TextureAtlas(new FileHandle(relativePath + "/" + pageModel.atlasName)), true, worldWidth, worldHeight);
		else
			useAtlas(new TextureAtlas(), true, worldWidth, worldHeight);
	}

	/** Builds the layers, for the default world size, from an atlas the caller keeps ownership of. */
	public void forceLoad(TextureAtlas atlas)
	{useAtlas(atlas, false, Gvars_Parallax.getWorldWidth(), Gvars_Parallax.getWorldHeight());}

	private void useAtlas(TextureAtlas atlas, boolean owned, float worldWidth, float worldHeight)
	{
		disposeOwnedAtlas();
		loadedAtlas = atlas;
		ownsAtlas = owned;
		loadedRegion = null;
		preloadValue = load(worldWidth, worldHeight, atlas);

		// setUpEverything placed the layers in the default world.
		for (ParallaxLayer layer : preloadValue)
		{
			layer.setWorldSize(worldWidth, worldHeight);
			layer.resetPosition();
		}
	}

	/** The atlas the layers were built from, or null before loading. */
	public TextureAtlas getLoadedAtlas()
	{return loadedAtlas;}

	/** Disposes the atlas if this page loaded it itself (external path); AssetManager and caller atlases are left alone. */
	public void disposeOwnedAtlas()
	{
		if (ownsAtlas && loadedAtlas != null)
		{
			loadedAtlas.dispose();
			// The layers drew from it: rebuild them from disk if this page is used again.
			preloadValue = null;
			loadedRegion = null;
		}

		loadedAtlas = null;
		ownsAtlas = false;
	}

	protected List<ParallaxLayer> load(float worldWidth, float worldHeight, TextureAtlas atlas)
	{
		List<ParallaxLayer> layers = new ArrayList<>(pageModel.pageList.size());

		for (Parallax_Model parallax : pageModel.pageList)
			layers.add(buildLayer(parallax, atlas, worldWidth));

		return layers;
	}

	protected ParallaxLayer buildLayer(Parallax_Model parallax, TextureAtlas atlas, float worldWidth)
	{
		ParallaxLayer layer = new ParallaxLayer(
				findLayer(parallax, atlas),
				true,
				worldWidth,
				parallax.parallaxScalingSpeedX, parallax.parallaxScalingSpeedY,
				parallax.sizeRatio);

		layer.setUpEverything(parallax);
		return layer;
	}

	/**
	 * The atlas's own region for a layer (TextureAtlas#findRegions would return copies): the n-th region with that
	 * name, in atlas order.
	 */
	protected AtlasRegion findLayer(Parallax_Model parallax, TextureAtlas atlas)
	{
		if (loadedRegion == null)
		{
			loadedRegion = new HashMap<>();
			HashMap<String, Integer> positions = new HashMap<>();
			for (AtlasRegion region : atlas.getRegions())
				loadedRegion.put(region.name + positions.merge(region.name, 1, Integer::sum), region);
		}

		// Keys are 1-based ("ground1" is position 0) so they never depend on region indices.
		AtlasRegion region = loadedRegion.get(parallax.regionName + (parallax.regionPosition + 1));
		if (region == null)
			throw new GdxRuntimeException("Region '" + parallax.regionName + "' #" + parallax.regionPosition
					+ " not found in atlas " + pageModel.atlasName);
		return region;
	}

	public void cleanPath()
	{pageModel.atlasName = pageModel.atlasName.substring(pageModel.atlasName.lastIndexOf('/') + 1);}

	public Color getTopHalf_top()
	{return topHalf_top;}

	public void setTopHalf_top(Color topHalf_top)
	{this.topHalf_top = topHalf_top;}

	public Color getTopHalf_bottom()
	{return topHalf_bottom;}

	public void setTopHalf_bottom(Color topHalf_bottom)
	{this.topHalf_bottom = topHalf_bottom;}

	public float getTopHalfSize()
	{return topHalfSize;}

	public void setTopHalfSize(float topHalfSize)
	{this.topHalfSize = topHalfSize;}

	public Color getBottomHalf_top()
	{return bottomHalf_top;}

	public void setBottomHalf_top(Color bottomHalf_top)
	{this.bottomHalf_top = bottomHalf_top;}

	public Color getBottomHalf_bottom()
	{return bottomHalf_bottom;}

	public void setBottomHalf_bottom(Color bottomHalf_bottom)
	{this.bottomHalf_bottom = bottomHalf_bottom;}

	public float getBottomHalfSize()
	{return bottomHalfSize;}

	public void setBottomHalfSize(float bottomHalfSize)
	{this.bottomHalfSize = bottomHalfSize;}

	public boolean isRepeatOnX()
	{return repeatOnX;}

	public void setRepeatOnX(boolean repeatOnX)
	{this.repeatOnX = repeatOnX;}

	public boolean isRepeatOnY()
	{return repeatOnY;}

	public void setRepeatOnY(boolean repeatOnY)
	{this.repeatOnY = repeatOnY;}

	public Page_Model getPageModel()
	{return pageModel;}

	public void setPageModel(Page_Model pageModel)
	{this.pageModel = pageModel;}
}
