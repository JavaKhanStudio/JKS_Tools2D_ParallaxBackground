package jks.tools2d.parallax.heart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Proxy;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.GdxNativesLoader;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.pages.Parallax_Model;
import jks.tools2d.parallax.pages.WholePage_Model;
import jks.tools2d.parallax.side.SquareBackground;

/**
 * Resize and the gradient squares, without a window: Gdx.graphics reports a fake window size and Gdx.gl is a proxy that
 * hands out handles and says "compiled" and "linked", so the real ShapeRenderer can be built.
 * <p>
 * A square's stored size is the part of the screen it leaves UNCOVERED: 0 covers the whole screen, 0.5 half, 1 nothing.
 */
class ParallaxHeartResizeTest
{
	private static int windowWidth, windowHeight;

	private final List<float[]> rects = new ArrayList<>();
	private final List<Color[]> rectColors = new ArrayList<>();
	/** The last matrix a shader received through glUniformMatrix4fv: what the GPU draws with. */
	private static float[] uploadedMatrix;
	private float savedWorldWidth, savedWorldHeight;

	@BeforeAll
	static void natives()
	{GdxNativesLoader.load();}

	@BeforeEach
	void fakeWindow()
	{
		savedWorldWidth = Gvars_Parallax.getWorldWidth();
		savedWorldHeight = Gvars_Parallax.getWorldHeight();
		window(1600, 900);

		Gdx.graphics = (Graphics) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Graphics.class }, (proxy, method, args) ->
		{
			switch (method.getName())
			{
				case "getWidth":
				case "getBackBufferWidth":
					return windowWidth;
				case "getHeight":
				case "getBackBufferHeight":
					return windowHeight;
				default:
					return defaultValue(method.getReturnType());
			}
		});
		Gdx.app = (Application) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Application.class }, (proxy, method, args) -> defaultValue(method.getReturnType()));
		Gdx.gl = Gdx.gl20 = (GL20) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { GL20.class }, (proxy, method, args) ->
		{
			boolean status = (method.getName().equals("glGetShaderiv") || method.getName().equals("glGetProgramiv"))
					&& ((int) args[1] == GL20.GL_COMPILE_STATUS || (int) args[1] == GL20.GL_LINK_STATUS);
			if (status)
				((IntBuffer) args[2]).put(0, 1);
			if (method.getName().equals("glUniformMatrix4fv") && args[3] instanceof float[])
				uploadedMatrix = ((float[]) args[3]).clone();
			if (method.getReturnType() == int.class)
				return 1; // a valid handle for glCreateShader, glCreateProgram, glGenBuffer...
			return method.getReturnType() == String.class ? "" : defaultValue(method.getReturnType());
		});
	}

	@AfterEach
	void restore()
	{
		Gdx.graphics = null;
		Gdx.app = null;
		Gdx.gl = Gdx.gl20 = null;
		Gvars_Parallax.setWorldWidth(savedWorldWidth);
		Gvars_Parallax.setWorldHeight(savedWorldHeight);
		Gvars_Parallax.setManager(null);
	}

	private static Object defaultValue(Class<?> type)
	{
		if (type == boolean.class)
			return false;
		if (type == float.class)
			return 0f;
		if (type == long.class)
			return 0L;
		if (type == double.class)
			return 0d;
		if (type.isPrimitive() && type != void.class)
			return 0;
		return null;
	}

	/** What the backend does before calling resize: the window reports its new size. */
	private static void window(int width, int height)
	{
		windowWidth = width;
		windowHeight = height;
	}

	/** Draws the squares with the heart's real renderer and returns the projection the shader was given. */
	private static Matrix4 drawnProjection(Parallax_Heart heart)
	{
		uploadedMatrix = null;
		heart.drawBackGround();
		assertNotNull(uploadedMatrix, "the squares were flushed to the shader");
		return new Matrix4(uploadedMatrix);
	}

	private ShapeRenderer recordingRenderer()
	{
		return new ShapeRenderer()
		{
			@Override
			public void rect(float x, float y, float width, float height, Color col1, Color col2, Color col3, Color col4)
			{
				rects.add(new float[] { x, y, width, height });
				rectColors.add(new Color[] { col1, col2, col3, col4 });
			}
		};
	}

	private static WholePage_Model page(float topUncovered, float bottomUncovered)
	{
		WholePage_Model page = new WholePage_Model()
		{
			@Override
			public List<ParallaxLayer> getDrawing(String relativePath, float worldWidth, float worldHeight)
			{return new ArrayList<>();}
		};
		page.topHalf_top = new Color(Color.RED);
		page.topHalf_bottom = new Color(Color.ORANGE);
		page.bottomHalf_top = new Color(Color.BLUE);
		page.bottomHalf_bottom = new Color(Color.NAVY);
		page.topHalfSize = topUncovered;
		page.bottomHalfSize = bottomUncovered;
		return page;
	}

	private static void assertRect(float[] rect, float x, float y, float width, float height)
	{
		assertEquals(x, rect[0], 1e-3f, "x");
		assertEquals(y, rect[1], 1e-3f, "y");
		assertEquals(width, rect[2], 1e-3f, "width");
		assertEquals(height, rect[3], 1e-3f, "height");
	}

	/** Projection of a setToOrtho2D(0, 0, width, height): x in [0, width] and y in [0, height] map to [-1, 1]. */
	private static void assertScreenProjection(Matrix4 projection, int width, int height)
	{
		assertEquals(2f / width, projection.val[Matrix4.M00], 1e-6f, "projection width");
		assertEquals(2f / height, projection.val[Matrix4.M11], 1e-6f, "projection height");
		assertEquals(-1f, projection.val[Matrix4.M03], 1e-6f);
		assertEquals(-1f, projection.val[Matrix4.M13], 1e-6f);
	}

	@Test
	void squaresLeaveTheirStoredPartUncovered()
	{
		SquareBackground top = new SquareBackground(Color.RED, Color.ORANGE, 0.25f, true);
		SquareBackground bottom = new SquareBackground(Color.BLUE, Color.NAVY, 0.25f, false);
		SquareBackground topFull = new SquareBackground(Color.RED, Color.ORANGE, 0, true);
		SquareBackground bottomNone = new SquareBackground(Color.BLUE, Color.NAVY, 1, false);

		ShapeRenderer renderer = recordingRenderer();
		top.draw(renderer);
		bottom.draw(renderer);
		topFull.draw(renderer);
		bottomNone.draw(renderer);

		assertEquals(3, rects.size(), "a square leaving the whole screen uncovered draws nothing");
		assertRect(rects.get(0), 0, 225, 1600, 675); // top: the bottom quarter is uncovered
		assertRect(rects.get(1), 0, 0, 1600, 675); // bottom: the top quarter is uncovered
		assertRect(rects.get(2), 0, 0, 1600, 900); // 0 covers everything
	}

	@Test
	void squareGradientRunsFromBottomColorToTopColor()
	{
		new SquareBackground(Color.RED, Color.NAVY, 0.5f, true).draw(recordingRenderer());

		// ShapeRenderer.rect corners: bottom left, bottom right, top right, top left.
		Color[] corners = rectColors.get(0);
		assertEquals(Color.NAVY, corners[0]);
		assertEquals(Color.NAVY, corners[1]);
		assertEquals(Color.RED, corners[2]);
		assertEquals(Color.RED, corners[3]);
	}

	@Test
	void settingASquareSizeUsesTheCurrentWindow()
	{
		SquareBackground top = new SquareBackground(Color.RED, Color.ORANGE, 0.5f, true);
		window(800, 1000);
		top.resize(800, 1000);

		top.setScreenPercentage(0.1f);
		top.draw(recordingRenderer());

		assertRect(rects.get(0), 0, 100, 800, 900);
	}

	@Test
	void resizeMovesTheSquaresTheWorldAndTheProjection()
	{
		Parallax_Heart heart = new Parallax_Heart();
		heart.setPage(page(0.5f, 0.75f));
		assertEquals(22.5f, Gvars_Parallax.getWorldHeight(), 1e-4f, "world height from the 16:9 startup window");

		window(1000, 1000);
		heart.resize(1000, 1000);

		assertEquals(40, Gvars_Parallax.getWorldWidth(), 1e-4f, "the world keeps its width");
		assertEquals(40, Gvars_Parallax.getWorldHeight(), 1e-4f, "and follows the new aspect ratio");
		assertEquals(40, heart.worldCamera.viewportWidth, 1e-4f);
		assertEquals(40, heart.worldCamera.viewportHeight, 1e-4f);
		assertEquals(20, heart.worldCamera.position.x, 1e-4f);
		assertEquals(20, heart.worldCamera.position.y, 1e-4f);
		assertScreenProjection(heart.shapeRender.getProjectionMatrix(), 1000, 1000);

		heart.shapeRender = recordingRenderer();
		heart.drawBackGround();
		assertEquals(2, rects.size());
		assertRect(rects.get(0), 0, 500, 1000, 500); // top square, half uncovered
		assertRect(rects.get(1), 0, 0, 1000, 250); // bottom square, three quarters uncovered
	}

	/** ShapeRenderer only rebuilds the matrix it draws with when told: editing getProjectionMatrix() alone was ignored. */
	@Test
	void resizeAfterTheFirstDrawChangesWhatTheSquaresAreDrawnWith()
	{
		Parallax_Heart heart = new Parallax_Heart();
		heart.setPage(page(0, 0));
		assertScreenProjection(drawnProjection(heart), 1600, 900);

		window(1100, 680);
		heart.resize(1100, 680);

		assertScreenProjection(drawnProjection(heart), 1100, 680);
	}

	@Test
	void startupSetsTheProjectionToTheWindow()
	{
		Parallax_Heart heart = new Parallax_Heart();
		assertScreenProjection(heart.shapeRender.getProjectionMatrix(), 1600, 900);
	}

	@Test
	void aPageSetAfterAResizeGetsTheNewWindowSize()
	{
		Parallax_Heart heart = new Parallax_Heart();
		window(1200, 600);
		heart.resize(1200, 600);

		heart.setPage(page(0, 0));
		heart.shapeRender = recordingRenderer();
		heart.drawBackGround();

		assertRect(rects.get(0), 0, 0, 1200, 600);
		assertRect(rects.get(1), 0, 0, 1200, 600);
	}

	@Test
	void minimizingKeepsEverything()
	{
		Parallax_Heart heart = new Parallax_Heart();
		heart.setPage(page(0.5f, 0.5f));

		// A minimized window reports 0x0.
		window(0, 0);
		heart.resize(0, 0);

		assertEquals(22.5f, Gvars_Parallax.getWorldHeight(), 1e-4f);
		assertEquals(22.5f, heart.worldCamera.viewportHeight, 1e-4f);
		assertScreenProjection(heart.shapeRender.getProjectionMatrix(), 1600, 900);
		heart.shapeRender = recordingRenderer();
		heart.drawBackGround();
		assertRect(rects.get(0), 0, 450, 1600, 450);
		assertRect(rects.get(1), 0, 0, 1600, 450);
	}

	@Test
	void withTheGamesCameraResizeOnlyMovesTheSquaresAndTheProjection()
	{
		OrthographicCamera gameCamera = new OrthographicCamera();
		gameCamera.setToOrtho(false, 30, 30);
		// The game owns the camera and the batch; resize never touches the batch.
		Parallax_Heart heart = new Parallax_Heart(gameCamera, null, 30, 30);
		heart.setPage(page(0.5f, 0.5f));

		window(2000, 1000);
		heart.resize(2000, 1000);

		assertEquals(30, Gvars_Parallax.getWorldHeight(), 1e-4f, "the game sized the world, resize leaves it");
		assertEquals(30, gameCamera.viewportWidth, 1e-4f);
		assertEquals(30, gameCamera.viewportHeight, 1e-4f);
		assertScreenProjection(heart.shapeRender.getProjectionMatrix(), 2000, 1000);

		heart.shapeRender = recordingRenderer();
		heart.drawBackGround();
		assertEquals(2, rects.size());
		assertRect(rects.get(0), 0, 500, 2000, 500);
		assertRect(rects.get(1), 0, 0, 2000, 500);
	}

	/** A page of one 16:9 layer, built through the AssetManager like a game's page, from an atlas made in memory. */
	private static WholePage_Model atlasPage(float decalX, float decalY)
	{
		TextureAtlas atlas = new TextureAtlas();
		atlas.addRegion("sky", new TextureRegion(new Texture(new Pixmap(192, 108, Pixmap.Format.RGBA8888))));
		Gvars_Parallax.setManager(new AssetManager()
		{
			@Override
			public synchronized <T> void load(String fileName, Class<T> type)
			{}

			@Override
			@SuppressWarnings("unchecked")
			public <T> T finishLoadingAsset(String fileName)
			{return (T) atlas;}

			@Override
			@SuppressWarnings("unchecked")
			public synchronized <T> T get(String fileName, Class<T> type)
			{return (T) atlas;}
		});

		Parallax_Model layer = new Parallax_Model();
		layer.regionName = "sky";
		layer.decal_X_Ratio = decalX;
		layer.decal_Y_Ratio = decalY;
		WholePage_Model page = new WholePage_Model("sky.atlas");
		page.pageModel.pageList.add(layer);
		return page;
	}

	/** Decals are percents of the world: one heart's world size must not move another heart's layers. */
	@Test
	void twoHeartsKeepTheirOwnWorldSize()
	{
		Parallax_Heart wide = new Parallax_Heart(new OrthographicCamera(40, 22.5f), null, atlasPage(10, 20), 40, 22.5f);
		Parallax_Heart tall = new Parallax_Heart(new OrthographicCamera(20, 40), null, atlasPage(10, 20), 20, 40);
		ParallaxLayer wideLayer = wide.parallaxReader.layers.get(0);
		ParallaxLayer tallLayer = tall.parallaxReader.layers.get(0);

		assertEquals(40, wideLayer.getWidth(), 1e-4f, "a layer is as wide as its own heart's world");
		assertEquals(20, tallLayer.getWidth(), 1e-4f);
		assertEquals(2, tallLayer.getCurrentDistanceX(), 1e-4f, "10% of 20");
		assertEquals(8, tallLayer.getCurrentDistanceY(), 1e-4f, "20% of 40");

		// Built after the tall heart, a game resizing its own full-window heart.
		Parallax_Heart owning = new Parallax_Heart();
		window(1000, 1000);
		owning.resize(1000, 1000);

		wide.parallaxReader.resetPositions();
		assertEquals(4, wideLayer.getCurrentDistanceX(), 1e-4f, "10% of 40");
		assertEquals(4.5f, wideLayer.getCurrentDistanceY(), 1e-4f, "20% of 22.5");
		wideLayer.setDecalPercentY(30);
		assertEquals(6.75f, wideLayer.getCurrentDistanceY(), 1e-4f, "30% of 22.5");

		// The incoming page is built lazily, long after the other hearts changed the default size.
		wide.transfertIntoPage(atlasPage(0, 50), 1);
		ParallaxLayer incoming = wide.parallaxReader.transferLayers.get(0);
		assertEquals(40, incoming.getWidth(), 1e-4f);
		assertEquals(11.25f, incoming.getCurrentDistanceY(), 1e-4f, "50% of 22.5");
	}
}
