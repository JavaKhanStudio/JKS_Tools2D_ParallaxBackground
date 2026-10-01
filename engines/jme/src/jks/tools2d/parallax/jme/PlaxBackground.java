package jks.tools2d.parallax.jme;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial.CullHint;

import jks.tools2d.parallax.LayerHook;
import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.Utils_Parallax;
import jks.tools2d.parallax.heart.Gvars_Parallax;
import jks.tools2d.parallax.pages.Utils_Page;
import jks.tools2d.parallax.pages.Utils_Page_Json;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * A parallax page behind a jME scene: core's {@code Parallax_Heart}, with jME drawing. The page is scrolled, tiled,
 * cross-faded and tinted by core's own {@link ParallaxPageReader}; {@link JmeBatch} and two {@link JmeGradient}s draw
 * it, its SHADER layers through {@link JmeLayerEffects}, in a viewport rendered before the application's, which stops clearing its colour.
 * <pre>
 * PlaxBackground bg = new PlaxBackground();
 * stateManager.attach(bg);
 * bg.setPage(PlaxBackground.loadPage(assetManager, "Parallax/Hiver.plax"), JmeAtlas.load(assetManager, "Parallax/Hiver.atlas"));
 * bg.speedConstantX = 60;
 * </pre>
 * Pages and atlases are assets: {@code .plax} (read with Kryo, as a libGDX game reads it), {@code .jplax} or
 * {@code .plaxpj}. The world is {@value #DEFAULT_WORLD_WIDTH} units wide, as a heart's, and as high as the screen's
 * aspect ratio makes it.
 */
public class PlaxBackground extends BaseAppState
{
	public static final float DEFAULT_WORLD_WIDTH = 40;

	/** Scroll speed for one frame only, then reset (e.g. from player movement). */
	public float speedConsumableX, speedConsumableY;
	public float speedConstantX, speedConstantY;
	/** False: the application calls {@link #act(float)} itself (a runner stepping a fixed time). */
	public boolean autoAct = true;

	private final float worldWidth;
	private final OrthographicCamera worldCamera = new OrthographicCamera();
	private final ParallaxPageReader reader = new ParallaxPageReader();
	private final Node root = new Node("parallax background");
	private final Node layers = new Node("parallax layers");

	private AssetManager assets;
	private ViewPort viewPort;
	private JmeBatch batch;
	private JmeGradient top, bottom;
	private WholePage_Model page, transfertPage;
	/** A page set before the state was initialized: shown once the screen size, so the world's height, is known. */
	private WholePage_Model pendingPage;
	private TextureAtlas pendingAtlas;
	private int screenWidth, screenHeight;

	public PlaxBackground()
	{this(DEFAULT_WORLD_WIDTH);}

	public PlaxBackground(float worldWidth)
	{
		this.worldWidth = worldWidth;
		root.attachChild(layers);
		root.setCullHint(CullHint.Never);
	}

	/** A page asset: a .plax (Kryo), or a .jplax / .plaxpj (JSON). */
	public static WholePage_Model loadPage(AssetManager assets, String path)
	{
		AssetInfo info = assets.locateAsset(new AssetKey<>(path));
		if (info == null)
			throw new AssetNotFoundException(path);
		if (path.endsWith(".plax"))
			return Utils_Page.loadPage(info.openStream());
		try (InputStream in = info.openStream())
		{return Utils_Page_Json.readPage(new String(in.readAllBytes(), StandardCharsets.UTF_8));}
		catch (IOException e)
		{throw new IllegalStateException("Could not read the page " + path, e);}
	}

	@Override
	protected void initialize(Application app)
	{
		assets = app.getAssetManager();
		Camera appCamera = app.getCamera();
		Camera camera = new Camera(appCamera.getWidth(), appCamera.getHeight());
		viewPort = app.getRenderManager().createPreView("parallax background", camera);
		viewPort.setClearFlags(true, true, true);
		viewPort.setBackgroundColor(com.jme3.math.ColorRGBA.Black);
		viewPort.attachScene(root);
		app.getViewPort().setClearColor(false);
		batch = new JmeBatch(assets, layers, 2);
		reader.setLayerEffects(new JmeLayerEffects(assets));
		resize(appCamera.getWidth(), appCamera.getHeight());
		if (pendingPage != null)
			setPage(pendingPage, pendingAtlas);
		pendingPage = null;
		pendingAtlas = null;
	}

	@Override
	protected void cleanup(Application app)
	{
		app.getRenderManager().removePreView(viewPort);
		app.getViewPort().setClearColor(true);
		batch.dispose();
	}

	@Override
	protected void onEnable()
	{root.setCullHint(CullHint.Never);}

	@Override
	protected void onDisable()
	{root.setCullHint(CullHint.Always);}

	/** Shows {@code model}, its layers built from {@code atlas}, from their decal positions. */
	public void setPage(WholePage_Model model, TextureAtlas atlas)
	{
		if (!isInitialized())
		{
			pendingPage = model;
			pendingAtlas = atlas;
			return;
		}
		build(model, atlas);
		page = model;
		transfertPage = null;
		reader.layers.clear();
		reader.resetTransfert();
		reader.addLayers(model.preloadValue);
		reader.setRepeatOnX(model.repeatOnX);
		reader.setRepeatOnY(model.repeatOnY);

		if (top != null)
			top.geometry().removeFromParent();
		if (bottom != null)
			bottom.geometry().removeFromParent();
		top = new JmeGradient(assets, model.topHalf_top, model.topHalf_bottom, model.topHalfSize, true, 0);
		bottom = new JmeGradient(assets, model.bottomHalf_top, model.bottomHalf_bottom, model.bottomHalfSize, false, 1);
		root.attachChildAt(top.geometry(), 0);
		root.attachChildAt(bottom.geometry(), 1);
	}

	/** Cross-fades into {@code model} over {@code seconds}, as Parallax_Heart.transfertIntoPage. */
	public void transfertIntoPage(WholePage_Model model, TextureAtlas atlas, float seconds)
	{
		build(model, atlas);
		transfertPage = model;
		reader.addLayersTransfert(model, seconds);
		if (top != null)
			top.transfertInto(model.topHalf_top, model.topHalf_bottom, seconds);
		if (bottom != null)
			bottom.transfertInto(model.bottomHalf_top, model.bottomHalf_bottom, seconds);
		if (!reader.isInTransfer())
			transferFinished();
	}

	/** Tints every layer toward {@code color} over {@code seconds}, not the gradients. */
	public void tintTo(Color color, float seconds)
	{reader.addColorTransfert(color, seconds);}

	/**
	 * Makes {@code hook} draw every EMPTY layer named {@code name}, as {@link ParallaxPageReader#setLayerHook}: through
	 * the {@link JmeBatch} it is handed, whose only draw is {@code draw(region, x, y, width, height)} (a region of a
	 * {@link JmeAtlas}); null removes it.
	 */
	public void setLayerHook(String name, LayerHook hook)
	{reader.setLayerHook(name, hook);}

	/**
	 * Draws every SEQUENCE layer's cycle from {@code seed} XOR the seed its page stores, as
	 * {@link ParallaxPageReader#setSequenceSeed}: a new ground each run.
	 */
	public void setSequenceSeed(int seed)
	{reader.setSequenceSeed(seed);}

	/** The SEQUENCE layers back to the seeds their pages store. */
	public void clearSequenceSeed()
	{reader.clearSequenceSeed();}

	/** Every layer back at its decal. */
	public void resetPositions()
	{reader.resetPositions();}

	/** Builds a page's layers for this world, once per atlas: a page already built is left as it is. */
	private void build(WholePage_Model model, TextureAtlas atlas)
	{
		if (model.preloadValue != null && model.getLoadedAtlas() == atlas)
			return;
		// forceLoad places the layers in the default world: this one.
		Gvars_Parallax.setWorldWidth(reader.getWorldWidth());
		Gvars_Parallax.setWorldHeight(reader.getWorldHeight());
		model.forceLoad(atlas);
	}

	private void transferFinished()
	{
		page = transfertPage;
		transfertPage = null;
	}

	@Override
	public void update(float tpf)
	{
		if (autoAct)
			act(tpf);
	}

	/** Scrolls and fades by {@code delta} seconds. */
	public void act(float delta)
	{
		if (top != null)
			top.act(delta);
		if (bottom != null)
			bottom.act(delta);
		reader.act(delta, speedConsumableX + speedConstantX, speedConsumableY + speedConstantY);
		if (transfertPage != null && !reader.isInTransfer())
			transferFinished();
		speedConsumableX = 0;
		speedConsumableY = 0;
	}

	@Override
	public void render(RenderManager renderManager)
	{
		Camera camera = viewPort.getCamera();
		if (camera.getWidth() != screenWidth || camera.getHeight() != screenHeight)
			resize(camera.getWidth(), camera.getHeight());

		if (top != null)
			top.update(screenWidth, screenHeight);
		if (bottom != null)
			bottom.update(screenWidth, screenHeight);

		float viewWidth = worldCamera.viewportWidth * worldCamera.zoom;
		float viewHeight = worldCamera.viewportHeight * worldCamera.zoom;
		batch.setView(worldCamera.position.x - viewWidth / 2, worldCamera.position.y - viewHeight / 2, viewWidth, viewHeight,
				screenWidth, screenHeight);
		batch.begin();
		reader.draw(worldCamera, batch);
		batch.end();

		root.updateLogicalState(0);
		root.updateGeometricState();
	}

	/** Keeps the world width, and gives the world the screen's aspect ratio, as Parallax_Heart.resize. */
	private void resize(int width, int height)
	{
		if (width <= 0 || height <= 0)
			return;
		screenWidth = width;
		screenHeight = height;
		float worldHeight = Utils_Parallax.calculateOtherDimension(true, worldWidth, width, height);
		reader.setWorldSize(worldWidth, worldHeight);
		// What setToOrtho sets, without its update(): the frustum is computed by a libGDX native, and the reader reads
		// only the view (ParallaxPageReader.draw).
		worldCamera.viewportWidth = worldWidth;
		worldCamera.viewportHeight = worldHeight;
		worldCamera.zoom = 1;
		worldCamera.position.set(worldWidth / 2, worldHeight / 2, 0);
	}

	public WholePage_Model getPage()
	{return page;}

	public ParallaxPageReader getReader()
	{return reader;}

	public float getWorldWidth()
	{return reader.getWorldWidth();}

	public float getWorldHeight()
	{return reader.getWorldHeight();}
}
