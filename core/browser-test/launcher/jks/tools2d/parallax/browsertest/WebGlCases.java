package jks.tools2d.parallax.browsertest;

import static jks.tools2d.parallax.browsertest.Check.isTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.ScreenUtils;

import jks.tools2d.parallax.GdxLayerEffects;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.pages.Enum_ShaderEffect;
import jks.tools2d.parallax.pages.Utils_Etc2_Atlas;

/**
 * What only a browser with WebGL can check, so not in BrowserSuite (its cases run on the JVM too, without GL): the SHADER
 * layers' effects compile as GLSL ES 1.0, and FOG draws what its formula says (r180); an atlas asking mipmaps of a
 * non-power-of-two page still draws (r212); a tiled layer shows no line at its joins (r218). Run by BrowserTestApp.
 */
final class WebGlCases
{
	private WebGlCases()
	{}

	static List<BrowserCase> all()
	{
		return Arrays.asList(
				new BrowserCase("webgl: effectShadersCompile", WebGlCases::effectShadersCompile),
				new BrowserCase("webgl: fogThinsAsItsFormulaSays", WebGlCases::fogThinsAsItsFormulaSays),
				new BrowserCase("webgl: npotMipMapAtlasDrawsThroughLoad", WebGlCases::npotMipMapAtlasDrawsThroughLoad),
				new BrowserCase("webgl: npotMipMapAtlasDrawsThroughAssetManager", WebGlCases::npotMipMapAtlasDrawsThroughAssetManager),
				new BrowserCase("webgl: tileJoinsDoNotReadPastTheRegion", WebGlCases::tileJoinsDoNotReadPastTheRegion));
	}

	static void effectShadersCompile()
	{
		List<String> failed = new ArrayList<>();
		for (Enum_ShaderEffect effect : Enum_ShaderEffect.values())
		{
			ShaderProgram program = new ShaderProgram(GdxLayerEffects.vertex(), GdxLayerEffects.fragment(effect));
			if (!program.isCompiled())
				failed.add(effect + ": " + program.getLog());
			else
				for (String uniform : new String[] { "u_region", "u_size", "u_effect" })
					if (program.fetchUniformLocation(uniform, false) < 0)
						failed.add(effect + " has no " + uniform);
			program.dispose();
		}
		isTrue(failed.isEmpty(), failed.toString());
	}

	/**
	 * A white SHADER layer through FOG at full amplitude, drawn by a reader over black: each pixel's level is
	 * 1 - n(x / wavelength, y / wavelength), the sum of sines GdxLayerEffects.FOG computes, at 8 bits.
	 */
	static void fogThinsAsItsFormulaSays()
	{
		int size = 64;
		Pixmap white = new Pixmap(4, 4, Format.RGBA8888);
		white.setColor(Color.WHITE);
		white.fill();
		Texture texture = new Texture(white);
		white.dispose();
		FrameBuffer frame = new FrameBuffer(Format.RGBA8888, size, size, false);
		SpriteBatch batch = new SpriteBatch();
		ParallaxPageReader reader = new ParallaxPageReader();
		try
		{
			OrthographicCamera camera = new OrthographicCamera();
			camera.setToOrtho(false, 40, 40);
			camera.update();
			reader.setWorldSize(40, 40);
			ParallaxLayer fog = ParallaxLayer.shader(new TextureRegion(texture), 40, 1, Enum_ShaderEffect.FOG, 1, 10, 0);
			List<ParallaxLayer> layers = new ArrayList<>();
			layers.add(fog);
			reader.addLayers(layers);

			frame.begin();
			Gdx.gl.glClearColor(0, 0, 0, 1);
			Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
			batch.setProjectionMatrix(camera.combined);
			batch.begin();
			reader.draw(camera, batch);
			batch.end();

			int worst = 0;
			String at = "";
			for (int py = 4; py < size; py += 9)
				for (int px = 3; px < size; px += 7)
				{
					byte[] pixel = ScreenUtils.getFrameBufferPixels(px, py, 1, 1, false);
					int level = pixel[0] & 0xFF;
					float x = (px + 0.5f) * 40 / size / 10, y = (py + 0.5f) * 40 / size / 10;
					double tau = 2 * Math.PI;
					double n = (Math.sin(tau * 0.875 * x + 2 * Math.sin(0.5 * tau * y)) + Math.sin(tau * (2.125 * x - 0.5 * y) + 1.3)
							+ Math.sin(tau * (2.875 * x + 0.8 * y) + 2.9)) / 6 + 0.5;
					int expected = (int) Math.round(255 * (1 - n));
					if (Math.abs(level - expected) > worst)
					{
						worst = Math.abs(level - expected);
						at = px + "," + py + ": " + level + " for " + expected;
					}
				}
			frame.end();
			isTrue(worst <= 2, "FOG off by " + worst + "/255 at " + at);
		}
		finally
		{
			reader.dispose();
			batch.dispose();
			frame.dispose();
			texture.dispose();
		}
	}

	/** calm.atlas as it ships ({@code filter: MipMap,MipMap}, a 5020x5160 page), loaded as the heart's getDrawing does. */
	static void npotMipMapAtlasDrawsThroughLoad()
	{
		TextureAtlas atlas = Utils_Etc2_Atlas.load(Gdx.files.internal("calm-mipmap.atlas"));
		try
		{checkDraws(atlas);}
		finally
		{atlas.dispose();}
	}

