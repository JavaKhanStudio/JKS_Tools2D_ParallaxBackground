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
 * {@link EffectsLab}), two panels side by side; with {@code ?lab=floor}, the floor lab (r275, {@link FloorLab}), one
 * panel showing the whole world; with {@code ?lab=tower}, the tower lab (r328, {@link TowerLab}), the same; with
 * {@code ?lab=planet}, the planet lab (r330, {@link PlanetLab}), the same. anim-lab.html, with an {@code #anim-lab}:
 * the animation lab (r407, {@link AnimLab}), four panels.
 */
public class BrowserTestLauncher extends GwtApplication
{
	private static boolean has(String id)
	{return Document.get().getElementById(id) != null;}

	private static boolean floor()
	{return has("effects-lab") && "floor".equals(com.google.gwt.user.client.Window.Location.getParameter("lab"));}

	private static boolean tower()
	{return has("effects-lab") && "tower".equals(com.google.gwt.user.client.Window.Location.getParameter("lab"));}

	private static boolean planet()
	{return has("effects-lab") && "planet".equals(com.google.gwt.user.client.Window.Location.getParameter("lab"));}

	@Override
	public GwtApplicationConfiguration getConfig()
	{return floor() || tower() || planet() ? new GwtApplicationConfiguration(FloorLab.FRAME_WIDTH, FloorLab.FRAME_HEIGHT)
			: has("effects-lab") ? new GwtApplicationConfiguration(2 * FogLab.FRAME_WIDTH + 4, FogLab.PANEL_HEIGHT)
			: has("anim-lab") ? new GwtApplicationConfiguration(2 * AnimLab.PANEL_WIDTH + 4, 2 * AnimLab.PANEL_HEIGHT + 4)
			: has("transfert-lab") ? new GwtApplicationConfiguration(TransfertLab.COLUMNS * (FogLab.FRAME_WIDTH + 4) - 4, 2 * FogLab.PANEL_HEIGHT + 4)
			: has("fog-lab") || has("haze-lab") ? new GwtApplicationConfiguration(2 * FogLab.FRAME_WIDTH + 4, 2 * FogLab.PANEL_HEIGHT + 4) : new GwtApplicationConfiguration(320, 60);}

	@Override
	public ApplicationListener createApplicationListener()
	{return floor() ? new FloorLab() : tower() ? new TowerLab() : planet() ? new PlanetLab() : has("effects-lab") ? new EffectsLab() : has("anim-lab") ? new AnimLab() : has("fog-lab") ? new FogLab() : has("haze-lab") ? new HazeLab() : has("transfert-lab") ? new TransfertLab() : new BrowserTestApp();}
}
