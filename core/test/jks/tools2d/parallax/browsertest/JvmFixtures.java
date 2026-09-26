package jks.tools2d.parallax.browsertest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.badlogic.gdx.graphics.Color;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import jks.tools2d.parallax.pages.Json_MixIns;
import jks.tools2d.parallax.pages.Utils_Page;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * The browser suite's saved pages, built on the JVM from the test fixtures (JsonPageTest's): every .plax that has its
 * .plaxpj beside it gives two JSON files, the .plaxpj and the .jplax the editor would export, each with the
 * {@link PageDump} of the page Kryo reads. {@link #main} writes them for the browser.
 */
public final class JvmFixtures implements Fixtures
{
	private static final ObjectMapper JSON = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	static
	{Json_MixIns.MIX_INS.forEach(JSON::addMixIn);}

	private final Map<String, String[]> fixtures = new LinkedHashMap<>();

	public JvmFixtures(Path root)
	{
		try
		{
			for (Path plax : plaxWithProject(root))
			{
				String name = root.relativize(plax).toString().replace('\\', '/').replaceAll("[^A-Za-z0-9.]+", "_");
				WholePage_Model page = Utils_Page.loadPage(new ByteArrayInputStream(Files.readAllBytes(plax)));
				fixtures.put(name + "pj", new String[] { Files.readString(Path.of(plax + "pj"), StandardCharsets.UTF_8), PageDump.of(page) });

				// The fields the samples predate, set so the .jplax proves they are read (JsonPageTest does the same).
				page.pageModel.pageList.get(0).flipY = true;
				page.pageModel.pageList.get(0).mirror = true;
				page.useOriginalSize = true;
				page.topHalf_top = new Color(0.1f, 0.2f, 0.3f, 0.4f);
				fixtures.put(name.replaceAll("\\.plax$", ".jplax"), new String[] { JSON.writeValueAsString(page), PageDump.of(page) });
			}
		}
		catch (IOException e)
		{throw new UncheckedIOException(e);}
	}

	private static List<Path> plaxWithProject(Path root) throws IOException
	{
		List<Path> pages = new ArrayList<>();
		for (String dir : new String[] { "core/test-data/onboard", "editor/Files", "demo/assets" })
			try (Stream<Path> walk = Files.walk(root.resolve(dir)))
			{
				pages.addAll(walk.filter(p -> p.toString().endsWith(".plax") && Files.exists(Path.of(p + "pj")))
						.sorted().collect(Collectors.toList()));
			}
		if (pages.isEmpty())
			throw new IllegalStateException("no .plax with its .plaxpj under " + root);
		return pages;
	}

	@Override
	public List<String> names()
	{return new ArrayList<>(fixtures.keySet());}

	@Override
	public String json(String name)
	{return fixtures.get(name)[0];}

	@Override
	public String expectedDump(String name)
	{return fixtures.get(name)[1];}

	/**
	 * Writes the fixtures where the browser build's asset preloader copies them from: {@code <dir>/fixtures/list.txt},
	 * then {@code <name>.json} and {@code <name>.dump.txt} for each. Arguments: the repo root, the assets folder.
	 */
	public static void main(String[] args) throws IOException
	{
		JvmFixtures fixtures = new JvmFixtures(Path.of(args[0]));
		Path dir = Path.of(args[1], "fixtures");
		Files.createDirectories(dir);
		for (String name : fixtures.names())
		{
			Files.writeString(dir.resolve(name + ".json"), fixtures.json(name), StandardCharsets.UTF_8);
			Files.writeString(dir.resolve(name + ".dump.txt"), fixtures.expectedDump(name), StandardCharsets.UTF_8);
		}
		Files.writeString(dir.resolve("list.txt"), String.join("\n", fixtures.names()) + "\n", StandardCharsets.UTF_8);
	}
}
