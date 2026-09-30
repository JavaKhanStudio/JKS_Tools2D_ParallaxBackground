package jks.tools2d.parallax.jme;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.jme3.app.SimpleApplication;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.system.AppSettings;
import com.jme3.system.lwjgl.LwjglWindow;

import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * Stills of every scene of a lab round drawn by jME, as {@code ParallaxLab --shots} takes them with libGDX: the same
 * page, the same 60 units/s scroll stepped at 1/60 s (in float, as the lab counts it), grabbed 0, 6 and 12 s in. A
 * scene's "transfer" (one or a list), "tint", "speedY" and "resize" are played at the same steps.
 * tools/jme-parallax-shots.sh runs it and compares the two sets.
 * <pre>
 *   java -cp ... jks.tools2d.parallax.jme.JmeParallaxShots &lt;round dir&gt; &lt;out dir&gt;   (from the repository root)
 * </pre>
 * {@code -Dparallax.jme.break=scroll} draws with a deliberately wrong scroll (x1.1), to prove the comparison sees one.
 */
public class JmeParallaxShots extends SimpleApplication
{
	private static final float SPEED = 60;
	private static final float[] SHOT_TIMES = { 0, 6, 12 };
	private static final float STEP = 1 / 60f;

	private static final class Transfer
	{
		float at, seconds;
		String page, atlasDir;
	}

	private static final class Scene
	{
		String id, page, atlasDir;
		float speedY, tintAt = -1, tintSeconds, resizeAt = -1;
		int resizeWidth, resizeHeight;
		Color tint;
		final List<Transfer> transfers = new ArrayList<>();
	}

	private final Path roundDir, outDir;
	private final List<Scene> scenes = new ArrayList<>();
	private final float speedFactor = "scroll".equals(System.getProperty("parallax.jme.break")) ? 1.1f : 1;
	private PlaxBackground bg;

	// Where the stepping is: scene, shot, time, and what of the scene has been played.
	private int sceneIndex = -1, shotIndex;
	private float time;
	private int transferred;
	private boolean tinted, resized;
	private int wantWidth, wantHeight, settleFrames;
	private final FrameGrab grab = new FrameGrab();
	private boolean done;

	public JmeParallaxShots(Path roundDir, Path outDir)
	{
		this.roundDir = roundDir;
		this.outDir = outDir;
	}

	public static void main(String[] args)
	{
		JmeParallaxShots app = new JmeParallaxShots(Paths.get(args[0]), Paths.get(args[1]));
		AppSettings settings = new AppSettings(true);
		settings.setRenderer(AppSettings.LWJGL_OPENGL32);
		settings.setResolution(1280, 720);
		settings.setResizable(true);
		settings.setGammaCorrection(false);
		settings.setSamples(0);
		settings.setVSync(false);
		settings.setFrameRate(-1);
		settings.setTitle("jME parallax shots");
		settings.setAudioRenderer(null);
		app.setSettings(settings);
		app.setShowSettings(false);
		app.setPauseOnLostFocus(false);
		app.start();
	}

	@Override
	public void simpleInitApp()
	{
		setDisplayFps(false);
		setDisplayStatView(false);
		flyCam.setEnabled(false);
		assetManager.registerLocator(Paths.get("").toAbsolutePath().toString(), FileLocator.class);
		try
		{Files.createDirectories(outDir);}
		catch (IOException e)
		{throw new RuntimeException(e);}
		readRound();

		bg = new PlaxBackground();
		bg.autoAct = false;
		stateManager.attach(bg);
		guiViewPort.addProcessor(grab);
	}

	private void readRound()
	{
		JsonValue round = new JsonReader().parse(readString(roundDir.resolve("round.json")));
		for (JsonValue s = round.get("scenes").child; s != null; s = s.next)
		{
			Scene scene = new Scene();
			scene.id = s.getString("id");
			scene.page = s.getString("page");
			scene.atlasDir = s.getString("atlasDir");
			scene.speedY = s.getFloat("speedY", 0);
			JsonValue resize = s.get("resize");
			if (resize != null)
			{
				scene.resizeAt = resize.getFloat("at");
				scene.resizeWidth = resize.get("size").getInt(0);
				scene.resizeHeight = resize.get("size").getInt(1);
			}
			JsonValue transfer = s.get("transfer");
			if (transfer != null)
				for (JsonValue t : transfer.isArray() ? transfer : List.of(transfer))
				{
					Transfer into = new Transfer();
					into.at = t.getFloat("at");
					into.seconds = t.getFloat("seconds");
					into.page = t.getString("page", null);
					into.atlasDir = t.getString("atlasDir", scene.atlasDir);
					scene.transfers.add(into);
				}
			JsonValue tint = s.get("tint");
			if (tint != null)
			{
				scene.tintAt = tint.getFloat("at");
				scene.tintSeconds = tint.getFloat("seconds");
				float[] c = tint.get("color").asFloatArray();
				scene.tint = new Color(c[0], c[1], c[2], c[3]);
			}
			scenes.add(scene);
		}
	}

