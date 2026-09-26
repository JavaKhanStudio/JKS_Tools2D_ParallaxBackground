package jks.tools2d.parallax.pages;

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
		for (String dir : new String[] { "editor/Files", "demo/assets" })
			try (Stream<Path> walk = Files.walk(ROOT.resolve(dir)))
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

		byte[] rewritten = write(original);
		// Byte 0 is Kryo's reference marker for the page itself.
		assertEquals(WholePage_Model_Serializer.VERSION_MARKER, rewritten[1]);

		WholePage_Model reread = read(rewritten);
		assertPageEquals(original, reread);
		assertTrue(reread.pageModel.pageList.get(0).flipY, "flipY is stored since format 2");
		assertTrue(reread.pageModel.pageList.get(0).mirror, "mirror is stored since format 3");
		assertTrue(reread.useOriginalSize, "useOriginalSize is stored since format 4");
	}

	@Test
	void format3FilesStillLoadStretched() throws IOException
	{
		WholePage_Model original = read(Files.readAllBytes(ROOT.resolve("demo/assets/hiver/Hiver.plax")));
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
		JsonNode saving = JSON.readTree(ROOT.resolve("demo/assets/hiver/Hiver.plaxpj").toFile()).get("saving");
		assertFalse(saving.has("useOriginalSize"));
		assertFalse(JSON.treeToValue(saving, WholePage_Model.class).useOriginalSize);
	}

	@Test
	void format2FilesStillLoadWithoutMirror() throws IOException
	{
		WholePage_Model original = read(Files.readAllBytes(ROOT.resolve("demo/assets/hiver/Hiver.plax")));
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
		WholePage_Model original = read(Files.readAllBytes(ROOT.resolve("demo/assets/hiver/Hiver.plax")));
		original.pageModel.pageList.get(0).flipY = true;
		original.pageModel.pageList.get(0).mirror = true;
		original.useOriginalSize = true;

		String json = JSON.writeValueAsString(original);
		assertFalse(json.contains("preloadValue") || json.contains("completeRegionName") || json.contains("\"speed\""), json);

		assertPageEquals(original, JSON.readValue(json, WholePage_Model.class));
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
