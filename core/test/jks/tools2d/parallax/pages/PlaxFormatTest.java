package jks.tools2d.parallax.pages;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.badlogic.gdx.graphics.Color;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jks.tools2d.parallax.heart.GVars_Serialization;

/** Guards the .plax (Kryo) and .plaxpj/.jplax (JSON) formats against the sample files written by the 2019 editor. */
class PlaxFormatTest
{
	private static final Path ROOT = Path.of(System.getProperty("parallax.repoRoot", ".."));
	static final ObjectMapper JSON = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	static
	{Json_MixIns.MIX_INS.forEach(JSON::addMixIn);}

	static List<Path> legacyPlaxFiles() throws IOException
	{
		List<Path> files = new ArrayList<>();
		// Copies of the editor's editor/Files and the demo's demo/assets (r130), so the library tests without them.
		try (Stream<Path> walk = Files.walk(ROOT.resolve("core/test-data/samples")))
		{files.addAll(walk.filter(p -> p.toString().endsWith(".plax")).sorted().collect(Collectors.toList()));}

		assertFalse(files.isEmpty(), "no sample .plax found under " + ROOT);
		return files;
	}

	/** .plax exports that have a .plaxpj project saved alongside them. */
	static List<Path> plaxWithProject() throws IOException
	{
		return legacyPlaxFiles().stream()
				.filter(p -> Files.exists(Path.of(p + "pj")))
				.collect(Collectors.toList());
	}

	@ParameterizedTest
	@MethodSource("legacyPlaxFiles")
	void readsEveryLegacyFile(Path plax) throws IOException
	{
		byte[] bytes = Files.readAllBytes(plax);
		assertNotEquals(WholePage_Model_Serializer.VERSION_MARKER, bytes[1], "sample files predate the versioned format");

		WholePage_Model page = read(bytes);

		assertTrue(page.pageModel.atlasName.endsWith(".atlas"), page.pageModel.atlasName);
		assertFalse(page.pageModel.pageList.isEmpty());
		for (Parallax_Model layer : page.pageModel.pageList)
		{
			assertFalse(layer.regionName.isBlank());
			assertTrue(layer.regionPosition >= 0);
			assertTrue(layer.sizeRatio > 0, "sizeRatio " + layer.sizeRatio);
		}
		for (Color color : colors(page))
			assertTrue(color.r >= 0 && color.r <= 1 && color.g >= 0 && color.g <= 1 && color.b >= 0 && color.b <= 1 && color.a >= 0 && color.a <= 1, color.toString());
	}

	/** Independent check of the legacy decoding: the export must match the project JSON saved in the same session. */
	@ParameterizedTest
	@MethodSource("plaxWithProject")
	void legacyExportMatchesItsJsonProject(Path plax) throws IOException
	{
		WholePage_Model exported = read(Files.readAllBytes(plax));

		JsonNode saving = JSON.readTree(Path.of(plax + "pj").toFile()).get("saving");
		WholePage_Model project = JSON.treeToValue(saving, WholePage_Model.class);

		// Exports only keep layers coming from the atlas.
		List<Parallax_Model> projectLayers = new ArrayList<>();
		JsonNode inside = saving.get("inside");
		for (int i = 0; i < project.pageModel.pageList.size(); i++)
			if (inside == null || inside.get(i).asBoolean())
				projectLayers.add(project.pageModel.pageList.get(i));

		assertEquals(project.pageModel.atlasName, exported.pageModel.atlasName);
		assertEquals(colors(project), colors(exported));
		assertEquals(project.repeatOnX, exported.repeatOnX);
		assertEquals(project.repeatOnY, exported.repeatOnY);
		assertEquals(projectLayers.size(), exported.pageModel.pageList.size());
		for (int i = 0; i < projectLayers.size(); i++)
			assertLayerEquals(projectLayers.get(i), exported.pageModel.pageList.get(i), false);
	}

