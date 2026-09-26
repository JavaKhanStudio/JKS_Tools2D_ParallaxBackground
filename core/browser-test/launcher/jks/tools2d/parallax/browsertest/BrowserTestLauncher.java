package jks.tools2d.parallax.browsertest;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.gwt.GwtApplication;
import com.badlogic.gdx.backends.gwt.GwtApplicationConfiguration;

/** The browser suite's page: a small libGDX canvas, so the cases run in a real WebGL browser app, as a game's would. */
public class BrowserTestLauncher extends GwtApplication
{
	@Override
	public GwtApplicationConfiguration getConfig()
	{return new GwtApplicationConfiguration(320, 60);}

	@Override
	public ApplicationListener createApplicationListener()
	{return new BrowserTestApp();}
}
