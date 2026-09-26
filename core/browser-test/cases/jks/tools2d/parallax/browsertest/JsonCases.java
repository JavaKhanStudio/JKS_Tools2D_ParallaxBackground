package jks.tools2d.parallax.browsertest;

import static jks.tools2d.parallax.browsertest.Check.equal;
import static jks.tools2d.parallax.browsertest.Check.isFalse;
import static jks.tools2d.parallax.browsertest.Check.isTrue;

import java.util.ArrayList;
import java.util.List;

import jks.tools2d.parallax.pages.Parallax_Model;
import jks.tools2d.parallax.pages.Utils_Page_Json;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * core/test JsonPageTest in the browser: Utils_Page_Json, the loader a browser game uses, must read every saved page as
 * Kryo reads its .plax on the JVM.
 */
final class JsonCases
{
	static List<BrowserCase> all(final Fixtures fixtures)
	{
		List<BrowserCase> cases = new ArrayList<>();
		for (final String name : fixtures.names())
			cases.add(new BrowserCase("json: " + name + " reads as its .plax", () -> readsAsItsPlax(fixtures, name)));
		cases.add(new BrowserCase("json: projectKeepsOnlyTheLayersAnExportKeeps", JsonCases::projectKeepsOnlyTheLayersAnExportKeeps));
		cases.add(new BrowserCase("json: missingFieldsKeepTheirDefaults", JsonCases::missingFieldsKeepTheirDefaults));
		return cases;
	}

	static void readsAsItsPlax(Fixtures fixtures, String name)
	{
		WholePage_Model page = Utils_Page_Json.readPage(fixtures.json(name));
		isFalse(page.pageModel.pageList.isEmpty(), "no layer");
		String difference = PageDump.firstDifference(fixtures.expectedDump(name), PageDump.of(page));
		if (difference != null)
			throw new AssertionError(difference);
	}

	static void projectKeepsOnlyTheLayersAnExportKeeps()
	{
		String project = "{\"saving\":{\"pageModel\":{\"atlasName\":\"a.atlas\",\"pageList\":["
				+ "{\"regionName\":\"sky\"},{\"regionName\":\"/home/me/loose.png\"},{\"regionName\":\"ground\",\"regionPosition\":2}]},"
				+ "\"inside\":[true,false,true]},\"outsideInfos\":null}";

		WholePage_Model page = Utils_Page_Json.readPage(project);

		equal(2, page.pageModel.pageList.size(), "layers");
		equal("sky", page.pageModel.pageList.get(0).regionName, "first layer");
		equal("ground", page.pageModel.pageList.get(1).regionName, "second layer");
		equal(2, page.pageModel.pageList.get(1).regionPosition, "regionPosition");
	}

	static void missingFieldsKeepTheirDefaults()
	{
		WholePage_Model page = Utils_Page_Json.readPage("{\"pageModel\":{\"pageList\":[{\"regionName\":null}]}}");
		WholePage_Model defaults = new WholePage_Model();

		equal(defaults.topHalf_top, page.topHalf_top, "topHalf_top");
		equal(defaults.topHalfSize, page.topHalfSize, 0, "topHalfSize");
		isTrue(page.repeatOnX, "repeatOnX");
		equal("", page.pageModel.atlasName, "atlasName");
		Parallax_Model layer = page.pageModel.pageList.get(0);
		equal(null, layer.regionName, "regionName");
		equal(1, layer.sizeRatio, 0, "sizeRatio");
		isFalse(layer.mirror, "mirror");
	}
}
