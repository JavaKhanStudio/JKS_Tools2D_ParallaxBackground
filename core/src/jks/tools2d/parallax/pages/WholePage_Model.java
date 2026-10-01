package jks.tools2d.parallax.pages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.GdxRuntimeException;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxParticles;
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

	/**
	 * True: a layer whose atlas region was packed with its whitespace stripped keeps the region's original size, and
	 * draws the packed image at its offset inside it, as libGDX's AtlasSprite does. False, the default and what every
	 * page saved before .plax format 4 holds: the packed image is stretched over the whole layer. The editor sets it on
	 * the pages it creates; a page keeps the value it was saved with, so its look never changes.
	 */
	public boolean useOriginalSize;

	public Page_Model pageModel;

	public List<ParallaxLayer> preloadValue;

	private HashMap<String, AtlasRegion> loadedRegion;
	private TextureAtlas loadedAtlas;
	/** True when {@link #loadedAtlas} was created by this page rather than handed in or owned by the AssetManager. */
	private boolean ownsAtlas;
	/** Finds the PARTICLES layers' .p files in the atlas's folder; null when the layers were built without one. */
	private FileHandleResolver effectFiles;

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
		String atlas = Gvars_Parallax.atlasFile(pageModel.atlasName);
		if (!atlas.equals(pageModel.atlasName) && !manager.getFileHandleResolver().resolve(atlas).exists())
			atlas = pageModel.atlasName;
		if (Utils_Etc2_Atlas.isEtc2Atlas(atlas))
			Utils_Etc2_Atlas.register(manager);
		manager.load(atlas, TextureAtlas.class);
		manager.finishLoadingAsset(atlas);
		final FileHandleResolver assets = manager.getFileHandleResolver();
		final String folder = atlas.substring(0, atlas.lastIndexOf('/') + 1);
		effectFiles = new FileHandleResolver()
		{
			@Override
			public FileHandle resolve(String fileName)
			{return assets.resolve(folder + fileName);}
		};
		useAtlas(manager.get(atlas, TextureAtlas.class), false, worldWidth, worldHeight);
	}

	public void preload(String relativePath)
	{preload(relativePath, Gvars_Parallax.getWorldWidth(), Gvars_Parallax.getWorldHeight());}

	public void preload(final String relativePath, float worldWidth, float worldHeight)
	{
		effectFiles = new FileHandleResolver()
		{
			@Override
			public FileHandle resolve(String fileName)
			{return new FileHandle(relativePath + "/" + fileName);}
		};
		if (pageModel.atlasName != null)
		{
			FileHandle atlas = new FileHandle(relativePath + "/" + Gvars_Parallax.atlasFile(pageModel.atlasName));
			if (!atlas.exists())
				atlas = new FileHandle(relativePath + "/" + pageModel.atlasName);
			useAtlas(Utils_Etc2_Atlas.load(atlas), true, worldWidth, worldHeight);
		}
		else
			useAtlas(new TextureAtlas(), true, worldWidth, worldHeight);
	}

	/**
	 * Builds the layers, for the default world size, from an atlas the caller keeps ownership of. Its PARTICLES layers
	 * draw nothing: {@link #forceLoad(TextureAtlas, FileHandleResolver)} says where their effects are.
	 */
	public void forceLoad(TextureAtlas atlas)
	{forceLoad(atlas, null);}

	/**
	 * Builds the layers, for the default world size, from an atlas the caller keeps ownership of, and the PARTICLES
	 * layers' effects from the .p files {@code effectFiles} finds (null: none, the layers draw nothing).
	 */
	public void forceLoad(TextureAtlas atlas, FileHandleResolver effectFiles)
	{
		this.effectFiles = effectFiles;
		useAtlas(atlas, false, Gvars_Parallax.getWorldWidth(), Gvars_Parallax.getWorldHeight());
	}

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
		if (parallax.kind == Enum_LayerKind.EMPTY)
		{
			ParallaxLayer empty = ParallaxLayer.empty(parallax.name, parallax.sizeRatio);
			empty.setUpEverything(parallax);
			return empty;
		}

		if (parallax.kind == Enum_LayerKind.PARTICLES)
		{
			ParallaxLayer particles = ParallaxLayer.particles(loadParticles(parallax, atlas), parallax.particlesAnchor, parallax.sizeRatio);
			particles.setUpEverything(parallax);
			return particles;
		}

		if (parallax.kind == Enum_LayerKind.SEQUENCE)
		{
			if (parallax.sequenceSegments == null || parallax.sequenceSegments.isEmpty())
				throw new GdxRuntimeException("The sequence layer " + (parallax.name == null ? "" : "'" + parallax.name + "' ")
						+ "of " + pageModel.atlasName + " has no segment");
			List<TextureRegion> segments = new ArrayList<>(parallax.sequenceSegments.size());
			int[] weights = new int[parallax.sequenceSegments.size()];
			for (int i = 0; i < weights.length; i++)
			{
				Sequence_Segment segment = parallax.sequenceSegments.get(i);
				segments.add(findRegion(segment.regionName, segment.regionPosition, atlas));
				weights[i] = segment.weight;
			}
			// Built from the stored seed: a reader given a game's seed draws the cycle again when it takes the layer.
			ParallaxLayer sequence = ParallaxLayer.sequence(segments, weights, parallax.sequenceSeed, parallax.sequenceLength, worldWidth, parallax.sizeRatio);
			sequence.setUseOriginalSize(useOriginalSize);
			sequence.setUpEverything(parallax);
			return sequence;
		}

		List<TextureRegion> regions = new ArrayList<>(1);
		regions.add(findLayer(parallax, atlas));
		ParallaxLayer layer = new ParallaxLayer(
				parallax.kind == Enum_LayerKind.SHADER ? Enum_LayerKind.SHADER : Enum_LayerKind.IMAGE,
				regions,
				true,
				worldWidth,
				parallax.parallaxScalingSpeedX, parallax.parallaxScalingSpeedY,
				parallax.sizeRatio);

		layer.setUseOriginalSize(useOriginalSize);
		layer.setUpEverything(parallax);
		return layer;
	}

	/**
	 * A PARTICLES layer's libGDX effect, its images taken from the page's atlas; null, said in the log, when the page
	 * names no .p for libGDX or was built without a folder to find it in.
	 */
	protected ParallaxParticles loadParticles(Parallax_Model parallax, TextureAtlas atlas)
	{
		if (parallax.particlesLibgdx == null || parallax.particlesLibgdx.isEmpty() || effectFiles == null)
		{
			if (Gdx.app != null)
				Gdx.app.log("Parallax", "The particle layer " + (parallax.name == null ? "" : "'" + parallax.name + "' ")
						+ "of " + pageModel.atlasName + " draws nothing: "
						+ (effectFiles == null ? "the page was built without a folder to find its effect in" : "the page names no libGDX effect (.p)"));
			return null;
		}

		ParallaxParticles effect = new ParallaxParticles();
		effect.load(effectFiles.resolve(parallax.particlesLibgdx), atlas);
		return effect;
	}

	/**
	 * The atlas's own region for a layer (TextureAtlas#findRegions would return copies): the n-th region with that
	 * name, in atlas order.
	 */
	protected AtlasRegion findLayer(Parallax_Model parallax, TextureAtlas atlas)
	{return findRegion(parallax.regionName, parallax.regionPosition, atlas);}

	/** The atlas's own n-th region named {@code regionName}, in atlas order. */
	protected AtlasRegion findRegion(String regionName, int regionPosition, TextureAtlas atlas)
	{
		if (loadedRegion == null)
		{
			loadedRegion = new HashMap<>();
			HashMap<String, Integer> positions = new HashMap<>();
			for (AtlasRegion region : atlas.getRegions())
				loadedRegion.put(region.name + positions.merge(region.name, 1, Integer::sum), region);
		}

		// Keys are 1-based ("ground1" is position 0) so they never depend on region indices.
		AtlasRegion region = loadedRegion.get(regionName + (regionPosition + 1));
		if (region == null)
			throw new GdxRuntimeException("Region '" + regionName + "' #" + regionPosition
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
