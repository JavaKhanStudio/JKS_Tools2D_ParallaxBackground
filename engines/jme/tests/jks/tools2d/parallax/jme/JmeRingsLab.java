package jks.tools2d.parallax.jme;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.jme3.app.SimpleApplication;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.shape.Quad;
import com.jme3.system.AppSettings;
import com.jme3.system.lwjgl.LwjglWindow;
import com.jme3.util.BufferUtils;

import jks.tools2d.parallax.ParallaxRings.Band;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * The jME rings lab (parallax-readers r50), three.js's rings lab (engines/threejs/tests/rings_lab.js) in jME: a page of
 * the rings round (engines/rounds/rings) as the sky of a small 3D world, drawn by {@link PlaxRings} over one
 * {@link PlaxBackground}: a valley, a snowy ridge round it at about the page's foot, a camera flying round the valley.
 * Panel A stands the bands at the library's depths (front / speed), panel B compresses them into front..far; a click on
 * a panel gives it the whole stage, another gives it back. The controls on the left, the view pick first: up and down
 * pick a knob (or a click on it), left and right move it (shift: ten steps; the mouse wheel too), V the view, N the next
 * scene, F a cross-fade into it, Space pause, R every knob back where it opened, C Copy settings (JSON on the clipboard
 * and in build/jme/rings-lab/settings.json). It opens at Simon's look on r48.
 * <pre>
 *   java -cp ... jks.tools2d.parallax.jme.JmeRingsLab [--shot DIR] [--scenes "g02 g01 g04"] [key=value...]
 * </pre>
 * engines/jme/tools/rings-lab.sh runs it: a window, or {@code --shot DIR}, stills in cage: DIR/&lt;scene&gt;-both-t&lt;s&gt;.png
 * at 0, 8 and 20 s of the flight for each scene, then the last scene cross-fading into the next, half way and done,
 * then Copy settings checked (a knob moved shows in "moved").
 */
public class JmeRingsLab extends SimpleApplication
{
	/**
	 * The round whose pages the lab draws: engines/rounds/rings, or round=NAME another of engines/rounds (round=shaders: the
	 * SHADER effects on a band, r61), round=/DIR a folder from the repository root (/engines/threejs/tests/rings-seam:
	 * r58's join); its scenes' rings fits unused. As three.js's ?round=.
	 */
	private static String round = "engines/rounds/rings";
	/** -Dparallax.jme.break=ringeffect draws every SHADER band's image plain (PlaxRings.effects off): r61's mutant. */
	private static final boolean BREAK_EFFECT = "ringeffect".equals(System.getProperty("parallax.jme.break"));
	private static final int CONTROLS = 300;
	private static final String[] VIEWS = { "both", "a", "b" };

	/** A knob: key, label, min, max, step, section. */
	private static final Object[][] KNOBS = {
		{ "front", "front", 50f, 800f, 5f, "Rings" }, { "far", "far (B)", 100f, 3000f, 10f, "Rings" },
		{ "foot", "foot", 0f, 120f, 1f, "Rings" }, { "lift", "lift", -12f, 12f, 0.5f, "Rings" }, { "scale", "scale", 0.2f, 2f, 0.05f, "Rings" },
		{ "pageDegrees", "page degrees", 10f, 90f, 1f, "Rings" },
		{ "fogStrength", "fog strength", 0f, 0.2f, 0.002f, "Fog" }, { "melt", "melt", 0f, 0.6f, 0.01f, "Fog" },
		{ "eyeMelt", "eye melt", 0f, 0.8f, 0.01f, "Fog" }, { "horizon", "horizon", 0f, 0.5f, 0.01f, "Fog" },
		{ "worldFog", "world fog far", 200f, 3000f, 10f, "Fog" },
		{ "dusk", "dusk tint", 0f, 1f, 0.01f, "World" }, { "tintSky", "tint sky (0/1)", 0f, 1f, 1f, "World" },
		{ "orbit", "flight radius", 20f, 240f, 5f, "World" }, { "height", "flight height", 5f, 120f, 1f, "World" },
		{ "speed", "flight speed", 0f, 3f, 0.05f, "World" }, { "look", "look (deg)", -180f, 180f, 1f, "World" },
	};
	/** Simon's look on r48 (26 Oct): a low flight, the bands compressed into 510..3000, 28 degrees of page, the eye melt. */
	private static final Map<String, Float> DEFAULTS = new LinkedHashMap<>();
	static
	{
		float[] values = { 510, 3000, 51, -2, 1, 28, 0, 0, 0.3f, 0.5f, 200, 0, 0, 60, 10, 0.4f, 0 };
		for (int i = 0; i < KNOBS.length; i++)
			DEFAULTS.put((String) KNOBS[i][0], values[i]);
	}
	private static final Color NIGHT = new Color(0x30 / 255f, 0x38 / 255f, 0x6e / 255f, 1);

