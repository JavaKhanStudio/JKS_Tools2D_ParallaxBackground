package jks.tools2d.parallax.browsertest;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.gwt.GwtApplication;
import com.badlogic.gdx.backends.gwt.GwtApplicationConfiguration;
import com.google.gwt.dom.client.Document;

/**
 * The browser suite's page: a small libGDX canvas, so the cases run in a real WebGL browser app, as a game's would.
 * fog-lab.html, the page with a {@code #fog-lab}: the FOG lab instead (r204, {@link FogLab}); haze-lab.html, with a
 * {@code #haze-lab}: the haze lab (r215, {@link HazeLab}); transfert-lab.html, with a {@code #transfert-lab}: the
 * transfert lab (r220, {@link TransfertLab}); effects-lab.html, with an {@code #effects-lab}: the effects labs (r224,
 * {@link EffectsLab}), two panels side by side.
 */
public class BrowserTestLauncher extends GwtApplication
{
	private static boolean has(String id)
	{return Document.get().getElementById(id) != null;}

	@Override
	public GwtApplicationConfiguration getConfig()
	{return has("effects-lab") ? new GwtApplicationConfiguration(2 * FogLab.FRAME_WIDTH + 4, FogLab.PANEL_HEIGHT)
			: has("transfert-lab") ? new GwtApplicationConfiguration(TransfertLab.COLUMNS * (FogLab.FRAME_WIDTH + 4) - 4, 2 * FogLab.PANEL_HEIGHT + 4)
			: has("fog-lab") || has("haze-lab") ? new GwtApplicationConfiguration(2 * FogLab.FRAME_WIDTH + 4, 2 * FogLab.PANEL_HEIGHT + 4) : new GwtApplicationConfiguration(320, 60);}

	@Override
	public ApplicationListener createApplicationListener()
	{return has("effects-lab") ? new EffectsLab() : has("fog-lab") ? new FogLab() : has("haze-lab") ? new HazeLab() : has("transfert-lab") ? new TransfertLab() : new BrowserTestApp();}
}
