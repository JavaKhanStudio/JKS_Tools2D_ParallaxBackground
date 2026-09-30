package jks.tools2d.parallax.browsertest;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import com.badlogic.gdx.utils.GdxNativesLoader;

/**
 * The browser suite (core/browser-test) on the JVM: a case that fails here would fail in Chrome too. Each case runs
 * under a timeout, so a reader that loops forever (a negative padding larger than the image did) fails instead of
 * stalling the build.
 */
class BrowserSuiteTest
{
	@BeforeAll
	static void natives()
	{GdxNativesLoader.load();}

	@TestFactory
	Stream<DynamicTest> browserSuite()
	{
		Path root = Path.of(System.getProperty("parallax.repoRoot", ".."));
		return BrowserSuite.all(new JvmFixtures(root)).stream()
				.map(c -> DynamicTest.dynamicTest(c.name, () -> assertTimeoutPreemptively(Duration.ofSeconds(10), c.body::run)));
	}
}
