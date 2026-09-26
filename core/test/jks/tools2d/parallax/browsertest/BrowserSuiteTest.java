package jks.tools2d.parallax.browsertest;

import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import com.badlogic.gdx.utils.GdxNativesLoader;

/** The browser suite (core/browser-test) on the JVM: a case that fails here would fail in Chrome too. */
class BrowserSuiteTest
{
	@BeforeAll
	static void natives()
	{GdxNativesLoader.load();}

	@TestFactory
	Stream<DynamicTest> browserSuite()
	{
		Path root = Path.of(System.getProperty("parallax.repoRoot", ".."));
		return BrowserSuite.all(new JvmFixtures(root)).stream().map(c -> DynamicTest.dynamicTest(c.name, c.body::run));
	}
}