	private static String readString(Path path)
	{
		try
		{return Files.readString(path);}
		catch (IOException e)
		{throw new RuntimeException(e);}
	}

	/**
	 * One step of the state machine a frame: waits for a resize, or plays the scene up to the next still and asks for
	 * it to be grabbed at the end of this frame.
	 */
	@Override
	public void simpleUpdate(float tpf)
	{
		if (done || grab.isPending())
			return;
		if (!windowIs(wantWidth, wantHeight))
			return;

		if (sceneIndex < 0 || shotIndex == SHOT_TIMES.length)
		{
			if (sceneIndex + 1 == scenes.size())
			{
				done = true;
				stop();
				return;
			}
			if (wantWidth != 1280 || wantHeight != 720)
			{
				resizeWindow(1280, 720);
				return;
			}
			show(++sceneIndex);
		}

		Scene scene = scenes.get(sceneIndex);
		float at = SHOT_TIMES[shotIndex];
		for (; time < at; time += STEP)
		{
			while (transferred < scene.transfers.size() && time >= scene.transfers.get(transferred).at)
			{
				Transfer transfer = scene.transfers.get(transferred++);
				WholePage_Model into = bg.getPage();
				TextureAtlas atlas = into.getLoadedAtlas();
				if (transfer.page != null)
				{
					into = PlaxBackground.loadPage(assetManager, transfer.page);
					atlas = JmeAtlas.load(assetManager, transfer.atlasDir + "/" + into.pageModel.atlasName);
				}
				bg.transfertIntoPage(into, atlas, transfer.seconds);
			}
			if (!resized && scene.resizeAt >= 0 && time >= scene.resizeAt)
			{
				resized = true;
				// The loop resumes once the window has the new size: the rest of this step then runs.
				resizeWindow(scene.resizeWidth, scene.resizeHeight);
				return;
			}
			if (!tinted && scene.tintAt >= 0 && time >= scene.tintAt)
			{
				tinted = true;
				bg.tintTo(scene.tint, scene.tintSeconds);
			}
			bg.act(STEP * speedFactor);
		}
		grab.grab(outDir.resolve(scene.id + "-t" + (int) at + ".png").toFile());
		shotIndex++;
	}

	private void show(int index)
	{
		Scene scene = scenes.get(index);
		WholePage_Model page = PlaxBackground.loadPage(assetManager, scene.page);
		bg.setPage(page, JmeAtlas.load(assetManager, scene.atlasDir + "/" + page.pageModel.atlasName));
		bg.resetPositions();
		bg.tintTo(Color.WHITE, 0);
		bg.speedConstantX = SPEED;
		bg.speedConstantY = scene.speedY;
		shotIndex = 0;
		time = 0;
		transferred = 0;
		tinted = resized = false;
		System.out.println("shots: " + scene.id);
	}

	@Override
	public void requestClose(boolean esc)
	{
		System.out.println("shots: close requested at scene " + sceneIndex + ", window " + cam.getWidth() + "x" + cam.getHeight());
		super.requestClose(esc);
	}

	private void resizeWindow(int width, int height)
	{
		wantWidth = width;
		wantHeight = height;
		settleFrames = 2;
		GLFW.glfwSetWindowSize(((LwjglWindow) getContext()).getWindowHandle(), width, height);
	}

	/** True once the viewport has that size and a couple of frames have been drawn at it. */
	private boolean windowIs(int width, int height)
	{
		if (width == 0)
		{
			wantWidth = cam.getWidth();
			wantHeight = cam.getHeight();
			return true;
		}
		if (cam.getWidth() != width || cam.getHeight() != height)
			return false;
		return settleFrames-- <= 0;
	}
}