	private final Path shotDir;
	private final List<String> shotScenes;
	private final Map<String, Float> given;

	private final List<JsonValue> scenes = new ArrayList<>();
	private JsonValue current;
	private final Map<String, Float> knobs = new LinkedHashMap<>(), opened = new LinkedHashMap<>();
	private String view = "both", zoomed;
	private int picked;
	private float flight;
	private boolean playing = true;
	private String flash = "";
	private float flashFor;

	private PlaxBackground bg;
	private PlaxRings ringsA, ringsB;
	private Node sceneB;
	private Camera camB;
	private ViewPort viewB;
	private final List<Material> grounds = new ArrayList<>();
	private BitmapFont font;
	private final Node hud = new Node("lab controls");
	private BitmapText tagA, tagB;
	private final FrameGrab grab = new FrameGrab();
	/** The --shot run: what is left to do, each step drawn (and grabbed when it names a file) before the next. */
	private final List<Object[]> steps = new ArrayList<>();
	private boolean shiftDown;
	private String failure;

	public JmeRingsLab(Path shotDir, List<String> shotScenes, Map<String, Float> given)
	{
		this.shotDir = shotDir;
		this.shotScenes = shotScenes;
		this.given = given;
	}

	public static void main(String[] args)
	{
		Path shot = null;
		List<String> scenes = List.of("g02", "g01", "g04");
		Map<String, Float> given = new LinkedHashMap<>();
		for (int i = 0; i < args.length; i++)
			if (args[i].equals("--shot"))
				shot = Paths.get(args[++i]);
			else if (args[i].equals("--scenes"))
				scenes = List.of(args[++i].trim().split("\\s+"));
			else if (args[i].startsWith("round="))
			{
				String r = args[i].substring("round=".length());
				round = r.startsWith("/") ? r.substring(1) : "engines/rounds/" + r;
			}
			else if (args[i].contains("="))
				given.put(args[i].substring(0, args[i].indexOf('=')), Float.parseFloat(args[i].substring(args[i].indexOf('=') + 1)));
		JmeRingsLab app = new JmeRingsLab(shot, scenes, given);
		AppSettings settings = new AppSettings(true);
		settings.setRenderer(AppSettings.LWJGL_OPENGL32);
		settings.setResolution(1600, shot != null ? 760 : 820);
		settings.setGammaCorrection(false);
		settings.setSamples(shot != null ? 0 : 4);
		settings.setVSync(shot == null);
		settings.setTitle("Rings lab (jME): a page round a 3D world");
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
		{
			for (JsonValue s = new JsonReader().parse(Files.readString(Paths.get(round, "round.json"))).get("scenes").child; s != null; s = s.next)
				scenes.add(s);
		}
		catch (IOException e)
		{throw new RuntimeException(e);}

		bg = new PlaxBackground();
		bg.autoAct = false;
		stateManager.attach(bg);
		// The rings draw the page; the flat background only moves (its act), drawn by nobody.
		bg.setEnabled(false);

		ringsA = new PlaxRings(assetManager, bg);
		ringsB = new PlaxRings(assetManager, bg);
		ringsA.effects = ringsB.effects = !BREAK_EFFECT;
		cam.setFrustumPerspective(60, 1, 0.5f, 20000);
		camB = cam.clone();
		sceneB = new Node("panel B");
		viewB = renderManager.createMainView("panel B", camB);
		viewB.setClearFlags(true, true, true);
		viewB.setBackgroundColor(ColorRGBA.Black);
		viewB.attachScene(sceneB);
		PlaxRings.sortIn(viewPort);
		PlaxRings.sortIn(viewB);
		for (Node root : new Node[] { rootNode, sceneB })
		{
			root.attachChild(valley());
			root.addLight(new AmbientLight(ColorRGBA.White));
		}
		rootNode.attachChild(ringsA);
		sceneB.attachChild(ringsB);

		font = assetManager.loadFont("Interface/Fonts/Default.fnt");
		guiNode.attachChild(hud);
		tagA = tag();
		tagB = tag();
		guiViewPort.addProcessor(grab);
		inputs();

		String first = shotDir != null ? shotScenes.get(0) : "g02";
		loadScene(first);
		if (shotDir != null)
			planShots();
	}