	@ParameterizedTest
	@MethodSource("legacyPlaxFiles")
	void rewritingUsesTheVersionedFormatAndKeepsEverything(Path plax) throws IOException
	{
		WholePage_Model original = read(Files.readAllBytes(plax));
		original.pageModel.pageList.get(0).flipY = true;
		original.pageModel.pageList.get(0).mirror = true;
		assertFalse(original.useOriginalSize, "files older than format 4 stretch stripped regions");
		original.useOriginalSize = true;
		for (Parallax_Model layer : original.pageModel.pageList)
			assertEquals(Enum_LayerKind.IMAGE, layer.kind, "files older than format 5 hold image layers");
		original.pageModel.pageList.add(emptyLayer());
		original.pageModel.pageList.add(particleLayer());
		original.pageModel.pageList.add(glint(shaderLayer()));
		original.pageModel.pageList.add(sequenceLayer());
		original.pageModel.pageList.add(floorLayer());
		original.pageModel.pageList.add(lurkerLayer());
		original.pageModel.pageList.get(1).towerRadius = 12.5f;
		original.pageModel.pageList.get(1).towerShade = 0.3f;
		original.pageModel.pageList.get(1).towerCamera = 3.3f;
		animate(original.pageModel.pageList.get(2));
		original.fogColor = new Color(0.2f, 0.3f, 0.5f, 1);
		original.planetRadius = 650;

		byte[] rewritten = write(original);
		// Byte 0 is Kryo's reference marker for the page itself.
		assertEquals(WholePage_Model_Serializer.VERSION_MARKER, rewritten[1]);

		WholePage_Model reread = read(rewritten);
		assertPageEquals(original, reread);
		assertTrue(reread.pageModel.pageList.get(0).flipY, "flipY is stored since format 2");
		assertTrue(reread.pageModel.pageList.get(0).mirror, "mirror is stored since format 3");
		assertTrue(reread.useOriginalSize, "useOriginalSize is stored since format 4");
		Parallax_Model slot = reread.pageModel.pageList.get(reread.pageModel.pageList.size() - 6);
		assertEquals(Enum_LayerKind.EMPTY, slot.kind, "kind is stored since format 5");
		assertEquals("birds", slot.name, "name is stored since format 5");
		Parallax_Model snow = reread.pageModel.pageList.get(reread.pageModel.pageList.size() - 5);
		assertEquals("effects/snow.p", snow.particlesLibgdx, "the libGDX effect is stored since format 6");
		assertEquals("effects/snow.tscn", snow.particlesGodot, "the Godot scene is stored since format 6");
		assertEquals(Enum_ParticleAnchor.VIEW, snow.particlesAnchor, "the anchor is stored since format 6");
		Parallax_Model fog = reread.pageModel.pageList.get(reread.pageModel.pageList.size() - 4);
		assertEquals(Enum_LayerKind.SHADER, fog.kind, "SHADER is a kind since format 7");
		assertEquals(Enum_ShaderEffect.FOG, fog.shaderEffect, "the effect is stored since format 7");
		assertEquals(0.6f, fog.shaderAmplitude, "its amplitude");
		assertEquals(7.5f, fog.shaderWavelength, "its wavelength");
		assertEquals(-1.25f, fog.shaderSpeed, "its speed");
		assertEquals(0, fog.shaderHaze, "its depth haze is no longer stored since format 10");
		assertEquals(0.25f, reread.getFogStrength(), "it is the page's fog's strength, stored since format 10");
		assertEquals(0.45f, fog.glintStrength, "its glint is stored since format 17");
		assertEquals(new Color(0.2f, 0.3f, 0.5f, 1), reread.fogColor, "and the fog's colour");
		Parallax_Model ground = reread.pageModel.pageList.get(reread.pageModel.pageList.size() - 3);
		assertEquals(Enum_LayerKind.SEQUENCE, ground.kind, "SEQUENCE is a kind since format 8");
		assertEquals(3, ground.sequenceSegments.size(), "the segments are stored since format 8");
		assertEquals("parallax3", ground.sequenceSegments.get(1).regionName, "a segment's region");
		assertEquals(50, ground.sequenceSegments.get(1).weight, "and its weight");
		assertEquals(-1640531527, ground.sequenceSeed, "the seed, negative ones too");
		assertEquals(24, ground.sequenceLength, "the cycle's length");
		Parallax_Model floor = reread.pageModel.pageList.get(reread.pageModel.pageList.size() - 2);
		assertEquals(Enum_LayerKind.FLOOR, floor.kind, "FLOOR is a kind since format 11");
		assertEquals(Enum_FloorMapping.LINE_SCROLL, floor.floorMapping, "the mapping is stored since format 11");
		assertEquals(0.016f, floor.floorTopSpeed, "the top row's speed");
		assertEquals(48, floor.floorRows, "the rows");
		assertEquals(40, floor.floorHeight, "the box's height");
		assertEquals(12.5f, reread.pageModel.pageList.get(1).towerRadius, "a tower's radius is stored since format 12");
		assertEquals(0.3f, reread.pageModel.pageList.get(1).towerShade, "and its shade");
		assertEquals(650, reread.planetRadius, "the page's planet is stored since format 13");
		assertEquals(3.3f, reread.pageModel.pageList.get(1).towerCamera, "a tower's camera is stored since format 14");
		Parallax_Model lurker = reread.pageModel.pageList.get(reread.pageModel.pageList.size() - 1);
		assertEquals(Enum_LayerKind.LURKER, lurker.kind, "LURKER is a kind since format 15");
		assertEquals(-1640531527, lurker.lurkerSeed, "its seed is stored since format 15, negative ones too");
		assertEquals(5, lurker.lurkerOdds, "and its odds");
		assertArrayEquals(new float[] { 1, 0.25f, 3.5f, 2, -4, 1.75f }, lurker.lurkerCover, "and where it is covered");
		Parallax_Model palms = reread.pageModel.pageList.get(2);
		assertEquals("palms_anim", palms.animRegion, "an animated patch's frames are stored since format 16");
		assertEquals(412, palms.animX, "where it lies");
		assertEquals(-3, palms.animY, "below the region too");
		assertEquals(7.5f, palms.animFps, "and its frame rate");
	}

	/** An animated patch (format 16): the frames named palms_anim, 412 pixels across the layer's region, 7.5 a second. */
	static Parallax_Model animate(Parallax_Model layer)
	{
		layer.animRegion = "palms_anim";
		layer.animX = 412;
		layer.animY = -3;
		layer.animFps = 7.5f;
		return layer;
	}

