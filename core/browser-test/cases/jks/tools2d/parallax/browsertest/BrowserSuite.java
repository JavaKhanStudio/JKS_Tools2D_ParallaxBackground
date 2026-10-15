package jks.tools2d.parallax.browsertest;

import java.util.ArrayList;
import java.util.List;

/**
 * The tests of core/test that translate to JavaScript, run in a browser by `tools/browser-test.sh` and on the JVM by
 * core/test BrowserSuiteTest, so a case that fails is seen by `./gradlew build` before it reaches a browser. What stays
 * JVM-only, and why: PlaxFormatTest (Kryo), FrameAllocationTest (the JVM's allocation counter), ParallaxLayerTest
 * (reflection) and ParallaxHeartResizeTest (a proxied GL).
 */
public final class BrowserSuite
{
	private BrowserSuite()
	{}

	public static List<BrowserCase> all(Fixtures fixtures)
	{
		List<BrowserCase> cases = new ArrayList<>();
		cases.addAll(TilingCases.all());
		cases.addAll(TransferCases.all());
		cases.addAll(HookCases.all());
		cases.addAll(ParticleCases.all());
		cases.addAll(ShaderCases.all());
		cases.addAll(PageFogCases.all());
		cases.addAll(SequenceCases.all());
		cases.addAll(TransfertStyleCases.all());
		cases.addAll(FloorCases.all());
		cases.addAll(DollyCases.all());
		cases.addAll(TowerCases.all());
		cases.addAll(PlanetCases.all());
		cases.addAll(AnimatedPatchCases.all());
		cases.addAll(LurkerCases.all());
		cases.addAll(JsonCases.all(fixtures));
		return cases;
	}
}