	// --- the world ---------------------------------------------------------------------------------------------------

	/** A valley 300 units across, its floor rolling, a ridge round it peaking near 50 (the rings' foot), snow on top. */
	private Geometry valley()
	{
		int rings = 70, around = 256;
		float R = 300;
		int n = (rings + 1) * (around + 1);
		float[] pos = new float[n * 3], col = new float[n * 4], nor = new float[n * 3];
		int at = 0;
		for (int i = 0; i <= rings; i++)
		{
			float r = R * i / rings;
			for (int j = 0; j <= around; j++, at++)
			{
				float a = FastMath.TWO_PI * j / around;
				float noise = FastMath.sin(a * 7 + 1.3f) * 0.5f + FastMath.sin(a * 13 + r * 0.02f) * 0.3f + FastMath.sin(a * 29 + 2.1f) * 0.2f;
				float ridge = smoothstep(150, 265, r) * (40 + 12 * noise);
				float floor = 2.5f * FastMath.sin(r * 0.05f + a * 3) * FastMath.cos(a * 5 - r * 0.03f);
				float y = Math.max(-1, ridge + floor);
				pos[at * 3] = r * FastMath.sin(a);
				pos[at * 3 + 1] = y;
				pos[at * 3 + 2] = -r * FastMath.cos(a);
				float[] c = y > 42 ? new float[] { 0.92f, 0.94f, 0.97f } : y > 22 ? new float[] { 0.45f, 0.43f, 0.4f } : new float[] { 0.32f, 0.45f, 0.22f };
				System.arraycopy(c, 0, col, at * 4, 3);
				col[at * 4 + 3] = 1;
			}
		}
		int[] index = new int[rings * around * 6];
		int k = 0;
		for (int i = 0; i < rings; i++)
			for (int j = 0; j < around; j++)
			{
				int a = i * (around + 1) + j, b = a + 1, c = a + around + 1, d = c + 1;
				int[] tri = { a, b, c, b, d, c };
				System.arraycopy(tri, 0, index, k, 6);
				k += 6;
			}
		// Smooth normals: each face's added to its corners.
		for (int t = 0; t < index.length; t += 3)
		{
			Vector3f p0 = vec(pos, index[t]), e1 = vec(pos, index[t + 1]).subtractLocal(p0), e2 = vec(pos, index[t + 2]).subtractLocal(p0);
			Vector3f f = e1.cross(e2);
			for (int c = 0; c < 3; c++)
			{
				nor[index[t + c] * 3] += f.x;
				nor[index[t + c] * 3 + 1] += f.y;
				nor[index[t + c] * 3 + 2] += f.z;
			}
		}
		// Lit once, as three.js's lab lights it (a hemisphere light and a sun that never move, MeshLambertMaterial, its
		// output in sRGB): the ground looks the same in both labs, and only the rings differ by engine.
		Vector3f sun = new Vector3f(-300, 400, -200).normalizeLocal();
		float[] sky = linear(0xdfe8ff), ground = linear(0x4a4030), sunColor = linear(0xfff2dd);
		for (int v = 0; v < n; v++)
		{
			Vector3f normal = vec(nor, v).normalizeLocal();
			float up = 0.5f * normal.y + 0.5f, lit = Math.max(0, normal.dot(sun));
			for (int c = 0; c < 3; c++)
			{
				float irradiance = 1.6f * (ground[c] + (sky[c] - ground[c]) * up) + 2.2f * lit * sunColor[c];
				col[v * 4 + c] = srgb(irradiance * col[v * 4 + c] / FastMath.PI);
			}
		}
		Mesh mesh = new Mesh();
		mesh.setBuffer(Type.Position, 3, BufferUtils.createFloatBuffer(pos));
		mesh.setBuffer(Type.Color, 4, BufferUtils.createFloatBuffer(col));
		mesh.setBuffer(Type.Normal, 3, BufferUtils.createFloatBuffer(nor));
		mesh.setBuffer(Type.Index, 3, BufferUtils.createIntBuffer(index));
		mesh.updateBound();
		Geometry g = new Geometry("valley", mesh);
		// Lighting for its fog only: a white ambient light gives back the colours lit above.
		Material m = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
		m.setBoolean("UseVertexColor", true);
		m.setBoolean("UseMaterialColors", true);
		m.setColor("Diffuse", ColorRGBA.Black);
		m.setColor("Specular", ColorRGBA.Black);
		m.setColor("Ambient", ColorRGBA.White);
		m.setBoolean("UseFog", true);
		g.setMaterial(m);
		grounds.add(m);
		return g;
	}

