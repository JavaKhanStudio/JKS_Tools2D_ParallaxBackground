package jks.tools2d.parallax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.ParticleEmitter;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.GdxRuntimeException;

import jks.tools2d.parallax.browsertest.RecordingBatch;
import jks.tools2d.parallax.pages.Enum_LayerKind;
import jks.tools2d.parallax.pages.Enum_ParticleAnchor;
import jks.tools2d.parallax.pages.Utils_Page_Json;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * A page's PARTICLES layer loads its libGDX effect from the .p it names (core/test-data/particles/snow.p, written by
 * MakeSnow.java beside it), its images from the page's atlas, and draws it.
 */
class ParticlePageTest
{
	private static final Path ROOT = Path.of(System.getProperty("parallax.repoRoot", ".."));
	private static final String PAGE = "{\"repeatOnX\":true,\"pageModel\":{\"atlasName\":\"test.atlas\",\"pageList\":["
			+ "{\"regionName\":\"sky\"},"
			+ "{\"kind\":\"PARTICLES\",\"name\":\"snow\",\"particlesLibgdx\":\"%s\",\"particlesGodot\":\"snow.tscn\","
			+ "\"particlesAnchor\":\"VIEW\",\"sizeRatio\":0.3,\"decal_Y_Ratio\":90,\"parallaxScalingSpeedX\":0.05}]}}";
	private static final FileHandleResolver TEST_DATA = name -> new FileHandle(ROOT.resolve("core/test-data").resolve(name).toFile());

	@BeforeAll
	static void natives()
	{GdxNativesLoader.load();}

	/** An atlas of two regions on a page with no GL texture behind it. */
	private static TextureAtlas atlas()
	{
		Texture page = new Texture()
		{
			@Override
			public int getWidth()
			{return 256;}

			@Override
			public int getHeight()
			{return 256;}
		};
		TextureAtlas atlas = new TextureAtlas();
		atlas.addRegion("sky", page, 0, 0, 192, 108);
		atlas.addRegion("snowflake", page, 200, 0, 8, 8);
		return atlas;
	}

	@Test
	void theEffectLoadsFromThePageAndDraws()
	{
		TextureAtlas atlas = atlas();
		WholePage_Model page = Utils_Page_Json.readPage(String.format(PAGE, "particles/snow.p"));
		page.forceLoad(atlas, TEST_DATA);

		List<ParallaxLayer> layers = page.preloadValue;
		assertEquals(2, layers.size());
		ParallaxLayer snow = layers.get(1);
		assertEquals(Enum_LayerKind.PARTICLES, snow.kind);
		assertEquals(Enum_ParticleAnchor.VIEW, snow.getAnchor());
		ParallaxParticles effect = snow.getParticles();
		assertNotNull(effect, "the effect named by the page");
		ParticleEmitter emitter = effect.findEmitter("snow");
		assertNotNull(emitter, "the .p's emitter");
		assertSame(atlas.findRegion("snowflake").getTexture(), emitter.getSprites().first().getTexture(), "its image is the atlas's");

		OrthographicCamera camera = new OrthographicCamera();
		camera.setToOrtho(false, 40, 22.5f);
		camera.update();
		ParallaxPageReader reader = new ParallaxPageReader();
		reader.setRepeatOnX(true);
		reader.setWorldSize(40, 22.5f);
		reader.addLayers(layers);
		for (int i = 0; i < 60; i++)
			reader.act(1 / 60f, 10, 0);
		RecordingBatch batch = new RecordingBatch();
		reader.draw(camera, batch);

		// The sky is one 40-wide draw per tile, the snow 0.4-wide flakes.
		long flakes = batch.draws.stream().filter(draw -> Math.abs(draw[2] - 0.4f) < 1e-3f).count();
		assertTrue(flakes >= 10, flakes + " flakes after a second at 20 a second");
	}

	/** jME builds its pages so: without a folder, a particle layer has nothing to draw and draws nothing. */
	@Test
	void withoutAFolderTheLayerDrawsNothing()
	{
		WholePage_Model page = Utils_Page_Json.readPage(String.format(PAGE, "particles/snow.p"));
		page.forceLoad(atlas());
		assertNull(page.preloadValue.get(1).getParticles());
	}

	@Test
	void aMissingEffectFileFailsNamingIt()
	{
		WholePage_Model page = Utils_Page_Json.readPage(String.format(PAGE, "particles/hail.p"));
		GdxRuntimeException e = assertThrows(GdxRuntimeException.class, () -> page.forceLoad(atlas(), TEST_DATA));
		assertTrue(e.getMessage().contains("hail.p"), e.getMessage());
	}

	/** What the editor marks: each kind's engines, a reason for each one that does not draw it. */
	@Test
	void effectSupportSaysWhoDrawsParticles()
	{
		for (EffectSupport.Engine engine : EffectSupport.Engine.values())
		{
			assertTrue(EffectSupport.draws(Enum_LayerKind.IMAGE, engine), engine + " draws images");
			assertTrue(EffectSupport.draws(Enum_LayerKind.EMPTY, engine), engine + " calls hooks");
		}
		assertTrue(EffectSupport.draws(Enum_LayerKind.PARTICLES, EffectSupport.Engine.LIBGDX));
		assertTrue(EffectSupport.draws(Enum_LayerKind.PARTICLES, EffectSupport.Engine.BROWSER), "ReaderCases run in Chrome");
		assertTrue(EffectSupport.whyNot(Enum_LayerKind.PARTICLES, EffectSupport.Engine.GODOT).contains("r179"));
		assertTrue(EffectSupport.whyNot(Enum_LayerKind.PARTICLES, EffectSupport.Engine.JME).contains("EMPTY"));
	}
}