	/** A glint on a SHADER layer's crests (format 17). */
	static Parallax_Model glint(Parallax_Model layer)
	{
		layer.glintStrength = 0.45f;
		return layer;
	}

	/** Format 16 has no glint: written there, a layer reads none. */
	@Test
	void format16FilesStillLoadWithoutAGlint() throws IOException
	{
		WholePage_Model original = read(Files.readAllBytes(ROOT.resolve("core/test-data/samples/hiver/Hiver.plax")));
		original.pageModel.pageList.add(animate(glint(shaderLayer())));

		byte[] written = write(original, 16);
		assertEquals(16, written[2], "format version");

		WholePage_Model reread = read(written);
		Parallax_Model fog = reread.pageModel.pageList.get(reread.pageModel.pageList.size() - 1);
		assertEquals("palms_anim", fog.animRegion, "format 16 has the animated patch");
		assertEquals(0, fog.glintStrength, "and no glint");
		original.pageModel.pageList.get(original.pageModel.pageList.size() - 1).glintStrength = 0;
		assertPageEquals(original, reread);
	}

	/** Format 15 has no animated patch: written there, a layer reads none, at 0, 0 and 12 frames a second. */
	@Test
	void format15FilesStillLoadWithoutAnAnimatedPatch() throws IOException
	{
		WholePage_Model original = read(Files.readAllBytes(ROOT.resolve("core/test-data/samples/hiver/Hiver.plax")));
		original.pageModel.pageList.get(1).lurkerSeed = 9;
		animate(original.pageModel.pageList.get(1));

		byte[] written = write(original, 15);
		assertEquals(15, written[2], "format version");

		WholePage_Model reread = read(written);
		assertEquals(9, reread.pageModel.pageList.get(1).lurkerSeed, "format 15 has the lurker's seed");
		assertEquals("", reread.pageModel.pageList.get(1).animRegion, "and no animated patch");
		assertEquals(0, reread.pageModel.pageList.get(1).animX);
		assertEquals(0, reread.pageModel.pageList.get(1).animY);
		assertEquals(Parallax_Model.DEFAULT_ANIM_FPS, reread.pageModel.pageList.get(1).animFps, "the default frame rate");
		original.pageModel.pageList.get(1).animRegion = "";
		original.pageModel.pageList.get(1).animX = 0;
		original.pageModel.pageList.get(1).animY = 0;
		original.pageModel.pageList.get(1).animFps = Parallax_Model.DEFAULT_ANIM_FPS;
		assertPageEquals(original, reread);
	}

	/** A LURKER layer: a figure the nearer layers hide, with its draws' seed, its odds and where it is covered. */
	static Parallax_Model lurkerLayer()
	{
		Parallax_Model lurker = new Parallax_Model();
		lurker.kind = Enum_LayerKind.LURKER;
		lurker.regionName = "parallax2";
		lurker.name = "hooded";
		lurker.lurkerSeed = -1640531527;
		lurker.lurkerOdds = 5;
		lurker.lurkerCover = new float[] { 1, 0.25f, 3.5f, 2, -4, 1.75f };
		lurker.sizeRatio = 0.2f;
		lurker.padX = 30;
		lurker.parallaxScalingSpeedX = 0.032f;
		return lurker;
	}

	/** Format 14 has no lurker fields: written there, a layer reads seed 0, odds 3 and no cover. */
	@Test
	void format14FilesStillLoadWithoutLurkerFields() throws IOException
	{
		WholePage_Model original = read(Files.readAllBytes(ROOT.resolve("core/test-data/samples/hiver/Hiver.plax")));
		original.pageModel.pageList.get(1).towerCamera = 3.3f;
		original.pageModel.pageList.get(1).lurkerSeed = 9;
		original.pageModel.pageList.get(1).lurkerOdds = 5;
		original.pageModel.pageList.get(1).lurkerCover = new float[] { 1, 0, 2 };

		byte[] written = write(original, 14);
		assertEquals(14, written[2], "format version");

		WholePage_Model reread = read(written);
		assertEquals(3.3f, reread.pageModel.pageList.get(1).towerCamera, "format 14 has the tower's camera");
		assertEquals(0, reread.pageModel.pageList.get(1).lurkerSeed, "and no lurker seed");
		assertEquals(3, reread.pageModel.pageList.get(1).lurkerOdds, "the default odds");
		assertEquals(0, reread.pageModel.pageList.get(1).lurkerCover.length, "no cover");
		original.pageModel.pageList.get(1).lurkerSeed = 0;
		original.pageModel.pageList.get(1).lurkerOdds = 3;
		original.pageModel.pageList.get(1).lurkerCover = new float[0];
		assertPageEquals(original, reread);
	}