	/** A colour three.js is given as hex: sRGB, made linear by its colour management. */
	private static float[] linear(int rgb)
	{
		float[] c = new float[3];
		for (int k = 0; k < 3; k++)
		{
			float v = (rgb >> (16 - 8 * k) & 0xFF) / 255f;
			c[k] = v < 0.04045f ? v / 12.92f : (float) Math.pow((v + 0.055f) / 1.055f, 2.4f);
		}
		return c;
	}

	/** Linear to sRGB, as three.js encodes its output. */
	private static float srgb(float v)
	{
		v = Math.max(0, Math.min(1, v));
		return v < 0.0031308f ? v * 12.92f : 1.055f * (float) Math.pow(v, 1 / 2.4f) - 0.055f;
	}

	private static Vector3f vec(float[] a, int i)
	{return new Vector3f(a[i * 3], a[i * 3 + 1], a[i * 3 + 2]);}

	private static float smoothstep(float from, float to, float x)
	{
		float t = Math.max(0, Math.min(1, (x - from) / (to - from)));
		return t * t * (3 - 2 * t);
	}

	/** The camera round the valley, looking ahead and out toward the ridge and the sky, turned by look degrees. */
	private void fly(Camera camera, float t)
	{
		float a = t * 0.12f, r = knobs.get("orbit"), y = knobs.get("height") + 6 * FastMath.sin(t * 0.3f);
		camera.setLocation(new Vector3f(r * FastMath.sin(a), y, -r * FastMath.cos(a)));
		float heading = a + FastMath.HALF_PI - 0.6f + knobs.get("look") * FastMath.DEG_TO_RAD;
		camera.lookAt(new Vector3f(camera.getLocation().x + FastMath.sin(heading) * 100, y + 14,
				camera.getLocation().z - FastMath.cos(heading) * 100), Vector3f.UNIT_Y);
	}

	// --- scenes and knobs --------------------------------------------------------------------------------------------

	private WholePage_Model pageOf(JsonValue scene)
	{return PlaxBackground.loadPage(assetManager, scene.getString("page"));}

	private void loadScene(String id)
	{
		current = scenes.stream().filter(s -> s.getString("id").equals(id)).findFirst().orElse(scenes.get(0));
		WholePage_Model page = pageOf(current);
		bg.setPage(page, JmeAtlas.load(assetManager, current.getString("atlasDir") + "/" + page.pageModel.atlasName));
		knobs.clear();
		knobs.putAll(DEFAULTS);
		knobs.put("fogStrength", page.getFogStrength());
		for (Map.Entry<String, Float> e : given.entrySet())
			if (knobs.containsKey(e.getKey()))
				knobs.put(e.getKey(), e.getValue());
		opened.clear();
		opened.putAll(knobs);
		apply();
	}

	/** Cross-fades the rings into the next scene's page over {@code seconds}; the knobs stay the scene's that opened. */
	private void fadeToNext(float seconds)
	{
		JsonValue next = scenes.get((scenes.indexOf(current) + 1) % scenes.size());
		WholePage_Model page = pageOf(next);
		bg.transfertIntoPage(page, JmeAtlas.load(assetManager, next.getString("atlasDir") + "/" + page.pageModel.atlasName), seconds);
		current = next;
		say("fading into " + next.getString("id"));
	}

