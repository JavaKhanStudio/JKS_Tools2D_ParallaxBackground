package jks.tools2d.parallax.pages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

/**
 * {@link Utils_Page_Json}, the browser build's loader, must read the same page as {@link Utils_Page} (Kryo). The pages of
 * core/test-data/onboard are copies of On Board's four carriage backdrops (onboard desktop/assets/game/wagon, 7dab6d0),
 * the game that first needed it.
 */
class JsonPageTest
{
	private static final Path ROOT = Path.of(System.getProperty("parallax.repoRoot", ".."));

	static List<Path> onBoardPages() throws IOException
	{
		try (Stream<Path> walk = Files.walk(ROOT.resolve("core/test-data/onboard")))
		{
			List<Path> pages = walk.filter(p -> p.toString().endsWith(".plax")).sorted().collect(Collectors.toList());
			assertEquals(4, pages.size(), "On Board has four carriages");
			return pages;
		}
	}

	/** Every page exported with its project: On Board's, the editor's samples and the demo's. */
	static List<Path> plaxWithProject() throws IOException
	{
		List<Path> pages = new ArrayList<>(onBoardPages());
		pages.addAll(PlaxFormatTest.plaxWithProject());
		return pages;
	}

	@ParameterizedTest
	@MethodSource("plaxWithProject")
	void projectReadsAsItsKryoExport(Path plax) throws IOException
	{
		WholePage_Model kryo = PlaxFormatTest.read(Files.readAllBytes(plax));
		WholePage_Model json = Utils_Page_Json.readPage(Files.readString(Path.of(plax + "pj"), StandardCharsets.UTF_8));

		assertFalse(kryo.pageModel.pageList.isEmpty());
		PlaxFormatTest.assertPageEquals(kryo, json);
	}

	/** A .jplax is what the editor's Jackson mapper writes of the page: the same mapper as PlaxFormatTest's. */
	@ParameterizedTest
	@MethodSource("plaxWithProject")
	void jsonExportReadsAsItsKryoExport(Path plax) throws IOException
	{
		WholePage_Model kryo = PlaxFormatTest.read(Files.readAllBytes(plax));
		kryo.pageModel.pageList.get(0).flipY = true;
		kryo.pageModel.pageList.get(0).mirror = true;
		kryo.useOriginalSize = true;
		kryo.topHalf_top = new Color(0.1f, 0.2f, 0.3f, 0.4f);

		PlaxFormatTest.assertPageEquals(kryo, Utils_Page_Json.readPage(PlaxFormatTest.JSON.writeValueAsString(kryo)));
	}

	@Test
	void projectKeepsOnlyTheLayersAnExportKeeps()
	{
		String project = "{\"saving\":{\"pageModel\":{\"atlasName\":\"a.atlas\",\"pageList\":["
				+ "{\"regionName\":\"sky\"},{\"regionName\":\"/home/me/loose.png\"},{\"regionName\":\"ground\",\"regionPosition\":2}]},"
				+ "\"inside\":[true,false,true]},\"outsideInfos\":null}";

		WholePage_Model page = Utils_Page_Json.readPage(project);

		assertEquals(2, page.pageModel.pageList.size());
		assertEquals("sky", page.pageModel.pageList.get(0).regionName);
		assertEquals("ground", page.pageModel.pageList.get(1).regionName);
		assertEquals(2, page.pageModel.pageList.get(1).regionPosition);
	}

	@Test
	void missingFieldsKeepTheirDefaults()
	{
		WholePage_Model page = Utils_Page_Json.readPage("{\"pageModel\":{\"pageList\":[{\"regionName\":null}]}}");
		WholePage_Model defaults = new WholePage_Model();

		assertEquals(defaults.topHalf_top, page.topHalf_top);
		assertEquals(defaults.topHalfSize, page.topHalfSize);
		assertTrue(page.repeatOnX);
		assertEquals("", page.pageModel.atlasName);
		Parallax_Model layer = page.pageModel.pageList.get(0);
		assertNull(layer.regionName);
		assertEquals(1, layer.sizeRatio);
		assertFalse(layer.mirror);
	}
}