	/** Format 13 has no tower camera: written there, a tower reads 0 (seen from far away). */
	@Test
	void format13FilesStillLoadWithoutATowerCamera() throws IOException
	{
		WholePage_Model original = read(Files.readAllBytes(ROOT.resolve("core/test-data/samples/hiver/Hiver.plax")));
		original.pageModel.pageList.get(1).towerRadius = 12.5f;
		original.pageModel.pageList.get(1).towerCamera = 3.3f;

		byte[] written = write(original, 13);
		assertEquals(13, written[2], "format version");

		WholePage_Model reread = read(written);
		assertEquals(12.5f, reread.pageModel.pageList.get(1).towerRadius, "format 13 has the tower");
		assertEquals(0, reread.pageModel.pageList.get(1).towerCamera, "and no camera");
		original.pageModel.pageList.get(1).towerCamera = 0;
		assertPageEquals(original, reread);
	}

	/** Format 12 has no planet: written there, the page reads radius 0 (flat). */
	@Test
	void format12FilesStillLoadWithoutAPlanet() throws IOException
	{
		WholePage_Model original = read(Files.readAllBytes(ROOT.resolve("core/test-data/samples/hiver/Hiver.plax")));
		original.planetRadius = 650;

		byte[] written = write(original, 12);
		assertEquals(12, written[2], "format version");

		WholePage_Model reread = read(written);
		assertEquals(0, reread.planetRadius, "format 12 has no planet");
		original.planetRadius = 0;
		assertPageEquals(original, reread);
	}

	/** Format 11 has no tower fields: written there, a layer reads radius 0 (flat) and the default shade. */
	@Test
	void format11FilesStillLoadWithoutTowers() throws IOException
	{
		WholePage_Model original = read(Files.readAllBytes(ROOT.resolve("core/test-data/samples/hiver/Hiver.plax")));
		Parallax_Model layer = original.pageModel.pageList.get(0);
		layer.towerRadius = 9;
		layer.towerShade = 0.1f;

		byte[] written = write(original, 11);
		assertEquals(11, written[2], "format version");

		WholePage_Model reread = read(written);
		Parallax_Model rereadLayer = reread.pageModel.pageList.get(0);
		assertEquals(0, rereadLayer.towerRadius, "format 11 has no tower");
		assertEquals(Parallax_Model.DEFAULT_TOWER_SHADE, rereadLayer.towerShade);
		layer.towerRadius = 0;
		layer.towerShade = Parallax_Model.DEFAULT_TOWER_SHADE;
		assertPageEquals(original, reread);
	}

	/**
	 * A FLOOR layer drawing Hiver.atlas's parallax1 as 48 rows of line scroll in a box 40% of the world high, from 0.06
	 * at the bottom to 0.016 at the top.
	 */
	static Parallax_Model floorLayer()
	{
		Parallax_Model floor = new Parallax_Model();
		floor.kind = Enum_LayerKind.FLOOR;
		floor.regionName = "parallax1";
		floor.floorMapping = Enum_FloorMapping.LINE_SCROLL;
		floor.floorTopSpeed = 0.016f;
		floor.floorRows = 48;
		floor.floorHeight = 40;
		floor.sizeRatio = 0.4f;
		floor.parallaxScalingSpeedX = 0.06f;
		return floor;
	}

	/** Format 10 has no floor fields: written there, a layer reads MODE7, 0, 96 rows and height 0. */
	@Test
	void format10FilesStillLoadWithoutFloors() throws IOException
	{
		WholePage_Model original = read(Files.readAllBytes(ROOT.resolve("core/test-data/samples/hiver/Hiver.plax")));
		Parallax_Model layer = original.pageModel.pageList.get(0);
		layer.floorMapping = Enum_FloorMapping.LINE_SCROLL;
		layer.floorTopSpeed = 0.5f;
		layer.floorRows = 7;
		layer.floorHeight = 30;

		byte[] written = write(original, 10);
		assertEquals(10, written[2], "format version");

		WholePage_Model reread = read(written);
		Parallax_Model rereadLayer = reread.pageModel.pageList.get(0);
		assertEquals(Enum_FloorMapping.MODE7, rereadLayer.floorMapping, "format 10 has no mapping");
		assertEquals(0, rereadLayer.floorTopSpeed);
		assertEquals(96, rereadLayer.floorRows);
		assertEquals(0, rereadLayer.floorHeight);
		layer.floorMapping = Enum_FloorMapping.MODE7;
		layer.floorTopSpeed = 0;
		layer.floorRows = 96;
		layer.floorHeight = 0;
		assertPageEquals(original, reread);
	}

	/** A SEQUENCE layer chaining three of Hiver.atlas's regions, the middle one twice as often. */
	static Parallax_Model sequenceLayer()
	{
		Parallax_Model ground = new Parallax_Model();
		ground.kind = Enum_LayerKind.SEQUENCE;
		ground.name = "ground";
		ground.sequenceSegments.add(new Sequence_Segment("parallax4", 1, 25));
		ground.sequenceSegments.add(new Sequence_Segment("parallax3", 0, 50));
		ground.sequenceSegments.add(new Sequence_Segment("parallax2", 0, 25));
		ground.sequenceSeed = -1640531527;
		ground.sequenceLength = 24;
		ground.sizeRatio = 0.4f;
		ground.padX = 0.5f;
		ground.parallaxScalingSpeedX = 0.08f;
		return ground;
	}

