package jks.tools2d.parallax.heart;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.utils.Disposable;

import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.Utils_Parallax;
import jks.tools2d.parallax.pages.Utils_Page;
import jks.tools2d.parallax.pages.WholePage_Model;
import jks.tools2d.parallax.side.SquareBackground;

/**
 * Entry point for games: owns the camera, batch and background, loads a page and scrolls/draws it.
 * <pre>
 * heart = new Parallax_Heart("myParallax.plax");   // in create()
 * heart.screenSpeedConstantX = 100;                // optional auto-scroll
 * heart.act(delta); heart.render();                // in render()
 * heart.resize(width, height);                     // in resize()
 * heart.dispose();                                 // in dispose()
 * </pre>
 */
public class Parallax_Heart implements Disposable
{
	public OrthographicCamera worldCamera;
	public SpriteBatch batch;
	public ParallaxPageReader parallaxReader;

	// Background
	public ShapeRenderer shapeRender;
	public SquareBackground topSquare;
	public SquareBackground bottomSquare;

	public WholePage_Model currentPage;
	public WholePage_Model currentTransfertPage;

	/** Scroll speed applied for one frame only, then reset (e.g. from player movement). */
	public float screenSpeedConsumableX;
	public float screenSpeedConstantX;

	public float screenSpeedConsumableY;
	public float screenSpeedConstantY;

	/** Folder that external (non-internal) atlases are loaded from, see {@link WholePage_Model#getDrawing(String)}. */
	public String relativePath = "";

	private static final int defaultWidth = 40;
	private boolean ownsBatch;

	public Parallax_Heart()
	{
		Gvars_Parallax.setWorldWidth(defaultWidth);
		Gvars_Parallax.setWorldHeight(Utils_Parallax.calculateOtherDimension(true, defaultWidth, Gdx.graphics.getWidth(), Gdx.graphics.getHeight()));

		worldCamera = new OrthographicCamera();
		worldCamera.setToOrtho(false, Gvars_Parallax.getWorldWidth(), Gvars_Parallax.getWorldHeight());

		batch = new SpriteBatch();
		ownsBatch = true;
		init();
	}

	public Parallax_Heart(String internalPath)
	{
		this();
		setPage(Utils_Page.loadPage(internalPath));
	}

	public Parallax_Heart(OrthographicCamera worldCamera, SpriteBatch batch, float worldWidth, float worldHeight)
	{
		this.worldCamera = worldCamera;
		this.batch = batch;
		Gvars_Parallax.setWorldWidth(worldWidth);
		Gvars_Parallax.setWorldHeight(worldHeight);
		init();
	}

	public Parallax_Heart(OrthographicCamera worldCamera, SpriteBatch batch, WholePage_Model pageModel, float worldWidth, float worldHeight)
	{
		this(worldCamera, batch, worldWidth, worldHeight);
		setPage(pageModel);
	}

	/** @deprecated {@code staticCamera} was never used, use {@link #Parallax_Heart(OrthographicCamera, SpriteBatch, float, float)}. */
	@Deprecated
	public Parallax_Heart(OrthographicCamera worldCamera, OrthographicCamera staticCamera, SpriteBatch batch, float worldWidth, float worldHeight)
	{this(worldCamera, batch, worldWidth, worldHeight);}

	/** @deprecated {@code staticCamera} was never used, use {@link #Parallax_Heart(OrthographicCamera, SpriteBatch, WholePage_Model, float, float)}. */
	@Deprecated
	public Parallax_Heart(OrthographicCamera worldCamera, OrthographicCamera staticCamera, SpriteBatch batch, WholePage_Model pageModel, float worldWidth, float worldHeight)
	{this(worldCamera, batch, pageModel, worldWidth, worldHeight);}

	private void init()
	{
		shapeRender = new ShapeRenderer();
		shapeRender.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		parallaxReader = new ParallaxPageReader();
	}

	public void setPage(WholePage_Model model)
	{Parallax_Utils_Page.setPage(this, model);}

	public void transfertIntoPage(WholePage_Model model, float intoXSec)
	{Parallax_Utils_Page.transfertIntoPage(this, model, intoXSec);}

	public void act(float delta)
	{
		if (topSquare != null)
			topSquare.act(delta);

		if (bottomSquare != null)
			bottomSquare.act(delta);

		parallaxReader.act(delta, screenSpeedConsumableX + screenSpeedConstantX, screenSpeedConsumableY + screenSpeedConstantY);
		if (currentTransfertPage != null && !parallaxReader.isInTransfer())
			Parallax_Utils_Page.transferFinished(this);

		screenSpeedConsumableX = 0;
		screenSpeedConsumableY = 0;
	}

	public void render()
	{
		worldCamera.update();
		drawBackGround();
		batch.setProjectionMatrix(worldCamera.combined);
		batch.enableBlending();
		batch.begin();
		parallaxReader.draw(worldCamera, batch);
		batch.end();
	}

	public void drawBackGround()
	{
		if (topSquare == null && bottomSquare == null)
			return;

		shapeRender.begin(ShapeType.Filled);

		if (topSquare != null)
			topSquare.draw(shapeRender);

		if (bottomSquare != null)
			bottomSquare.draw(shapeRender);

		shapeRender.end();
	}

	/**
	 * Keeps the world width and adapts the world height to the new aspect ratio. Only needed when the parallax owns its
	 * camera (no-arg or path constructors).
	 */
	public void resize(int width, int height)
	{
		if (width <= 0 || height <= 0)
			return;

		if (ownsBatch)
		{
			Gvars_Parallax.setWorldHeight(Utils_Parallax.calculateOtherDimension(true, Gvars_Parallax.getWorldWidth(), width, height));
			worldCamera.setToOrtho(false, Gvars_Parallax.getWorldWidth(), Gvars_Parallax.getWorldHeight());
		}

		shapeRender.getProjectionMatrix().setToOrtho2D(0, 0, width, height);

		if (topSquare != null)
			topSquare.resize(width, height);
		if (bottomSquare != null)
			bottomSquare.resize(width, height);
	}

	public String getAtlasName()
	{return currentPage == null ? null : currentPage.pageModel.getAtlasName();}

	/** Releases the renderers, and the batch and atlases if this heart created them. */
	@Override
	public void dispose()
	{
		shapeRender.dispose();
		if (ownsBatch)
			batch.dispose();
		if (currentPage != null)
			currentPage.disposeOwnedAtlas();
		if (currentTransfertPage != null && currentTransfertPage != currentPage)
			currentTransfertPage.disposeOwnedAtlas();
	}
}
