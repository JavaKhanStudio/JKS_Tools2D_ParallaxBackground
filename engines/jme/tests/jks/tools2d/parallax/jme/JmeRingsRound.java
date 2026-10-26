package jks.tools2d.parallax.jme;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.jme3.app.SimpleApplication;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;

import jks.tools2d.parallax.ParallaxRings;
import jks.tools2d.parallax.pages.WholePage_Model;
import jks.tools2d.parallax.rounds.RingsRound;

/**
 * The rings round (engines/rounds/rings) through jME (parallax-readers r50): each scene's page loaded as a jME game
 * loads it ({@link PlaxBackground#loadPage}, {@link JmeAtlas#load}), shown by {@link PlaxRings} with the scene's options,
 * and the bands and gradient its update laid out written as {@code OUT/<id>.json} by core's own RingsRound.json, for
 * tools/compare-rings.py. Headless: no window, no GL. engines/jme/tools/frame-check.sh runs it for that round.
 * <pre>
 *   java -cp ... jks.tools2d.parallax.jme.JmeRingsRound &lt;round dir&gt; &lt;out dir&gt;   (from the repository root)
 * </pre>
 * {@code -Dparallax.jme.break=rings} lays the rings out with the scene's front 1 % out: the round must FAIL.
 */
public class JmeRingsRound extends SimpleApplication
{
	private final Path roundDir, outDir;
	private final boolean breakRings = "rings".equals(System.getProperty("parallax.jme.break"));
	private PlaxBackground bg;
	private JsonValue scene;
	private int failed, frames;

	public JmeRingsRound(Path roundDir, Path outDir)
	{
		super((com.jme3.app.state.AppState[]) null);
		this.roundDir = roundDir;
		this.outDir = outDir;
	}

	public static void main(String[] args)
	{
		JmeRingsRound app = new JmeRingsRound(Paths.get(args[0]), Paths.get(args[1]));
		AppSettings settings = new AppSettings(true);
		// 16:9, as a ring's page is (ParallaxRings.pageHeightOf): the world PlaxBackground builds the layers in.
		settings.setResolution(1280, 720);
		settings.setAudioRenderer(null);
		app.setSettings(settings);
		app.setShowSettings(false);
		app.start(JmeContext.Type.Headless);
	}

	@Override
	public void simpleInitApp()
	{
		assetManager.registerLocator(Paths.get("").toAbsolutePath().toString(), FileLocator.class);
		try
		{
			Files.createDirectories(outDir);
			scene = new JsonReader().parse(Files.readString(roundDir.resolve("round.json"))).get("scenes").child;
		}
		catch (IOException e)
		{throw new RuntimeException(e);}
		bg = new PlaxBackground();
		bg.autoAct = false;
		stateManager.attach(bg);
	}

	/** One scene a frame, once the background is initialized (it knows the screen's size then). */
	@Override
	public void simpleUpdate(float tpf)
	{
		if (!bg.isInitialized() || frames++ < 1)
			return;
		if (scene == null)
		{
			System.out.println("rings: " + (failed > 0 ? "FAIL" : "PASS"));
			stop();
			return;
		}
		String id = scene.getString("id");
		try
		{
			WholePage_Model page = PlaxBackground.loadPage(assetManager, scene.getString("page"));
			bg.setPage(page, JmeAtlas.load(assetManager, scene.getString("atlasDir") + "/" + page.pageModel.atlasName));
			PlaxRings rings = new PlaxRings(assetManager, bg);
			ParallaxRings.Options o = RingsRound.options(scene.get("rings"), page);
			rings.front = o.front * (breakRings ? 1.01f : 1);
			rings.far = o.far;
			rings.foot = o.foot;
			rings.lift = o.lift;
			rings.scale = o.scale;
			rings.pageDegrees = o.pageDegrees;
			rings.fogStrength = scene.get("rings") != null && scene.get("rings").has("fogStrength") ? o.fogStrength : Float.NaN;
			rings.update(0);
			Files.writeString(outDir.resolve(id + ".json"), RingsRound.json(rings.bands(), rings.gradient(), rings.ringOptions(page)));
			System.out.println(id + ": " + rings.bands().size() + " bands");
			rings.dispose();
		}
		catch (RuntimeException | IOException e)
		{
			failed++;
			System.out.println("FAIL " + id + ": " + e);
			e.printStackTrace(System.out);
		}
		scene = scene.next;
	}
}