	/**
	 * Format 9 stored a FOG layer's depth haze, not a page fog: read there, the layer keeps its haze and the page's fog
	 * is the largest such haze, in the mist's white.
	 */
	@Test
	void format9FilesLoadTheirHazeAsThePageFog() throws IOException
	{
		WholePage_Model original = read(Files.readAllBytes(ROOT.resolve("core/test-data/samples/hiver/Hiver.plax")));
		original.pageModel.pageList.add(shaderLayer());
		Parallax_Model thinner = shaderLayer();
		thinner.shaderHaze = 0.1f;
		original.pageModel.pageList.add(thinner);
		original.fogColor = new Color(Color.RED);

		byte[] written = write(original, 9);
		assertEquals(9, written[2], "format version");

		WholePage_Model reread = read(written);
		assertEquals(0.25f, reread.pageModel.pageList.get(reread.pageModel.pageList.size() - 2).shaderHaze, "format 9 holds the haze");
		assertEquals(0.25f, reread.getFogStrength(), "the largest haze is the page's fog");
		assertEquals(new Color(WholePage_Model.FOG_R, WholePage_Model.FOG_G, WholePage_Model.FOG_B, 1), reread.fogColor,
				"format 9 has no fog colour: the mist's white");
		original.fogColor = reread.fogColor;
		assertPageEquals(original, reread);

		WholePage_Model upgraded = read(write(reread));
		assertEquals(0.25f, upgraded.getFogStrength(), "written again, the fog is the page's");
		assertEquals(0, upgraded.pageModel.pageList.get(upgraded.pageModel.pageList.size() - 2).shaderHaze, "and the haze gone");
	}

	/** Format 8 has no depth haze: written there, a FOG layer reads haze 0. */
	@Test
	void format8FilesStillLoadWithoutHaze() throws IOException
	{
		WholePage_Model original = read(Files.readAllBytes(ROOT.resolve("core/test-data/samples/hiver/Hiver.plax")));
		original.pageModel.pageList.add(shaderLayer());
		original.pageModel.pageList.add(sequenceLayer());

		byte[] written = write(original, 8);
		assertEquals(8, written[2], "format version");

		WholePage_Model reread = read(written);
		Parallax_Model fog = reread.pageModel.pageList.get(reread.pageModel.pageList.size() - 2);
		assertEquals(0, fog.shaderHaze, "format 8 has no haze");
		original.pageModel.pageList.get(original.pageModel.pageList.size() - 2).shaderHaze = 0;
		assertPageEquals(original, reread);
		assertEquals(24, reread.pageModel.pageList.get(reread.pageModel.pageList.size() - 1).sequenceLength);
	}

	/** Format 7 has no sequence fields: written there, a layer reads no segments, seed 0 and 16 slots. */
	@Test
	void format7FilesStillLoadWithoutSequences() throws IOException
	{
		WholePage_Model original = read(Files.readAllBytes(ROOT.resolve("core/test-data/samples/hiver/Hiver.plax")));
		original.pageModel.pageList.add(shaderLayer());
		Parallax_Model layer = original.pageModel.pageList.get(0);
		layer.sequenceSegments.add(new Sequence_Segment("parallax1", 0, 3));
		layer.sequenceSeed = 99;
		layer.sequenceLength = 5;

		byte[] written = write(original, 7);
		assertEquals(7, written[2], "format version");

		WholePage_Model reread = read(written);
		Parallax_Model rereadLayer = reread.pageModel.pageList.get(0);
		assertTrue(rereadLayer.sequenceSegments.isEmpty(), "format 7 has no segments");
		assertEquals(0, rereadLayer.sequenceSeed);
		assertEquals(16, rereadLayer.sequenceLength);
		layer.sequenceSegments.clear();
		layer.sequenceSeed = 0;
		layer.sequenceLength = 16;
		original.pageModel.pageList.get(original.pageModel.pageList.size() - 1).shaderHaze = 0;
		assertPageEquals(original, reread);
		assertEquals(Enum_ShaderEffect.FOG, reread.pageModel.pageList.get(reread.pageModel.pageList.size() - 1).shaderEffect);
	}

	/** A SHADER layer drawing an atlas region through FOG. */
	static Parallax_Model shaderLayer()
	{
		Parallax_Model fog = new Parallax_Model();
		fog.kind = Enum_LayerKind.SHADER;
		fog.regionName = "parallax4";
		fog.regionPosition = 1;
		fog.shaderEffect = Enum_ShaderEffect.FOG;
		fog.shaderAmplitude = 0.6f;
		fog.shaderWavelength = 7.5f;
		fog.shaderSpeed = -1.25f;
		fog.shaderHaze = 0.25f;
		fog.sizeRatio = 0.8f;
		fog.parallaxScalingSpeedX = 0.04f;
		return fog;
	}