	private void apply()
	{
		for (PlaxRings p : new PlaxRings[] { ringsA, ringsB })
		{
			p.front = knobs.get("front");
			p.far = p == ringsA ? 0 : knobs.get("far");
			p.foot = knobs.get("foot");
			p.lift = knobs.get("lift");
			p.scale = knobs.get("scale");
			p.pageDegrees = knobs.get("pageDegrees");
			p.fogStrength = knobs.get("fogStrength");
			p.melt = knobs.get("melt");
			p.eyeMelt = knobs.get("eyeMelt");
			p.horizon = knobs.get("horizon");
			p.tintSky = knobs.get("tintSky") > 0;
		}
		float dusk = knobs.get("dusk");
		bg.tintTo(new Color(1 + (NIGHT.r - 1) * dusk, 1 + (NIGHT.g - 1) * dusk, 1 + (NIGHT.b - 1) * dusk, 1), 0);
		bg.act(0);
		ringsA.update(0);
		ringsB.update(0);
		// the world's fog in the rings' fog colour, tinted with tint sky
		float[] fog = ringsB.fogColor(bg.getPage());
		for (Material m : grounds)
		{
			// three.js's Fog takes the colour as linear, and its ground's output is encoded to sRGB
			m.setColor("FogColor", new ColorRGBA(srgb(fog[0]), srgb(fog[1]), srgb(fog[2]), 1));
			m.setVector2("LinearFog", new Vector2f(knobs.get("front") * 0.3f, knobs.get("worldFog")));
		}
		drawControls();
	}

	private void move(int steps)
	{
		Object[] k = KNOBS[picked];
		String key = (String) k[0];
		float min = (Float) k[2], max = (Float) k[3], step = (Float) k[4];
		float v = Math.round((knobs.get(key) + steps * step) / step) * step;
		knobs.put(key, Math.max(min, Math.min(max, v)));
		apply();
	}

	/** Every knob, what it opened at with this scene, and the ones moved, as JSON. */
	private String settings()
	{
		StringBuilder s = new StringBuilder("{\n \"scene\": \"" + current.getString("id") + "\",\n \"view\": \"" + view + "\",\n");
		s.append(" \"values\": ").append(object(knobs)).append(",\n \"opened\": ").append(object(opened)).append(",\n \"moved\": {");
		String sep = "";
		for (String k : knobs.keySet())
			if (!knobs.get(k).equals(opened.get(k)))
			{
				s.append(sep).append('"').append(k).append("\": [").append(num(opened.get(k))).append(", ").append(num(knobs.get(k))).append(']');
				sep = ", ";
			}
		return s.append("}\n}\n").toString();
	}

	private static String object(Map<String, Float> m)
	{
		StringBuilder s = new StringBuilder("{");
		String sep = "";
		for (Map.Entry<String, Float> e : m.entrySet())
		{
			s.append(sep).append('"').append(e.getKey()).append("\": ").append(num(e.getValue()));
			sep = ", ";
		}
		return s.append('}').toString();
	}

	private static String num(float v)
	{
		String s = String.format(Locale.ROOT, "%.4f", v).replaceAll("0+$", "");
		return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
	}

	/** Copy settings: on the clipboard and in build/jme/rings-lab/settings.json (or the shots' folder). */
	private String copySettings()
	{
		String text = settings();
		if (getContext() instanceof LwjglWindow)
			GLFW.glfwSetClipboardString(((LwjglWindow) getContext()).getWindowHandle(), text);
		Path out = shotDir != null ? shotDir.resolve("settings.json") : Paths.get("build", "jme", "rings-lab", "settings.json");
		try
		{
			Files.createDirectories(out.getParent());
			Files.writeString(out, text);
		}
		catch (IOException e)
		{throw new RuntimeException(e);}
		System.out.println(text);
		say("Copied (and in " + out + ")");
		return text;
	}

	private void say(String text)
	{
		flash = text;
		flashFor = 2.5f;
		drawControls();
	}

	// --- the controls ------------------------------------------------------------------------------------------------

	private BitmapText text(String s, float x, float y, ColorRGBA color, float size)
	{
		BitmapText t = new BitmapText(font);
		t.setSize(size);
		t.setColor(color);
		t.setText(s);
		t.setLocalTranslation(x, y, 1);
		hud.attachChild(t);
		return t;
	}

