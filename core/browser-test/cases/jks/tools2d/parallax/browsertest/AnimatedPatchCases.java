package jks.tools2d.parallax.browsertest;

import static jks.tools2d.parallax.browsertest.Check.equal;
import static jks.tools2d.parallax.browsertest.Check.isTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.pages.Enum_LayerKind;
import jks.tools2d.parallax.pages.Enum_ShaderEffect;

/**
 * ParallaxPageReader's cases on an animated patch (format 16, docs/animated-layers.md): after each tile of an IMAGE or
 * SHADER layer, its patch's frame of the reader's clock, at the tile's corner plus its place scaled to the drawn size.
 * Shared builders in {@link ReaderFixtures}.
 */
final class AnimatedPatchCases extends ReaderFixtures
{
	/** The layer's image, in pixels, and the patch's frames' size and place in it. */
	private static final int IMAGE_WIDTH = 1920, IMAGE_HEIGHT = 1080, FRAME_WIDTH = 200, FRAME_HEIGHT = 100, AT_X = 412, AT_Y = 50;
	/** Each frame's left edge on the atlas page, 300 pixels apart: a draw's u says which frame it drew. */
	private static final int FRAME_STEP = 300, PAGE = 4096;

	static List<BrowserCase> all()
	{
		return Arrays.asList(
				new BrowserCase("reader: anAnimatedPatchIsDrawnAfterEachTileAtTheClocksFrame", () -> new AnimatedPatchCases().anAnimatedPatchIsDrawnAfterEachTileAtTheClocksFrame()),
				new BrowserCase("reader: aFlippedTileMirrorsItsPatch", () -> new AnimatedPatchCases().aFlippedTileMirrorsItsPatch()),
				new BrowserCase("reader: onlyImageAndShaderLayersDrawAPatch", () -> new AnimatedPatchCases().onlyImageAndShaderLayersDrawAPatch()));
	}

	/**
	 * At 0.125 s, 10 frames a second, frame 1; at 0.325 s, frame 3 of 3: frame 0. Every tile is followed by its patch,
	 * in all four repeat modes, an IMAGE and a SHADER layer alike.
	 */
	void anAnimatedPatchIsDrawnAfterEachTileAtTheClocksFrame()
	{
		boolean[][] modes = { { true, false }, { false, true }, { true, true }, { false, false } };
		for (boolean shader : new boolean[] { false, true })
			for (boolean[] mode : modes)
			{
				String repeat = (shader ? "SHADER" : "IMAGE") + ", repeat X " + mode[0] + ", Y " + mode[1];
				ParallaxPageReader reader = reader(mode[0], mode[1]);
				reader.setLayerEffects(new RecordingEffects());
				ParallaxLayer layer = patched(shader);
				reader.addLayers(list(layer));

				draws.clear();
				reader.act(0.125f, 0, 0);
				reader.draw(camera, batch);
				int tiles = checkPatches(layer, 1, repeat + ", at 0.125 s");
				isTrue(mode[0] || mode[1] ? tiles >= 2 : tiles == 1, repeat + ": " + tiles + " tiles");

				draws.clear();
				reader.act(0.2f, 0, 0);
				reader.draw(camera, batch);
				equal(tiles, checkPatches(layer, 0, repeat + ", at 0.325 s"), repeat + ": the same tiles");
			}
	}

	/** Flipped on X, the patch lies as far from the tile's right edge as it lay from its left, and is reversed with it. */
	void aFlippedTileMirrorsItsPatch()
	{
		ParallaxPageReader reader = reader(true, false);
		ParallaxLayer layer = patched(false);
		layer.setFlipX(true);
		reader.addLayers(list(layer));
		reader.act(0.125f, 0, 0);
		reader.draw(camera, batch);

		float scale = layer.getRegionWidth() / IMAGE_WIDTH;
		isTrue(draws.size() >= 4, draws.size() + " draws");
		for (int i = 0; i < draws.size(); i += 2)
		{
			float[] tile = draws.get(i), patch = draws.get(i + 1);
			isTrue(tile[2] < 0 && patch[2] < 0, "both reversed");
			// A reversed draw starts at its right edge.
			equal(tile[0] - AT_X * scale, patch[0], 1e-4f, "draw " + i + ": the patch's right edge, AT_X from the tile's");
			equal(FRAME_WIDTH * scale, -patch[2], 1e-4f, "its width");
		}
	}

