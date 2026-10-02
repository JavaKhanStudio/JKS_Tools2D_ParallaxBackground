package jks.tools2d.parallax.browsertest;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.gwt.GwtApplication;
import com.badlogic.gdx.backends.gwt.GwtApplicationConfiguration;
import com.google.gwt.dom.client.Document;

/**
 * The browser suite's page: a small libGDX canvas, so the cases run in a real WebGL browser app, as a game's would.
 * fog-lab.html, the page with a {@code #fog-lab}: the FOG lab instead (r204, {@link FogLab}).
 */
public class BrowserTestLauncher extends GwtApplication
{
	private static boolean fogLab()
	{return Document.get().getElementById("fog-lab") != null;}

	@Override
	public GwtApplicationConfiguration getConfig()
	{return fogLab() ? new GwtApplicationConfiguration(2 * FogLab.FRAME_WIDTH + 4, 2 * FogLab.PANEL_HEIGHT + 4) : new GwtApplicationConfiguration(320, 60);}

	@Override
	public ApplicationListener createApplicationListener()
	{return fogLab() ? new FogLab() : new BrowserTestApp();}
}
