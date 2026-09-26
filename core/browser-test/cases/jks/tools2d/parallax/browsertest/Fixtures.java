package jks.tools2d.parallax.browsertest;

import java.util.List;

/**
 * The saved pages the browser suite loads: each is a JSON file (.plaxpj, or a .jplax the JVM wrote from the .plax) and
 * the {@link PageDump} of the page Kryo reads from the .plax beside it, which the JSON must load as.
 */
public interface Fixtures
{
	/** Names of the JSON files, in the order the suite runs them. */
	List<String> names();

	String json(String name);

	String expectedDump(String name);
}