	/** A LURKER layer (drawn as an image for now) and a tower keep the numbers but draw no patch. */
	void onlyImageAndShaderLayersDrawAPatch()
	{
		ParallaxPageReader reader = reader(true, false);
		ParallaxLayer lurker = new ParallaxLayer(Enum_LayerKind.LURKER, new ArrayList<>(Arrays.asList(region(IMAGE_WIDTH, IMAGE_HEIGHT))), true, 40, 0.02f, 0.02f, 0.3f);
		lurker.setAnim("fronds", AT_X, AT_Y, 10);
		lurker.setAnimFrames(frames());
		reader.addLayers(list(lurker));
		reader.draw(camera, batch);
		for (float[] draw : draws)
			equal(lurker.getRegionWidth(), draw[2], 1e-4f, "only the lurker's tiles");

		draws.clear();
		ParallaxLayer tower = patched(false);
		tower.setTower(10, 0.65f);
		reader = reader(true, false);
		reader.addLayers(list(tower));
		reader.draw(camera, batch);
		isTrue(!draws.isEmpty(), "the tower draws");
		for (float[] draw : draws)
			equal(tower.getRegionHeight(), Math.abs(draw[3]), 1e-4f, "only the tower's strips, as high as the layer");
	}

	/**
	 * The draws are tile, patch, tile, patch...: each patch at its tile's corner plus its place, scaled, drawing
	 * {@code frame}. Returns how many tiles.
	 */
	private int checkPatches(ParallaxLayer layer, int frame, String when)
	{
		float scaleX = layer.getRegionWidth() / IMAGE_WIDTH, scaleY = layer.getRegionHeight() / IMAGE_HEIGHT;
		equal(0, draws.size() % 2, when + ": a patch per tile, " + draws.size() + " draws");
		for (int i = 0; i < draws.size(); i += 2)
		{
			float[] tile = draws.get(i), patch = draws.get(i + 1);
			String draw = when + ", tile " + i / 2;
			equal(layer.getRegionWidth(), tile[2], 1e-4f, draw + ": the tile first");
			equal(tile[0] + AT_X * scaleX, patch[0], 1e-4f, draw + ": the patch's x");
			equal(tile[1] + AT_Y * scaleY, patch[1], 1e-4f, draw + ": its y");
			equal(FRAME_WIDTH * scaleX, patch[2], 1e-4f, draw + ": its width");
			equal(FRAME_HEIGHT * scaleY, patch[3], 1e-4f, draw + ": its height");
			// Inset by half a texel: within the frame's span on the page.
			isTrue(patch[8] > (float) (frame * FRAME_STEP) / PAGE && patch[9] < (float) (frame * FRAME_STEP + FRAME_WIDTH) / PAGE,
					draw + ": frame " + frame + ", drew u " + patch[8] + " to " + patch[9]);
		}
		return draws.size() / 2;
	}

	/** A 12 x 6.75 layer (an IMAGE or a WAVE one) with a patch of three frames at 10 a second. */
	private static ParallaxLayer patched(boolean shader)
	{
		ParallaxLayer layer = shader ? ParallaxLayer.shader(region(IMAGE_WIDTH, IMAGE_HEIGHT), 40, 0.3f, Enum_ShaderEffect.WAVE, 0.5f, 2, 1)
				: new ParallaxLayer(region(IMAGE_WIDTH, IMAGE_HEIGHT), true, 40, 0.02f, 0.02f, 0.3f);
		layer.setPadX(2);
		layer.setPadY(1);
		layer.setDecalPercentX(10);
		layer.setDecalPercentY(20);
		layer.setAnim("fronds", AT_X, AT_Y, 10);
		layer.setAnimFrames(frames());
		return layer;
	}

	/** Three frames, side by side on a page with no GL texture behind it. */
	private static List<TextureRegion> frames()
	{
		Texture page = new Texture()
		{
			@Override
			public int getWidth()
			{return PAGE;}

			@Override
			public int getHeight()
			{return PAGE;}
		};
		List<TextureRegion> frames = new ArrayList<>();
		for (int i = 0; i < 3; i++)
			frames.add(new TextureRegion(page, i * FRAME_STEP, 0, FRAME_WIDTH, FRAME_HEIGHT));
		return frames;
	}
}
