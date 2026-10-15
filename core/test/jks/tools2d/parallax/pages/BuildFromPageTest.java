package jks.tools2d.parallax.pages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.GdxRuntimeException;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.TestGdx;

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
		Texture page = TestGdx.unbackedTexture(256, 256);
		TextureAtlas atlas = new TextureAtlas();
		atlas.addRegion("sky", page, 0, 0, 192, 108);
		atlas.addRegion("snowflake", page, 200, 0, 8, 8);
		// An animated patch's three frames, the second named anything else in between: atlas order, by name.
		atlas.addRegion("fronds", page, 0, 120, 40, 20);
		atlas.addRegion("other", page, 0, 200, 4, 4);
		atlas.addRegion("fronds", page, 40, 120, 40, 20);
		atlas.addRegion("fronds", page, 80, 120, 40, 20);
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
		// Wrapped round a tower.
		sky.towerRadius = 12.5f;
		sky.towerShade = 0.4f;
		sky.towerCamera = 3.3f;
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

		Parallax_Model fog = PlaxFormatTest.glint(PlaxFormatTest.shaderLayer());
		fog.regionName = "sky";
		fog.regionPosition = 0;
		PlaxFormatTest.animate(fog).animRegion = "fronds";
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

		Parallax_Model floor = PlaxFormatTest.floorLayer();
		floor.regionName = "sky";
		page.pageModel.pageList.add(floor);

		Parallax_Model lurker = PlaxFormatTest.lurkerLayer();
		lurker.regionName = "snowflake";
		page.pageModel.pageList.add(lurker);
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
		assertEquals("fronds", saved.pageModel.pageList.get(3).animRegion, "its animated patch");
		assertEquals(412, saved.pageModel.pageList.get(3).animX, "where it lies");
		assertEquals(-3, saved.pageModel.pageList.get(3).animY);
		assertEquals(7.5f, saved.pageModel.pageList.get(3).animFps, "its frame rate");
		assertEquals(0.45f, saved.pageModel.pageList.get(3).glintStrength, "its glint");
		assertEquals(Enum_LayerKind.SEQUENCE, saved.pageModel.pageList.get(4).kind);
		assertEquals("snowflake", saved.pageModel.pageList.get(4).sequenceSegments.get(1).regionName);
		assertEquals(Enum_LayerKind.FLOOR, saved.pageModel.pageList.get(5).kind);
		assertEquals(Enum_FloorMapping.LINE_SCROLL, saved.pageModel.pageList.get(5).floorMapping);
		assertEquals(48, saved.pageModel.pageList.get(5).floorRows);
		assertEquals(12.5f, saved.pageModel.pageList.get(0).towerRadius, "the tower's radius");
		assertEquals(0.4f, saved.pageModel.pageList.get(0).towerShade, "and its shade");
		assertEquals(3.3f, saved.pageModel.pageList.get(0).towerCamera, "and where its camera stands");
		assertEquals(Enum_LayerKind.LURKER, saved.pageModel.pageList.get(6).kind);
		assertEquals(-1640531527, saved.pageModel.pageList.get(6).lurkerSeed, "the lurker's seed");
		assertEquals(5, saved.pageModel.pageList.get(6).lurkerOdds, "its odds");
		assertEquals(6, saved.pageModel.pageList.get(6).lurkerCover.length, "and its cover");
		assertNotSame(page.preloadValue.get(6).getLurkerCover(), saved.pageModel.pageList.get(6).lurkerCover,
				"saved as a copy: the editor works the layer's cover out again at its next save");
	}

	/** An animated patch's frames are the atlas's regions of its name, in atlas order, found when the page loads. */
	@Test
	void anAnimatedPatchFindsItsFramesInTheAtlas()
	{
		WholePage_Model page = page();
		TextureAtlas atlas = atlas();
		page.forceLoad(atlas);

		List<TextureRegion> frames = page.preloadValue.get(3).getAnimFrames();
		assertEquals(3, frames.size(), "every region named fronds");
		for (int i = 0; i < 3; i++)
			assertSame(atlas.getRegions().get(i == 0 ? 2 : i + 3), frames.get(i), "frame " + i + " in atlas order");
		assertNull(page.preloadValue.get(0).getAnimFrames(), "a layer without a patch has none");
	}

	/** A patch whose frames the atlas does not have fails the load, naming them, as a missing layer region does. */
	@Test
	void anAnimatedPatchWithoutFramesFailsTheLoad()
	{
		WholePage_Model page = page();
		page.pageModel.pageList.get(3).animRegion = "palms";
		GdxRuntimeException thrown = assertThrows(GdxRuntimeException.class, () -> page.forceLoad(atlas()));
		assertTrue(thrown.getMessage().contains("'palms'"), thrown.getMessage());
	}

	/** A layer in front of a floor moves at the speed the reader plants it at, and saves the speed it was given. */
	@Test
	void aPlantedLayerSavesItsOwnSpeed()
	{
		WholePage_Model page = page();
		Parallax_Model tree = new Parallax_Model();
		tree.regionName = "sky";
		tree.parallaxScalingSpeedX = 0.04f;
		page.pageModel.pageList.add(tree);
		page.forceLoad(atlas());
		ParallaxPageReader reader = new ParallaxPageReader();
		reader.addLayers(page.preloadValue);
		reader.act(1 / 60f, 10, 0);
		assertEquals(0.06f, page.preloadValue.get(7).getActingSpeedX(), 1e-6f, "standing at the floor's foot: its bottom row's speed");

		assertSameLayers(page, saved(page));
		assertEquals(0.04f, saved(page).pageModel.pageList.get(7).parallaxScalingSpeedX);
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
