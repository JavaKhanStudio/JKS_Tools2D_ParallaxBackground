package jks.tools2d.parallax.browsertest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;

/**
 * Runs {@link BrowserSuite} once the assets are loaded and writes one row per case into the page, then a summary line
 * {@code <pre id="summary">} that tools/browser-test.sh reads: "browser-tests: N passed, M failed". The canvas stays
 * green when every case passed, red otherwise.
 */
public class BrowserTestApp extends ApplicationAdapter
{
	private boolean passed;

	@Override
	public void create()
	{
		Document document = Document.get();
		Element table = document.createTableElement();
		table.setAttribute("style", "font: 13px monospace; border-collapse: collapse");
		int ok = 0, failed = 0;
		for (BrowserCase test : BrowserSuite.all(new AssetFixtures()))
		{
			String error = null;
			try
			{test.body.run();}
			catch (Throwable e)
			{error = e.getClass().getSimpleName() + ": " + e.getMessage();}
			if (error == null)
				ok++;
			else
				failed++;
			Element row = document.createTRElement();
			row.setAttribute("class", error == null ? "pass" : "fail");
			row.setAttribute("style", "background:" + (error == null ? "#e6f4ea" : "#fce8e6"));
			row.appendChild(cell(document, error == null ? "PASS" : "FAIL"));
			row.appendChild(cell(document, test.name));
			row.appendChild(cell(document, error == null ? "" : error));
			table.appendChild(row);
		}
		passed = failed == 0 && ok > 0;
		Element summary = document.createPreElement();
		summary.setId("summary");
		summary.setInnerText("browser-tests: " + ok + " passed, " + failed + " failed");
		Element browser = document.createDivElement();
		browser.setAttribute("style", "color: #666; margin-bottom: 8px");
		browser.setInnerText(navigator());
		document.getBody().appendChild(summary);
		document.getBody().appendChild(browser);
		document.getBody().appendChild(table);
		document.setTitle((passed ? "PASS " : "FAIL ") + ok + "/" + (ok + failed) + " parallax browser tests");
	}

	private static Element cell(Document document, String text)
	{
		Element cell = document.createTDElement();
		cell.setAttribute("style", "padding: 2px 8px; border-bottom: 1px solid #ddd");
		cell.setInnerText(text);
		return cell;
	}

	private static native String navigator() /*-{
		return $wnd.navigator.userAgent;
	}-*/;

	@Override
	public void render()
	{
		Gdx.gl.glClearColor(passed ? 0.2f : 0.8f, passed ? 0.7f : 0.2f, 0.2f, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
	}

	/** The fixtures JvmFixtures wrote into the assets, read back through Gdx.files as a browser game reads its pages. */
	private static final class AssetFixtures implements Fixtures
	{
		private final List<String> names = new ArrayList<>();

		AssetFixtures()
		{
			for (String name : Arrays.asList(Gdx.files.internal("fixtures/list.txt").readString("UTF-8").split("\n")))
				if (!name.trim().isEmpty())
					names.add(name.trim());
		}

		@Override
		public List<String> names()
		{return names;}

		@Override
		public String json(String name)
		{return Gdx.files.internal("fixtures/" + name + ".json").readString("UTF-8");}

		@Override
		public String expectedDump(String name)
		{return Gdx.files.internal("fixtures/" + name + ".dump.txt").readString("UTF-8");}
	}
}
