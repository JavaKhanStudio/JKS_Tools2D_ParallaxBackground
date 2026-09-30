package probe;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import jks.tools2d.parallax.jme.JmeAtlas;
import jks.tools2d.parallax.jme.PlaxBackground;
import jks.tools2d.parallax.pages.WholePage_Model;

import java.nio.file.Paths;

/** A headless jME game (null renderer): loads demo/assets' Hiver page through the published reader, scrolls it, exits 1 unless it has layers. */
public class JmeProbe extends SimpleApplication
{
	private PlaxBackground bg;
	private int frames;
	private static volatile int status = 1;

	public static void main(String[] args)
	{
		JmeProbe app = new JmeProbe();
		AppSettings settings = new AppSettings(true);
		settings.setResolution(800, 450);
		app.setSettings(settings);
		app.setShowSettings(false);
		app.start(JmeContext.Type.Headless);
		long end = System.currentTimeMillis() + 30_000;
		while (status == 1 && app.frames < 30 && System.currentTimeMillis() < end)
			try {Thread.sleep(50);} catch (InterruptedException e) {break;}
		app.stop(true);
		System.out.println(status == 0 ? "OK" : "FAIL");
		System.exit(status);
	}

	@Override
	public void simpleInitApp()
	{
		assetManager.registerLocator(Paths.get("").toAbsolutePath().toString(), FileLocator.class);
		bg = new PlaxBackground();
		stateManager.attach(bg);
		WholePage_Model page = PlaxBackground.loadPage(assetManager, "hiver/Hiver.plax");
		bg.setPage(page, JmeAtlas.load(assetManager, page.pageModel.atlasName));
		bg.speedConstantX = 60;
	}

	@Override
	public void simpleUpdate(float tpf)
	{
		if (++frames == 20)
		{
			int layers = bg.getReader().layers.size();
			System.out.println("Hiver: " + layers + " layers, world " + bg.getWorldWidth() + " x " + bg.getWorldHeight());
			status = layers > 0 ? 0 : 2;
		}
	}
}