	/** Format 6 has no shader fields: written there, a layer reads WAVE and no numbers. */
	@Test
	void format6FilesStillLoadWithoutShaders() throws IOException
	{
		WholePage_Model original = read(Files.readAllBytes(ROOT.resolve("core/test-data/samples/hiver/Hiver.plax")));
		original.pageModel.pageList.add(particleLayer());
		Parallax_Model layer = original.pageModel.pageList.get(0);
		layer.shaderEffect = Enum_ShaderEffect.FOG;
		layer.shaderAmplitude = 1;
		layer.shaderWavelength = 2;
		layer.shaderSpeed = 3;

		byte[] written = write(original, 6);
		assertEquals(6, written[2], "format version");

		WholePage_Model reread = read(written);
		Parallax_Model rereadLayer = reread.pageModel.pageList.get(0);
		assertEquals(Enum_ShaderEffect.WAVE, rereadLayer.shaderEffect, "format 6 has no shader effect");
		assertEquals(0, rereadLayer.shaderAmplitude);
		assertEquals(0, rereadLayer.shaderWavelength);
		assertEquals(0, rereadLayer.shaderSpeed);
		layer.shaderEffect = Enum_ShaderEffect.WAVE;
		layer.shaderAmplitude = layer.shaderWavelength = layer.shaderSpeed = 0;
		assertPageEquals(original, reread);
		assertEquals("effects/snow.p", reread.pageModel.pageList.get(reread.pageModel.pageList.size() - 1).particlesLibgdx);
	}

	/** A PARTICLES layer naming an effect per engine, emitted from the view. */
	static Parallax_Model particleLayer()
	{
		Parallax_Model snow = new Parallax_Model();
		snow.kind = Enum_LayerKind.PARTICLES;
		snow.name = "snow";
		snow.particlesLibgdx = "effects/snow.p";
		snow.particlesGodot = "effects/snow.tscn";
		snow.particlesAnchor = Enum_ParticleAnchor.VIEW;
		snow.sizeRatio = 0.3f;
		snow.parallaxScalingSpeedX = 0.05f;
		snow.decal_Y_Ratio = 90;
		return snow;
	}

	/** Format 5 has no particle fields: written there, a layer reads them as none, pinned to the layer. */
	@Test
	void format5FilesStillLoadWithoutParticles() throws IOException
	{
		WholePage_Model original = read(Files.readAllBytes(ROOT.resolve("core/test-data/samples/hiver/Hiver.plax")));
		original.pageModel.pageList.add(emptyLayer());
		Parallax_Model layer = original.pageModel.pageList.get(0);
		layer.particlesLibgdx = "a.p";
		layer.particlesGodot = "a.tscn";
		layer.particlesAnchor = Enum_ParticleAnchor.VIEW;

		byte[] written = write(original, 5);
		assertEquals(5, written[2], "format version");

		WholePage_Model reread = read(written);
		Parallax_Model rereadLayer = reread.pageModel.pageList.get(0);
		assertEquals(null, rereadLayer.particlesLibgdx, "format 5 has no libGDX effect");
		assertEquals(null, rereadLayer.particlesGodot, "nor a Godot scene");
		assertEquals(Enum_ParticleAnchor.LAYER, rereadLayer.particlesAnchor, "and pins to the layer");
		layer.particlesLibgdx = layer.particlesGodot = null;
		layer.particlesAnchor = Enum_ParticleAnchor.LAYER;
		assertPageEquals(original, reread);
		assertEquals(Enum_LayerKind.EMPTY, reread.pageModel.pageList.get(reread.pageModel.pageList.size() - 1).kind);
	}

	/** An EMPTY layer between the far and near layers, named for a game's hook. */
	static Parallax_Model emptyLayer()
	{
		Parallax_Model slot = new Parallax_Model();
		slot.kind = Enum_LayerKind.EMPTY;
		slot.name = "birds";
		slot.sizeRatio = 0.5f;
		slot.parallaxScalingSpeedX = 0.03f;
		slot.decal_Y_Ratio = 40;
		return slot;
	}

	@Test
	void format4FilesStillLoadAsImageLayers() throws IOException
	{
		WholePage_Model original = read(Files.readAllBytes(ROOT.resolve("core/test-data/samples/hiver/Hiver.plax")));
		original.useOriginalSize = true;
		original.pageModel.pageList.get(0).name = "named";

		byte[] written = write(original, 4);
		assertEquals(4, written[2], "format version");

		WholePage_Model reread = read(written);
		assertTrue(reread.useOriginalSize, "useOriginalSize is stored since format 4");
		assertEquals(null, reread.pageModel.pageList.get(0).name, "format 4 has no layer name");
		original.pageModel.pageList.get(0).name = null;
		assertPageEquals(original, reread);
	}

	/** Writing a kind to a format that cannot hold it would load as an image: the kind's field only goes in format 5. */
	@Test
	void anEmptyLayerOnlyRoundTripsInFormat5() throws IOException
	{
		WholePage_Model original = read(Files.readAllBytes(ROOT.resolve("core/test-data/samples/hiver/Hiver.plax")));
		original.pageModel.pageList.add(3, emptyLayer());

		WholePage_Model reread = read(write(original));
		assertPageEquals(original, reread);
		assertEquals(Enum_LayerKind.EMPTY, reread.pageModel.pageList.get(3).kind);
		assertEquals(Enum_LayerKind.IMAGE, reread.pageModel.pageList.get(4).kind);
	}