	/** The same atlas through an AssetManager, as WholePage_Model.preload() loads it. */
	static void npotMipMapAtlasDrawsThroughAssetManager()
	{
		AssetManager manager = new AssetManager();
		try
		{
			Utils_Etc2_Atlas.register(manager);
			manager.load("calm-mipmap.atlas", TextureAtlas.class);
			manager.finishLoadingAsset("calm-mipmap.atlas");
			checkDraws(manager.get("calm-mipmap.atlas", TextureAtlas.class));
		}
		finally
		{manager.dispose();}
	}

	/**
	 * Draws the atlas's Clouds over black and counts the pixels that are not: WebGL 1 leaves a mipmapped
	 * non-power-of-two texture incomplete, and an incomplete texture samples black.
	 */
	private static void checkDraws(TextureAtlas atlas)
	{
		while (Gdx.gl.glGetError() != GL20.GL_NO_ERROR)
			;
		int size = 32;
		FrameBuffer frame = new FrameBuffer(Format.RGBA8888, size, size, false);
		SpriteBatch batch = new SpriteBatch();
		try
		{
			TextureRegion clouds = atlas.findRegion("Clouds");
			isTrue(clouds != null, "calm-mipmap.atlas has no Clouds");
			OrthographicCamera camera = new OrthographicCamera();
			camera.setToOrtho(false, size, size);
			camera.update();
			frame.begin();
			Gdx.gl.glClearColor(0, 0, 0, 1);
			Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
			batch.setProjectionMatrix(camera.combined);
			batch.begin();
			batch.draw(clouds, 0, 0, size, size);
			batch.end();
			byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, size, size, false);
			frame.end();
			int lit = 0;
			for (int i = 0; i < pixels.length; i += 4)
				if ((pixels[i] & 0xFF) > 16 || (pixels[i + 1] & 0xFF) > 16 || (pixels[i + 2] & 0xFF) > 16)
					lit++;
			int error = Gdx.gl.glGetError();
			isTrue(lit > size * size / 4, lit + " of " + size * size + " pixels not black; GL error " + error);
			isTrue(error == GL20.GL_NO_ERROR, "GL error " + error + " loading or drawing it");
		}
		finally
		{
			batch.dispose();
			frame.dispose();
		}
	}

	/**
	 * r218: a white 4x4 region with transparent texels on both sides, as in an atlas packed without duplicatePadding,
	 * tiled on X four times magnified and filtered Linear over black. Sampling at a join used to mix in the transparent
	 * texel next to the region: a grey column at every join. Every pixel of the strip's middle row must stay white.
	 */
	static void tileJoinsDoNotReadPastTheRegion()
	{
		Pixmap pixels = new Pixmap(8, 4, Format.RGBA8888);
		pixels.setColor(0, 0, 0, 0);
		pixels.fill();
		pixels.setColor(Color.WHITE);
		pixels.fillRectangle(2, 0, 4, 4);
		Texture texture = new Texture(pixels);
		pixels.dispose();
		texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
		int size = 64;
		FrameBuffer frame = new FrameBuffer(Format.RGBA8888, size, size, false);
		SpriteBatch batch = new SpriteBatch();
		ParallaxPageReader reader = new ParallaxPageReader();
		try
		{
			OrthographicCamera camera = new OrthographicCamera();
			camera.setToOrtho(false, 40, 40);
			camera.update();
			reader.setWorldSize(40, 40);
			reader.setRepeatOnX(true);
			List<ParallaxLayer> layers = new ArrayList<>();
			// 10 units on a 40-unit, 64-pixel view: 16 pixels a tile, 4 per texel; joins at a quarter of a pixel.
			ParallaxLayer layer = new ParallaxLayer(new TextureRegion(texture, 2, 0, 4, 4), true, 10, 0, 0, 1);
			layer.setCurrentDistanceX(0.15f);
			layers.add(layer);
			reader.addLayers(layers);

			frame.begin();
			Gdx.gl.glClearColor(0, 0, 0, 1);
			Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
			batch.setProjectionMatrix(camera.combined);
			batch.begin();
			reader.draw(camera, batch);
			batch.end();
			byte[] frameBytes = ScreenUtils.getFrameBufferPixels(0, 0, size, size, false);
			frame.end();

			// The strip's rows: those with a white pixel anywhere (a column may be a join).
			int top = -1, bottom = -1;
			for (int y = 0; y < size; y++)
				for (int x = 0; x < size; x++)
					if ((frameBytes[(y * size + x) * 4] & 0xFF) > 250)
					{
						if (top < 0)
							top = y;
						bottom = y;
						break;
					}
			isTrue(top >= 0, "the strip was not drawn");
			int row = (top + bottom) / 2, darkest = 255, at = -1;
			for (int x = 0; x < size; x++)
			{
				int level = frameBytes[(row * size + x) * 4] & 0xFF;
				if (level < darkest)
				{
					darkest = level;
					at = x;
				}
			}
			isTrue(darkest >= 250, "a join drew " + darkest + "/255 at x " + at + " of row " + row);
		}
		finally
		{
			reader.dispose();
			batch.dispose();
			frame.dispose();
			texture.dispose();
		}
	}
}
