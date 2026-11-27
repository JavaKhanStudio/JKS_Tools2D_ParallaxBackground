package jks.tools2d.parallax.jme;

import java.nio.file.Paths;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;

/**
 * The start every app in tests/ shares (r84): {@link #settings} a measuring window, {@link #start} it, and
 * {@link #bare} the scene in simpleInitApp. An app sets only what differs from these after the call.
 */
final class JmeApps
{
	private JmeApps()
	{}

	/**
	 * An OpenGL 3.2 window of {@code width} by {@code height}: no gamma correction (the stills compare raw bytes with
	 * libGDX's), no multisampling, no vsync, no frame cap, no audio.
	 */
	static AppSettings settings(String title, int width, int height)
	{
		AppSettings settings = new AppSettings(true);
		settings.setRenderer(AppSettings.LWJGL_OPENGL32);
		settings.setResolution(width, height);
		settings.setGammaCorrection(false);
		settings.setSamples(0);
		settings.setVSync(false);
		settings.setFrameRate(-1);
		settings.setTitle(title);
		settings.setAudioRenderer(null);
		return settings;
	}

	/** Starts {@code app} in a window, no settings dialog, still running when it loses focus. */
	static void start(SimpleApplication app, AppSettings settings)
	{
		start(app, settings, JmeContext.Type.Display);
	}

	/** Starts {@code app} in a context of {@code type}, no settings dialog, still running when it loses focus. */
	static void start(SimpleApplication app, AppSettings settings, JmeContext.Type type)
	{
		app.setSettings(settings);
		app.setShowSettings(false);
		app.setPauseOnLostFocus(false);
		app.start(type);
	}

	/** simpleInitApp's first lines: no FPS or stats overlay, the fly camera off, assets read from the working directory. */
	static void bare(SimpleApplication app)
	{
		bare(app, "");
	}

	/** As {@link #bare(SimpleApplication)}, assets read from {@code dir} under the working directory. */
	static void bare(SimpleApplication app, String dir)
	{
		app.setDisplayFps(false);
		app.setDisplayStatView(false);
		app.getFlyByCamera().setEnabled(false);
		locate(app, dir);
	}

	/** Registers {@code dir}, under the working directory, as where {@code app}'s assets are read from. */
	static void locate(SimpleApplication app, String dir)
	{
		app.getAssetManager().registerLocator(Paths.get(dir).toAbsolutePath().toString(), FileLocator.class);
	}
}
