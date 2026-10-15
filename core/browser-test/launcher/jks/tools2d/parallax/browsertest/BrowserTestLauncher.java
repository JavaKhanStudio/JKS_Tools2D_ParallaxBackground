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
 * {@code ?lab=planet}, the planet lab (r330, {@link PlanetLab}), the same; with {@code ?lab=lurker}, the lurker lab
 * (r369, {@link LurkerLab}), the same. anim-lab.html, with an {@code #anim-lab}: the animation lab (r407,
 * {@link AnimLab}), four panels. hills-lab.html, with a {@code #hills-lab}: the hills lab (r384, {@link HillsLab}), two
 * panels side by side. pines-lab.html, with a {@code #pines-lab}: the pines lab (r421, {@link PinesLab}), two panels.
 * shadow-lab.html, with a {@code #shadow-lab}: the shadow lab (r425, {@link ShadowLab}), two panels
 * side by side, with a stencil buffer.
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

	private static boolean lurker()
	{return has("effects-lab") && "lurker".equals(com.google.gwt.user.client.Window.Location.getParameter("lab"));}

	@Override
	public GwtApplicationConfiguration getConfig()
	{return floor() || tower() || planet() || lurker() ? new GwtApplicationConfiguration(FloorLab.FRAME_WIDTH, FloorLab.FRAME_HEIGHT)
			: has("effects-lab") ? new GwtApplicationConfiguration(2 * FogLab.FRAME_WIDTH + 4, FogLab.PANEL_HEIGHT)
			: has("hills-lab") ? new GwtApplicationConfiguration(2 * HillsLab.FRAME_WIDTH + 4, HillsLab.FRAME_HEIGHT)
			: has("pines-lab") ? new GwtApplicationConfiguration(2 * PinesLab.FRAME_WIDTH + 4, PinesLab.FRAME_HEIGHT)
			: has("shadow-lab") ? stencil(new GwtApplicationConfiguration(2 * ShadowLab.FRAME_WIDTH + 4, ShadowLab.FRAME_HEIGHT))
			: has("anim-lab") ? new GwtApplicationConfiguration(2 * AnimLab.PANEL_WIDTH + 4, 2 * AnimLab.PANEL_HEIGHT + 4)
			: has("transfert-lab") ? new GwtApplicationConfiguration(TransfertLab.COLUMNS * (FogLab.FRAME_WIDTH + 4) - 4, 2 * FogLab.PANEL_HEIGHT + 4)
			: has("fog-lab") || has("haze-lab") ? new GwtApplicationConfiguration(2 * FogLab.FRAME_WIDTH + 4, 2 * FogLab.PANEL_HEIGHT + 4) : new GwtApplicationConfiguration(320, 24);}

	/** {@code config} with a stencil buffer: the shadow lab keeps its shadows on the layer behind with it. */
	private static GwtApplicationConfiguration stencil(GwtApplicationConfiguration config)
	{
		config.stencil = true;
		return config;
	}

	@Override
	public ApplicationListener createApplicationListener()
	{return floor() ? new FloorLab() : tower() ? new TowerLab() : planet() ? new PlanetLab() : lurker() ? new LurkerLab() : has("effects-lab") ? new EffectsLab() : has("anim-lab") ? new AnimLab() : has("hills-lab") ? new HillsLab() : has("pines-lab") ? new PinesLab() : has("shadow-lab") ? new ShadowLab() : has("fog-lab") ? new FogLab() : has("haze-lab") ? new HazeLab() : has("transfert-lab") ? new TransfertLab() : new BrowserTestApp();}
}