	private static byte[] write(WholePage_Model page, int version)
	{
		Kryo kryo = GVars_Serialization.prepareKryo();
		kryo.register(WholePage_Model.class, new WholePage_Model_Serializer(version));
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (Output output = new Output(bytes))
		{kryo.writeObject(output, page);}
		return bytes.toByteArray();
	}

	@Test
	void format3FilesStillLoadStretched() throws IOException
	{
		WholePage_Model original = read(Files.readAllBytes(ROOT.resolve("core/test-data/samples/hiver/Hiver.plax")));
		original.pageModel.pageList.get(0).mirror = true;
		original.useOriginalSize = true;

		Kryo format3 = GVars_Serialization.prepareKryo();
		format3.register(WholePage_Model.class, new WholePage_Model_Serializer(3));
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (Output output = new Output(bytes))
		{format3.writeObject(output, original);}
		byte[] written = bytes.toByteArray();
		assertEquals(3, written[2], "format version");

		WholePage_Model reread = read(written);
		assertTrue(reread.pageModel.pageList.get(0).mirror, "mirror is stored since format 3");
		assertFalse(reread.useOriginalSize, "format 3 has no useOriginalSize");
		original.useOriginalSize = false;
		assertPageEquals(original, reread);
	}

	/** .jplax and .plaxpj written before the field existed: Jackson leaves it at its default. */
	@Test
	void jsonWithoutUseOriginalSizeLoadsStretched() throws IOException
	{
		JsonNode saving = JSON.readTree(ROOT.resolve("core/test-data/samples/hiver/Hiver.plaxpj").toFile()).get("saving");
		assertFalse(saving.has("useOriginalSize"));
		assertFalse(JSON.treeToValue(saving, WholePage_Model.class).useOriginalSize);
	}

	@Test
	void format2FilesStillLoadWithoutMirror() throws IOException
	{
		WholePage_Model original = read(Files.readAllBytes(ROOT.resolve("core/test-data/samples/hiver/Hiver.plax")));
		original.pageModel.pageList.get(0).flipY = true;
		original.pageModel.pageList.get(0).mirror = true;

		Kryo format2 = GVars_Serialization.prepareKryo();
		format2.register(WholePage_Model.class, new WholePage_Model_Serializer(2));
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (Output output = new Output(bytes))
		{format2.writeObject(output, original);}
		byte[] written = bytes.toByteArray();
		assertEquals(2, written[2], "format version");

		WholePage_Model reread = read(written);
		assertTrue(reread.pageModel.pageList.get(0).flipY, "flipY is stored since format 2");
		assertFalse(reread.pageModel.pageList.get(0).mirror, "format 2 has no mirror");
		original.pageModel.pageList.get(0).mirror = false;
		assertPageEquals(original, reread);
	}

	@Test
	void jsonExportRoundTrips() throws IOException
	{
		WholePage_Model original = read(Files.readAllBytes(ROOT.resolve("core/test-data/samples/hiver/Hiver.plax")));
		original.pageModel.pageList.get(0).flipY = true;
		original.pageModel.pageList.get(0).mirror = true;
		original.useOriginalSize = true;
		original.pageModel.pageList.add(2, emptyLayer());
		original.pageModel.pageList.add(4, particleLayer());
		original.pageModel.pageList.add(5, animate(glint(shaderLayer())));
		original.pageModel.pageList.add(6, sequenceLayer());
		original.pageModel.pageList.add(7, floorLayer());
		original.pageModel.pageList.add(8, lurkerLayer());
		original.pageModel.pageList.get(0).towerRadius = 12.5f;
		original.pageModel.pageList.get(0).towerShade = 0.3f;
		original.pageModel.pageList.get(0).towerCamera = 3.3f;
		animate(original.pageModel.pageList.get(1));
		original.planetRadius = 650;

		String json = JSON.writeValueAsString(original);
		assertFalse(json.contains("preloadValue") || json.contains("completeRegionName") || json.contains("\"speed\""), json);
		assertFalse(json.contains("shaderHaze"), "a FOG layer's haze is no longer written (format 10)");
		assertTrue(json.contains("\"fogStrength\":0.25") && json.contains("\"fogColor\""), "the page's fog is: " + json);
		assertTrue(json.contains("\"planetRadius\":650.0"), "the page's planet is: " + json);

		assertPageEquals(original, JSON.readValue(json, WholePage_Model.class));
		String legacy = json.replace("\"fogStrength\":0.25,", "").replace("\"shaderEffect\":\"FOG\",", "\"shaderEffect\":\"FOG\",\"shaderHaze\":0.4,");
		assertFalse(legacy.contains("fogStrength"), legacy);
		assertEquals(0.4f, JSON.readValue(legacy, WholePage_Model.class).getFogStrength(), "a format 9 .jplax: its FOG layer's haze is the fog");
	}

	static WholePage_Model read(byte[] bytes)
	{
		InputStream stream = new ByteArrayInputStream(bytes);
		return Utils_Page.loadPage(stream);
	}

	private static byte[] write(WholePage_Model page)
	{
		GVars_Serialization.init();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (Output output = new Output(bytes))
		{GVars_Serialization.kryo.writeObject(output, page);}
		return bytes.toByteArray();
	}

