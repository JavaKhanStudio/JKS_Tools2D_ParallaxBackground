package jks.tools2d.parallax.pages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.GdxNativesLoader;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;

/**
 * The editor saves a page from its loaded layers through {@link Utils_Page#buildFromPage}: a layer that comes back
 * from it must be the one the page was loaded from, EMPTY, PARTICLES (r191), SHADER (r180) and SEQUENCE (r182) layers
 * included.
 */
class BuildFromPageTest
{
	private static final Path ROOT = Path.of(System.getProperty("parallax.repoRoot", ".."));
	private static final FileHandleResolver TEST_DATA = name -> new FileHandle(ROOT.resolve("core/test-data").resolve(name).toFile());

	@BeforeAll
	static void natives()
	{GdxNativesLoader.load();}

	/** An atlas of the page's image and the snow effect's flake, on a page with no GL texture behind it. */
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

	private static WholePage_Model page()
	{
		WholePage_Model page = new WholePage_Model("test.atlas");
		page.pageModel.pageList = new ArrayList<>();

		Parallax_Model sky = new Parallax_Model();
		sky.regionName = "sky";
		sky.flipY = true;
		sky.mirror = true;
		sky.parallaxScalingSpeedX = 0.01f;
		sky.padX = 2;
		page.pageModel.pageList.add(sky);

		Parallax_Model birds = new Parallax_Model();
		birds.kind = Enum_LayerKind.EMPTY;
		birds.name = "birds";
		birds.sizeRatio = 0.5f;
		birds.decal_X_Ratio = 25;
		page.pageModel.pageList.add(birds);

		Parallax_Model snow = PlaxFormatTest.particleLayer();
		snow.particlesLibgdx = "particles/snow.p";
		page.pageModel.pageList.add(snow);

		Parallax_Model fog = PlaxFormatTest.shaderLayer();
		fog.regionName = "sky";
		fog.regionPosition = 0;
		page.pageModel.pageList.add(fog);

		Parallax_Model ground = new Parallax_Model();
		ground.kind = Enum_LayerKind.SEQUENCE;
		ground.sequenceSegments.add(new Sequence_Segment("sky", 0, 3));
		ground.sequenceSegments.add(new Sequence_Segment("snowflake", 0, 1));
		ground.sequenceSeed = 7;
		ground.sequenceLength = 9;
		ground.sizeRatio = 0.25f;
		ground.padX = 1;
		page.pageModel.pageList.add(ground);
		return page;
	}

	/** What the editor writes: each loaded layer back to its stored form, under the region it was loaded from. */
	private static WholePage_Model saved(WholePage_Model loaded)
	{
		WholePage_Model saved = new WholePage_Model(loaded.pageModel.atlasName);
		saved.pageModel.pageList = new ArrayList<>();
		List<ParallaxLayer> layers = loaded.preloadValue;
		for (int i = 0; i < layers.size(); i++)
		{
			Parallax_Model stored = loaded.pageModel.pageList.get(i);
			saved.pageModel.pageList.add(Utils_Page.buildFromPage(layers.get(i), stored.regionName, stored.regionPosition));
		}
		return saved;
	}

	@Test
	void emptyAndParticleLayersSaveAsThemselves()
	{
		WholePage_Model page = page();
		page.forceLoad(atlas(), TEST_DATA);
		assertNotNull(page.preloadValue.get(2).getParticles(), "the effect loaded");

		assertSameLayers(page, saved(page));
	}

	/** Loaded without a folder for its effects (the editor before phase 4), a PARTICLES layer still names them. */
	@Test
	void particleFilesSurviveAnEffectThatDidNotLoad()
	{
		WholePage_Model page = page();
		page.forceLoad(atlas());
		assertNull(page.preloadValue.get(2).getParticles(), "no folder, no effect");

		assertSameLayers(page, saved(page));
	}

	private static void assertSameLayers(WholePage_Model page, WholePage_Model saved)
	{
		PlaxFormatTest.assertPageEquals(page, saved);
		// assertPageEquals only reads the floats it was given: check the kinds by name, so a failure says which.
		assertEquals(Enum_LayerKind.EMPTY, saved.pageModel.pageList.get(1).kind);
		assertEquals(Enum_LayerKind.PARTICLES, saved.pageModel.pageList.get(2).kind);
		assertEquals("particles/snow.p", saved.pageModel.pageList.get(2).particlesLibgdx);
		assertEquals(Enum_ParticleAnchor.VIEW, saved.pageModel.pageList.get(2).particlesAnchor);
		assertEquals(Enum_LayerKind.SHADER, saved.pageModel.pageList.get(3).kind);
		assertEquals(Enum_ShaderEffect.FOG, saved.pageModel.pageList.get(3).shaderEffect);
		assertEquals(Enum_LayerKind.SEQUENCE, saved.pageModel.pageList.get(4).kind);
		assertEquals("snowflake", saved.pageModel.pageList.get(4).sequenceSegments.get(1).regionName);
	}

	/** A game's seed redraws the cycle on screen, never the seed the editor saves. */
	@Test
	void aSequenceSavesItsStoredSeedNotTheGames()
	{
		WholePage_Model page = page();
		page.forceLoad(atlas());
		ParallaxPageReader reader = new ParallaxPageReader();
		reader.addLayers(page.preloadValue);
		reader.setSequenceSeed(123456);
		assertEquals(123456 ^ 7, page.preloadValue.get(4).getDrawnSeed());

		assertSameLayers(page, saved(page));
		assertEquals(7, saved(page).pageModel.pageList.get(4).sequenceSeed);
	}
}