	private BitmapText tag()
	{
		BitmapText t = new BitmapText(font);
		t.setSize(16);
		t.setColor(ColorRGBA.White);
		guiNode.attachChild(t);
		return t;
	}

	/** Where each knob's row is, from the window's top: for a click. */
	private final List<float[]> rows = new ArrayList<>();

	private void drawControls()
	{
		hud.detachAllChildren();
		rows.clear();
		float h = settings.getHeight(), y = h - 10;
		Geometry back = new Geometry("controls back", new Quad(CONTROLS, h));
		Material m = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		m.setColor("Color", new ColorRGBA(0x1b / 255f, 0x1b / 255f, 0x1f / 255f, 1));
		back.setMaterial(m);
		hud.attachChild(back);
		ColorRGBA dim = new ColorRGBA(0.6f, 0.6f, 0.65f, 1), on = new ColorRGBA(0.45f, 0.65f, 1f, 1);
		StringBuilder views = new StringBuilder();
		for (String v : VIEWS)
			views.append(v.equals(view) ? "[" + label(v) + "]  " : label(v) + "  ");
		text("View (V): " + views, 12, y, ColorRGBA.White, 16);
		y -= 24;
		text("Scene (N next, F fade): " + current.getString("id") + " " + current.getString("name", ""), 12, y, ColorRGBA.White, 16);
		y -= 22;
		String section = null;
		for (int i = 0; i < KNOBS.length; i++)
		{
			Object[] k = KNOBS[i];
			if (!k[5].equals(section))
			{
				section = (String) k[5];
				y -= 6;
				text(section.toUpperCase(Locale.ROOT), 12, y, dim, 13);
				y -= 18;
			}
			boolean sel = i == picked;
			text((sel ? "> " : "  ") + k[1], 12, y, sel ? on : ColorRGBA.White, 16);
			text(num(knobs.get(k[0])), 210, y, sel ? on : ColorRGBA.White, 16);
			rows.add(new float[] { h - y, h - y + 20 });
			y -= 20;
		}
		y -= 10;
		text("up/down pick, left/right move (shift x10),", 12, y, dim, 13);
		y -= 16;
		text("wheel moves, R resets, Space " + (playing ? "pauses" : "plays") + ",", 12, y, dim, 13);
		y -= 16;
		text("C copies settings, a click on a panel zooms it", 12, y, dim, 13);
		y -= 24;
		for (String line : numbers().split("\n"))
		{
			text(line, 12, y, dim, 13);
			y -= 16;
		}
		if (flashFor > 0)
			text(flash, 12, 14, new ColorRGBA(0.75f, 1f, 0.75f, 1), 15);
	}

	private static String label(String view)
	{return view.equals("both") ? "A | B" : view.equals("a") ? "A" : "B";}

	/** The bands' radii, per panel, under the controls. */
	private String numbers()
	{
		StringBuilder s = new StringBuilder();
		for (PlaxRings p : new PlaxRings[] { ringsA, ringsB })
		{
			List<Band> bands = p.bands();
			s.append(p == ringsA ? "A" : "B").append(": ").append(bands.size()).append(" bands, radii\n ");
			for (Band b : bands)
				s.append(b.radius == Float.POSITIVE_INFINITY ? "sky" : String.valueOf(Math.round(b.radius))).append(' ');
			s.append('\n');
		}
		return s.toString();
	}

