package jks.tools2d.parallax.browsertest;

import static jks.tools2d.parallax.browsertest.Check.equal;
import static jks.tools2d.parallax.browsertest.Check.isFalse;
import static jks.tools2d.parallax.browsertest.Check.isTrue;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.utils.GdxRuntimeException;

import jks.tools2d.parallax.pages.Enum_LayerKind;
import jks.tools2d.parallax.pages.Enum_ParticleAnchor;
import jks.tools2d.parallax.pages.Enum_ShaderEffect;
import jks.tools2d.parallax.pages.Parallax_Model;
import jks.tools2d.parallax.pages.Sequence_Segment;
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
		cases.add(new BrowserCase("json: emptyLayerKeepsItsKindAndName", JsonCases::emptyLayerKeepsItsKindAndName));
		cases.add(new BrowserCase("json: unknownKindFails", JsonCases::unknownKindFails));
		cases.add(new BrowserCase("json: particleLayerKeepsItsEffectsAndAnchor", JsonCases::particleLayerKeepsItsEffectsAndAnchor));
		cases.add(new BrowserCase("json: unknownAnchorFails", JsonCases::unknownAnchorFails));
		cases.add(new BrowserCase("json: shaderLayerKeepsItsEffectAndNumbers", JsonCases::shaderLayerKeepsItsEffectAndNumbers));
		cases.add(new BrowserCase("json: unknownEffectFails", JsonCases::unknownEffectFails));
		cases.add(new BrowserCase("json: sequenceLayerKeepsItsSegmentsSeedAndLength", JsonCases::sequenceLayerKeepsItsSegmentsSeedAndLength));
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
		equal(Enum_LayerKind.IMAGE, layer.kind, "kind");
		equal(null, layer.name, "name");
	}

	static void emptyLayerKeepsItsKindAndName()
	{
		WholePage_Model page = Utils_Page_Json.readPage("{\"pageModel\":{\"pageList\":[{\"regionName\":\"sky\"},"
				+ "{\"kind\":\"EMPTY\",\"name\":\"birds\",\"sizeRatio\":0.5}]}}");

		equal(Enum_LayerKind.IMAGE, page.pageModel.pageList.get(0).kind, "an image layer");
		Parallax_Model slot = page.pageModel.pageList.get(1);
		equal(Enum_LayerKind.EMPTY, slot.kind, "kind");
		equal("birds", slot.name, "name");
		equal(0.5f, slot.sizeRatio, 0, "sizeRatio");
	}

	/** A kind added later must not load as an image in an older game: it fails, naming the kind. */
	static void unknownKindFails()
	{
		try
		{
			Utils_Page_Json.readPage("{\"pageModel\":{\"pageList\":[{\"kind\":\"HOLOGRAM\"}]}}");
		}
		catch (GdxRuntimeException e)
		{
			isTrue(e.getMessage().contains("HOLOGRAM"), e.getMessage());
			return;
		}
		throw new AssertionError("a page with an unknown kind loaded");
	}

	static void particleLayerKeepsItsEffectsAndAnchor()
	{
		WholePage_Model page = Utils_Page_Json.readPage("{\"pageModel\":{\"pageList\":[{\"regionName\":\"sky\"},"
				+ "{\"kind\":\"PARTICLES\",\"name\":\"snow\",\"particlesLibgdx\":\"fx/snow.p\",\"particlesGodot\":\"fx/snow.tscn\","
				+ "\"particlesAnchor\":\"VIEW\"}]}}");

		Parallax_Model sky = page.pageModel.pageList.get(0);
		equal(null, sky.particlesLibgdx, "an image layer names no effect");
		equal(Enum_ParticleAnchor.LAYER, sky.particlesAnchor, "and is pinned to its layer");
		Parallax_Model snow = page.pageModel.pageList.get(1);
		equal(Enum_LayerKind.PARTICLES, snow.kind, "kind");
		equal("fx/snow.p", snow.particlesLibgdx, "the libGDX effect");
		equal("fx/snow.tscn", snow.particlesGodot, "the Godot scene");
		equal(Enum_ParticleAnchor.VIEW, snow.particlesAnchor, "anchor");
	}

	/** An anchor added later must not load as another in an older game. */
	static void unknownAnchorFails()
	{
		try
		{
			Utils_Page_Json.readPage("{\"pageModel\":{\"pageList\":[{\"kind\":\"PARTICLES\",\"particlesAnchor\":\"ORBIT\"}]}}");
		}
		catch (GdxRuntimeException e)
		{
			isTrue(e.getMessage().contains("ORBIT"), e.getMessage());
			return;
		}
		throw new AssertionError("a page with an unknown anchor loaded");
	}

	static void shaderLayerKeepsItsEffectAndNumbers()
	{
		WholePage_Model page = Utils_Page_Json.readPage("{\"pageModel\":{\"pageList\":[{\"regionName\":\"sky\"},"
				+ "{\"kind\":\"SHADER\",\"regionName\":\"sea\",\"shaderEffect\":\"FOG\",\"shaderAmplitude\":0.35,"
				+ "\"shaderWavelength\":6.5,\"shaderSpeed\":-0.7}]}}");

		Parallax_Model sky = page.pageModel.pageList.get(0);
		equal(Enum_ShaderEffect.WAVE, sky.shaderEffect, "an image layer reads the default effect");
		equal(0, sky.shaderWavelength, 0, "and no numbers");
		Parallax_Model sea = page.pageModel.pageList.get(1);
		equal(Enum_LayerKind.SHADER, sea.kind, "kind");
		equal("sea", sea.regionName, "a SHADER layer names its region");
		equal(Enum_ShaderEffect.FOG, sea.shaderEffect, "effect");
		isTrue(Float.floatToIntBits(0.35f) == Float.floatToIntBits(sea.shaderAmplitude), "amplitude " + sea.shaderAmplitude);
		isTrue(Float.floatToIntBits(6.5f) == Float.floatToIntBits(sea.shaderWavelength), "wavelength " + sea.shaderWavelength);
		isTrue(Float.floatToIntBits(-0.7f) == Float.floatToIntBits(sea.shaderSpeed), "speed " + sea.shaderSpeed);
	}

	/** An effect added later must not load as another in an older game. */
	static void unknownEffectFails()
	{
		try
		{
			Utils_Page_Json.readPage("{\"pageModel\":{\"pageList\":[{\"kind\":\"SHADER\",\"shaderEffect\":\"BLOOM\"}]}}");
		}
		catch (GdxRuntimeException e)
		{
			isTrue(e.getMessage().contains("BLOOM"), e.getMessage());
			return;
		}
		throw new AssertionError("a page with an unknown effect loaded");
	}

	static void sequenceLayerKeepsItsSegmentsSeedAndLength()
	{
		WholePage_Model page = Utils_Page_Json.readPage("{\"pageModel\":{\"pageList\":[{\"regionName\":\"sky\"},"
				+ "{\"kind\":\"SEQUENCE\",\"sequenceSegments\":[{\"regionName\":\"rock\",\"regionPosition\":2,\"weight\":30},"
				+ "{\"regionName\":\"bridge\",\"weight\":5},{\"regionName\":\"river\"}],"
				+ "\"sequenceSeed\":-1640531527,\"sequenceLength\":40}]}}");

		Parallax_Model sky = page.pageModel.pageList.get(0);
		isTrue(sky.sequenceSegments.isEmpty(), "an image layer reads no segments");
		equal(16, sky.sequenceLength, "and the default length");
		Parallax_Model ground = page.pageModel.pageList.get(1);
		equal(Enum_LayerKind.SEQUENCE, ground.kind, "kind");
		equal(3, ground.sequenceSegments.size(), "segments");
		Sequence_Segment rock = ground.sequenceSegments.get(0), bridge = ground.sequenceSegments.get(1), river = ground.sequenceSegments.get(2);
		equal("rock", rock.regionName, "a segment's region");
		equal(2, rock.regionPosition, "its position");
		equal(30, rock.weight, "its weight");
		equal(0, bridge.regionPosition, "a missing position is 0");
		equal(1, river.weight, "a missing weight is 1");
		equal(-1640531527, ground.sequenceSeed, "a negative seed");
		equal(40, ground.sequenceLength, "length");
	}
}