	private static List<Color> colors(WholePage_Model page)
	{return List.of(page.topHalf_top, page.topHalf_bottom, page.bottomHalf_top, page.bottomHalf_bottom);}

	static void assertPageEquals(WholePage_Model expected, WholePage_Model actual)
	{
		assertEquals(colors(expected), colors(actual));
		assertEquals(expected.topHalfSize, actual.topHalfSize);
		assertEquals(expected.bottomHalfSize, actual.bottomHalfSize);
		assertEquals(expected.repeatOnX, actual.repeatOnX);
		assertEquals(expected.repeatOnY, actual.repeatOnY);
		assertEquals(expected.useOriginalSize, actual.useOriginalSize);
		assertEquals(expected.getFogStrength(), actual.getFogStrength(), "fog strength");
		assertEquals(expected.fogColor, actual.fogColor, "fog colour");
		assertEquals(expected.planetRadius, actual.planetRadius, "planet radius");
		assertEquals(expected.pageModel.atlasName, actual.pageModel.atlasName);
		assertEquals(expected.pageModel.outside, actual.pageModel.outside);
		assertEquals(expected.pageModel.pageList.size(), actual.pageModel.pageList.size());
		for (int i = 0; i < expected.pageModel.pageList.size(); i++)
			assertLayerEquals(expected.pageModel.pageList.get(i), actual.pageModel.pageList.get(i), true);
	}

	private static void assertLayerEquals(Parallax_Model expected, Parallax_Model actual, boolean checkNewFields)
	{
		String name = expected.getCompleteRegionName();
		assertEquals(expected.regionName, actual.regionName);
		assertEquals(expected.regionPosition, actual.regionPosition, name);
		assertEquals(expected.flipX, actual.flipX, name);
		if (checkNewFields)
		{
			assertEquals(expected.flipY, actual.flipY, name);
			assertEquals(expected.mirror, actual.mirror, name);
			assertEquals(expected.kind, actual.kind, name);
			assertEquals(expected.name, actual.name, name);
			assertEquals(expected.particlesLibgdx, actual.particlesLibgdx, name);
			assertEquals(expected.particlesGodot, actual.particlesGodot, name);
			assertEquals(expected.particlesAnchor, actual.particlesAnchor, name);
			assertEquals(expected.shaderEffect, actual.shaderEffect, name);
			assertEquals(expected.shaderAmplitude, actual.shaderAmplitude, name);
			assertEquals(expected.shaderWavelength, actual.shaderWavelength, name);
			assertEquals(expected.shaderSpeed, actual.shaderSpeed, name);
			assertEquals(expected.sequenceSegments.size(), actual.sequenceSegments.size(), name);
			for (int i = 0; i < expected.sequenceSegments.size(); i++)
			{
				Sequence_Segment want = expected.sequenceSegments.get(i), got = actual.sequenceSegments.get(i);
				assertEquals(want.regionName, got.regionName, name + " segment " + i);
				assertEquals(want.regionPosition, got.regionPosition, name + " segment " + i);
				assertEquals(want.weight, got.weight, name + " segment " + i);
			}
			assertEquals(expected.sequenceSeed, actual.sequenceSeed, name);
			assertEquals(expected.sequenceLength, actual.sequenceLength, name);
			assertEquals(expected.floorMapping, actual.floorMapping, name);
			assertEquals(expected.floorTopSpeed, actual.floorTopSpeed, name);
			assertEquals(expected.floorRows, actual.floorRows, name);
			assertEquals(expected.floorHeight, actual.floorHeight, name);
			assertEquals(expected.towerRadius, actual.towerRadius, name);
			assertEquals(expected.towerShade, actual.towerShade, name);
			assertEquals(expected.towerCamera, actual.towerCamera, name);
			assertEquals(expected.lurkerSeed, actual.lurkerSeed, name);
			assertEquals(expected.lurkerOdds, actual.lurkerOdds, name);
			assertArrayEquals(expected.lurkerCover, actual.lurkerCover, name);
			assertEquals(expected.animRegion, actual.animRegion, name);
			assertEquals(expected.animX, actual.animX, name);
			assertEquals(expected.animY, actual.animY, name);
			assertEquals(expected.animFps, actual.animFps, name);
			assertEquals(expected.glintStrength, actual.glintStrength, name);
		}
		assertEquals(expected.parallaxScalingSpeedX, actual.parallaxScalingSpeedX, name);
		assertEquals(expected.parallaxScalingSpeedY, actual.parallaxScalingSpeedY, name);
		assertEquals(expected.speedXAtRest, actual.speedXAtRest, name);
		assertEquals(expected.sizeRatio, actual.sizeRatio, name);
		assertEquals(expected.decal_X_Ratio, actual.decal_X_Ratio, name);
		assertEquals(expected.decal_Y_Ratio, actual.decal_Y_Ratio, name);
		assertEquals(expected.padX, actual.padX, name);
		assertEquals(expected.padXFactor, actual.padXFactor, name);
		assertEquals(expected.padY, actual.padY, name);
		assertEquals(expected.padYFactor, actual.padYFactor, name);
	}
}