	private void inputs()
	{
		inputManager.addMapping("up", new KeyTrigger(KeyInput.KEY_UP));
		inputManager.addMapping("down", new KeyTrigger(KeyInput.KEY_DOWN));
		inputManager.addMapping("left", new KeyTrigger(KeyInput.KEY_LEFT));
		inputManager.addMapping("right", new KeyTrigger(KeyInput.KEY_RIGHT));
		inputManager.addMapping("shift", new KeyTrigger(KeyInput.KEY_LSHIFT), new KeyTrigger(KeyInput.KEY_RSHIFT));
		inputManager.addMapping("view", new KeyTrigger(KeyInput.KEY_V));
		inputManager.addMapping("next", new KeyTrigger(KeyInput.KEY_N));
		inputManager.addMapping("fade", new KeyTrigger(KeyInput.KEY_F));
		inputManager.addMapping("play", new KeyTrigger(KeyInput.KEY_SPACE));
		inputManager.addMapping("reset", new KeyTrigger(KeyInput.KEY_R));
		inputManager.addMapping("copy", new KeyTrigger(KeyInput.KEY_C));
		inputManager.addMapping("click", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
		inputManager.addMapping("wheelUp", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
		inputManager.addMapping("wheelDown", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));
		ActionListener on = (name, pressed, tpf) -> {
			if (name.equals("shift"))
			{
				shiftDown = pressed;
				return;
			}
			if (!pressed)
				return;
			int ten = shiftDown ? 10 : 1;
			switch (name)
			{
				case "up": picked = (picked + KNOBS.length - 1) % KNOBS.length; drawControls(); break;
				case "down": picked = (picked + 1) % KNOBS.length; drawControls(); break;
				case "left": case "wheelDown": move(-ten); break;
				case "right": case "wheelUp": move(ten); break;
				case "view": view = VIEWS[(java.util.Arrays.asList(VIEWS).indexOf(view) + 1) % VIEWS.length]; zoomed = null; drawControls(); break;
				case "next": loadScene(scenes.get((scenes.indexOf(current) + 1) % scenes.size()).getString("id")); break;
				case "fade": fadeToNext(4); break;
				case "play": playing = !playing; drawControls(); break;
				case "reset": knobs.putAll(opened); apply(); break;
				case "copy": copySettings(); break;
				case "click": click(inputManager.getCursorPosition()); break;
				default: break;
			}
		};
		inputManager.addListener(on, "up", "down", "left", "right", "shift", "view", "next", "fade", "play", "reset", "copy", "click",
				"wheelUp", "wheelDown");
	}

	private void click(Vector2f at)
	{
		float fromTop = settings.getHeight() - at.y;
		if (at.x < CONTROLS)
		{
			for (int i = 0; i < rows.size(); i++)
				if (fromTop >= rows.get(i)[0] - 16 && fromTop < rows.get(i)[1] - 16)
				{
					picked = i;
					drawControls();
				}
			return;
		}
		if (zoomed != null)
			zoomed = null;
		else if (view.equals("both"))
			zoomed = at.x < CONTROLS + (settings.getWidth() - CONTROLS) / 2f ? "a" : "b";
	}

	// --- each frame --------------------------------------------------------------------------------------------------

	/** Each panel's share of the window, left to right, as {name, x0, x1} in 0..1. */
	private List<Object[]> panels()
	{
		float left = CONTROLS / (float) settings.getWidth();
		List<String> shown = zoomed != null ? List.of(zoomed) : view.equals("both") ? List.of("a", "b") : List.of(view);
		List<Object[]> out = new ArrayList<>();
		for (int i = 0; i < shown.size(); i++)
			out.add(new Object[] { shown.get(i), left + (1 - left) * i / shown.size(), left + (1 - left) * (i + 1) / shown.size() });
		return out;
	}

	private void layout()
	{
		viewPort.setEnabled(false);
		viewB.setEnabled(false);
		tagA.setCullHint(Node.CullHint.Always);
		tagB.setCullHint(Node.CullHint.Always);
		for (Object[] p : panels())
		{
			boolean a = p[0].equals("a");
			Camera c = a ? cam : camB;
			float x0 = (Float) p[1], x1 = (Float) p[2];
			c.setViewPort(x0, x1, 0, 1);
			c.setFrustumPerspective(60, (x1 - x0) * settings.getWidth() / settings.getHeight(), 0.5f, 20000);
			fly(c, flight);
			(a ? viewPort : viewB).setEnabled(true);
			BitmapText t = a ? tagA : tagB;
			t.setCullHint(Node.CullHint.Inherit);
			t.setText(a ? "A  library depth (front / speed)" : "B  compressed into " + num(knobs.get("front")) + ".." + num(knobs.get("far")));
			t.setLocalTranslation(x0 * settings.getWidth() + 10, settings.getHeight() - 10, 2);
		}
	}

	@Override
	public void simpleUpdate(float tpf)
	{
		if (shotDir != null)
			shoot();
		else
		{
			float dt = Math.min(0.1f, tpf);
			if (playing)
			{
				flight += dt * knobs.get("speed");
				bg.act(dt);
			}
			ringsA.update(0);
			ringsB.update(0);
			if (flashFor > 0 && (flashFor -= tpf) <= 0)
				drawControls();
		}
		layout();
		sceneB.updateLogicalState(tpf);
		sceneB.updateGeometricState();
	}

	/** The flight at {@code t} seconds, the page's drift and fades {@code t} seconds in. */
	private void at(float t)
	{
		bg.resetPositions();
		bg.act(t);
		flight = t * knobs.get("speed");
		ringsA.update(0);
		ringsB.update(0);
	}

	/**
	 * The look (degrees) that turns the camera, at the flight now, to where panel B's band of the first SHADER layer
	 * closes its ring (its tile repeat-1 meeting tile 0, at the band's angle 0, turned by the scroll as PlaxRings.sync
	 * turns it); null for a page without one. three.js's ringsLab.lookAtJoin (r58).
	 */
	private Float lookAtJoin()
	{
		List<jks.tools2d.parallax.ParallaxLayer> layers = bg.getReader().layers;
		for (Band band : ringsB.bands())
		{
			jks.tools2d.parallax.ParallaxLayer l = layers.get(band.layer);
			if (l.getDrawnEffect() == null)
				continue;
			float tile = l.getTotalWidth();
			float turned = tile > 0 ? l.getCurrentDistanceX() / tile * FastMath.TWO_PI / band.repeat : 0, R = band.radius;
			float a = flight * 0.12f, r = knobs.get("orbit");
			float dx = R * FastMath.sin(turned) - r * FastMath.sin(a), dz = -R * FastMath.cos(turned) + r * FastMath.cos(a);
			float heading = FastMath.atan2(dx, -dz);
			float look = ((heading - (a + FastMath.HALF_PI - 0.6f)) * FastMath.RAD_TO_DEG + 540) % 360 - 180;
			return (float) Math.round(look);
		}
		return null;
	}

	private void step(float t)
	{
		bg.act(t);
		ringsA.update(0);
		ringsB.update(0);
	}

	private void planShots()
	{
		playing = false;
		for (String scene : shotScenes)
		{
			steps.add(new Object[] { (Runnable) () -> loadScene(scene), null });
			for (int t : new int[] { 0, 8, 20 })
				steps.add(new Object[] { (Runnable) () -> at(t), scene + "-both-t" + t + ".png" });
			// A page with a SHADER layer: panel B turned to where its band closes the ring (r58), at 8 s; the scene's
			// look put back after. Planned now, it is named when its page is loaded: a still only for such a page.
			steps.add(new Object[] { (Runnable) () -> {
				at(8);
				Float look = lookAtJoin();
				if (look != null)
				{
					steps.add(0, new Object[] { (Runnable) () -> { knobs.put("look", look); at(8); }, scene + "-join-t8.png" });
					steps.add(1, new Object[] { (Runnable) () -> { knobs.put("look", opened.get("look")); at(8); }, null });
				}
			}, null });
		}
		String last = shotScenes.get(shotScenes.size() - 1);
		// A cross-fade into the next scene's page, halfway (2 s of 4): both pages' bands at their alphas.
		steps.add(new Object[] { (Runnable) () -> { at(8); fadeToNext(4); }, null });
		steps.add(new Object[] { (Runnable) () -> step(2), last + "-fade-half.png" });
		steps.add(new Object[] { (Runnable) () -> step(2.5f), last + "-fade-done.png" });
		// Copy settings: a knob moved shows in "moved", from what it opened at.
		steps.add(new Object[] { (Runnable) () -> {
			float was = knobs.get("front");
			knobs.put("front", 400f);
			apply();
			if (!copySettings().contains("\"front\": [" + num(was) + ", 400]"))
				failure = "Copy settings: front moved to 400 is not in its moved";
		}, null });
	}

	/** One step of the --shot run a frame, the frame grabbed when the step names a file. */
	private void shoot()
	{
		if (grab.isPending())
			return;
		if (steps.isEmpty())
		{
			System.out.println(failure != null ? "FAIL: " + failure : "rings lab: " + shotScenes.size() + " scenes into " + shotDir + ", PASS");
			stop();
			return;
		}
		Object[] s = steps.remove(0);
		((Runnable) s[0]).run();
		if (s[1] != null)
		{
			drawControls();
			grab.grab(new File(shotDir.toFile(), (String) s[1]));
			System.out.println(s[1] + ": " + settings().replace('\n', ' '));
		}
	}
}
