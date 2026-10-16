package jks.tools2d.parallax.browsertest;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.SelectElement;

import jks.tools2d.parallax.GdxLayerEffects;
import jks.tools2d.parallax.LayerEffects;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.pages.Enum_LayerKind;
import jks.tools2d.parallax.pages.Enum_ShaderEffect;
import jks.tools2d.parallax.pages.Parallax_Model;
import jks.tools2d.parallax.pages.Utils_Page_Json;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * The SWELL edges lab (r434, Simon on its question: "I see no difference, we need a lab"): SWELL (r431) reads each
 * pixel from where its waves move it, the image wrapped across and held at its top and bottom rows, whatever the page
 * repeats on. Three panels of one page, drawn by the real reader: A through SWELL as it ships, B through SWELL wrapping
 * only on the axes the page repeats on and holding on the others (the choice r434 asks), C red where A and B differ,
 * over the picture dimmed. The page is the shaders round's checker (core/test-data/shaders/checker.png, 32 px squares)
 * tiled on X and Y, on Y only, on neither, on X only, or r365's beach sea as it ships (tiled on X: both rules agree).
 * B and C are lab GLSL: GdxLayerEffects' SWELL with its one wrap line replaced, so the lab follows the shipped effect.
 * Opened with effects-lab.html?lab=swell: tools/effects-lab.sh --lab swell.
 * <p>
 * {@code &clip=1}: nothing moves by itself, {@code window.effectsLabAct(seconds)} steps the hearts at 1/60 s.
 */
public class SwellLab extends ApplicationAdapter implements LabKit.Clip
{
	static final String CHECKER_PAGE = "swell-lab/checker.jplax", SEA_PAGE = "anim-lab/beach.jplax";
	static final float WORLD_WIDTH = FogLab.WORLD_WIDTH, WORLD_HEIGHT = FogLab.WORLD_HEIGHT;
	/** Three 16:9 panels in a row, as wide as fits over the sliders at 1580 x 800. */
	static final int PANEL_WIDTH = 506, PANEL_HEIGHT = 285;

	static final String[] CASES = { "checker tiled on X and Y", "checker tiled on Y only", "checker on neither axis", "checker tiled on X only",
			"beach sea, as it ships (X only)" };
	/** Each case's page: what it repeats on, and its SWELL layer's width (of the world) and offsets (percent). */
	static final boolean[] REPEAT_X = { true, false, false, true, true }, REPEAT_Y = { true, true, false, false, false };
	static final float[] SIZE = { 0.45f, 0.6f, 0.6f, 0.45f, 0 }, DECAL_X = { 0, 20, 20, 0, 0 }, DECAL_Y = { 0, 0, 30, 30, 0 };
	/**
	 * Where each case's frame is held under the zoom, as fractions of the frame from its left and bottom, at the panels'
	 * middle: the checker's left side where the page does not repeat on X, its middle, the beach's sea.
	 */
	static final float[] FOCUS_X = { 0.5f, 0.22f, 0.22f, 0.5f, 0.5f }, FOCUS_Y = { 0.5f, 0.5f, 0.43f, 0.4f, 0.12f };
	/** The sea layer of the beach page (AnimLab's). */
	static final String SEA = "l1";

	/** What a panel draws the SWELL layer through. */
	static final int SHIPS = 0, PER_AXIS = 1, DIFFER = 2;

	/** GdxLayerEffects' SWELL wrap, which B and C replace: the source fails here loudly if it changes. */
	static final String WRAP = "	vec2 f = vec2(fract(l.x / u_size.x), clamp(l.y / u_size.y, 0.0, 1.0));\n";
	static final String DRAW = "	vec4 image = texture2D(u_texture, uv);\n	gl_FragColor = hazed(glinted(v_color * image, image, uv));\n";
	/** B's: u_wrap 1 on an axis the page repeats on (fract), 0 on one it does not (held at the edge rows). */
	static final String WRAPPED = "uniform vec2 u_wrap;\n"
			+ "vec2 wrapped(vec2 f)\n"
			+ "{\n"
			+ "	return mix(clamp(f, 0.0, 1.0), fract(f), u_wrap);\n"
			+ "}\n";
	/** C's: B's pixel, red where A reads another one (any channel 0.05 off), the rest dimmed. */
	static final String DIFFERS = "	vec4 image = texture2D(u_texture, uv);\n"
			+ "	vec2 f0 = vec2(fract(l.x / u_size.x), clamp(l.y / u_size.y, 0.0, 1.0));\n"
			+ "	vec4 ships = texture2D(u_texture, vec2(mix(u_region.x, u_region.z, f0.x), mix(u_region.w, u_region.y, f0.y)));\n"
			+ "	float d = step(0.05, max(max(abs(image.r - ships.r), abs(image.g - ships.g)), max(abs(image.b - ships.b), abs(image.a - ships.a))));\n"
			+ "	gl_FragColor = vec4(mix(0.3 * image.rgb, vec3(1.0, 0.1, 0.1), d), max(image.a, d) * v_color.a);\n";

	private final Parallax_Heart[] hearts = new Parallax_Heart[CASES.length];
	private final ParallaxLayer[] swells = new ParallaxLayer[CASES.length];
	private final SwellEffects[] effects = new SwellEffects[3];
	private SelectElement view;
	private InputElement amplitude, wavelength, speed, scrollUp, scroll, zoom;
	private Element readout;
	private boolean clip;
	private SpriteBatch batch;

	@Override
	public void create()
	{
		clip = "1".equals(com.google.gwt.user.client.Window.Location.getParameter("clip"));
		batch = new SpriteBatch();
		for (int i = 0; i < CASES.length; i++)
		{
			boolean sea = i == CASES.length - 1;
			WholePage_Model page = Utils_Page_Json.loadPage(sea ? SEA_PAGE : CHECKER_PAGE);
			int index = 0;
			for (int k = page.pageModel.pageList.size() - 1; k >= 0; k--)
				if (!sea || SEA.equals(page.pageModel.pageList.get(k).regionName))
					index = k;
			Parallax_Model model = page.pageModel.pageList.get(index);
			model.kind = Enum_LayerKind.SHADER;
			model.shaderEffect = Enum_ShaderEffect.SWELL;
			if (!sea)
			{
				page.repeatOnX = REPEAT_X[i];
				page.repeatOnY = REPEAT_Y[i];
				model.sizeRatio = SIZE[i];
				model.decal_X_Ratio = DECAL_X[i];
				model.decal_Y_Ratio = DECAL_Y[i];
			}
			OrthographicCamera camera = new OrthographicCamera();
			camera.setToOrtho(false, WORLD_WIDTH, WORLD_HEIGHT);
			hearts[i] = new Parallax_Heart(camera, batch, WORLD_WIDTH, WORLD_HEIGHT);
			hearts[i].setPage(page);
			swells[i] = hearts[i].parallaxReader.layers.get(index);
		}
		for (int k = 0; k < effects.length; k++)
			effects[k] = new SwellEffects(k);
		buildControls();
		applyControls();
		if (clip)
			LabKit.exportClip("effectsLab", this);
	}

	private void buildControls()
	{
		Document document = Document.get();
		document.setTitle("Effects lab: SWELL's edges (r434)");
		document.getElementById("effects-title").setInnerText("Effects lab: SWELL's edges");
		Element stage = document.getElementById("effects-lab");
		stage.addClassName("lab-three");
		stage.getStyle().setProperty("maxWidth", (3 * PANEL_WIDTH + 8) + "px");
		LabControls.panelLabels(document, "effects-labels", "A: as it ships", "B: wraps per axis", "C: red where A and B differ");
		view = LabControls.stageChoice(document, "effects-labels", "page", "the page: the checker tiled on X and Y, on Y only, on neither, on X only, or the beach sea as it ships", CASES, 0);
		Element controls = document.getElementById("effects-controls");
		amplitude = LabControls.slider(document, controls, "swellAmplitude", "SWELL amplitude (world units: the image moves up to this sideways, half of it up and down; the round's checker 0.3)", 0, 1, 0.01f, 0.3f);
		wavelength = LabControls.slider(document, controls, "swellWavelength", "SWELL wavelength (world units)", 0.25f, 6, 0.05f, 1.25f);
		speed = LabControls.slider(document, controls, "swellSpeed", "SWELL speed (world units/s)", 0, 2, 0.05f, 0.3f);
		scrollUp = LabControls.slider(document, controls, "scrollUp", "scroll up (world units/s: a page tiled on Y wraps up the view, so its tile edges cross it)", 0, 60, 1, 10);
		scroll = LabControls.slider(document, controls, "cameraScroll", "camera scroll (world units/s, across)", -80, 80, 1, 0);
		zoom = LabControls.slider(document, controls, "zoom", "zoom (x the page's frame, held on the checker's left side where the page does not repeat on X, on the sea: 1 the whole frame)", 1, 6, 0.25f, 3);
		readout = LabControls.readout(document, controls, "effects-readout");
	}

	/** The sliders into every heart, and the readout. */
	private void applyControls()
	{
		float a = LabControls.read(amplitude), w = LabControls.read(wavelength), s = LabControls.read(speed);
		float up = LabControls.read(scrollUp), across = LabControls.read(scroll);
		LabControls.read(zoom);
		for (int i = 0; i < hearts.length; i++)
		{
			swells[i].setShaderAmplitude(a);
			swells[i].setShaderWavelength(w);
			swells[i].setShaderSpeed(s);
			// A page that does not repeat on Y would scroll off the view: only those tiled on Y scroll up.
			hearts[i].screenSpeedConstantY = REPEAT_Y[i] ? up : 0;
			hearts[i].screenSpeedConstantX = across;
		}
		ParallaxLayer layer = swells[LabControls.read(view)];
		float px = layer.getDrawnRegion().getRegionHeight() / (layer.getHeight() * layer.getPackedHeightRatio());
		readout.setInnerText("SWELL layer " + two(layer.getWidth()) + " x " + two(layer.getHeight()) + " world units, its image " + two(px)
				+ " px a unit: up to " + two(a * px) + " px sideways and " + two(a / 2 * px) + " px up and down. A holds its top and bottom rows: a band "
				+ "up to that tall at every tile edge on Y. A wraps across: on a page that does not repeat on X, up to " + two(a * px)
				+ " px of the image's other side come in at each side. B holds on an axis the page does not repeat on and wraps on one it does.");
	}

	private static String two(float value)
	{return String.valueOf(Math.round(value * 100) / 100.0);}

	@Override
	public void act(float seconds)
	{
		applyControls();
		LabKit.clip(seconds, hearts);
	}

	@Override
	public void render()
	{
		if (!clip)
		{
			applyControls();
			LabKit.act(LabKit.liveDelta(), hearts);
		}
		int i = LabControls.read(view), height = Gdx.graphics.getBackBufferHeight();
		float z = LabControls.read(zoom);
		int frameWidth = Math.round(PANEL_WIDTH * z), frameHeight = Math.round(frameWidth * WORLD_HEIGHT / WORLD_WIDTH);
		LabKit.beginPanels();
		for (int k = 0; k < effects.length; k++)
		{
			int x = k * (PANEL_WIDTH + 4);
			Gdx.gl.glScissor(x, height - PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);
			int left = Math.max(x + PANEL_WIDTH - frameWidth, Math.min(x, Math.round(x + PANEL_WIDTH / 2f - FOCUS_X[i] * frameWidth)));
			int bottom = Math.max(height - frameHeight, Math.min(height - PANEL_HEIGHT, Math.round(height - PANEL_HEIGHT / 2f - FOCUS_Y[i] * frameHeight)));
			Gdx.gl.glViewport(left, bottom, frameWidth, frameHeight);
			effects[k].wrap(REPEAT_X[i], REPEAT_Y[i]);
			hearts[i].parallaxReader.setLayerEffects(effects[k]);
			hearts[i].render();
		}
		LabKit.endPanels();
	}

	@Override
	public void dispose()
	{batch.dispose();}

	/**
	 * A panel's effects: the SWELL layer as it ships (GdxLayerEffects itself), or through B's or C's GLSL, set as
	 * GdxLayerEffects sets its own; every other layer as it ships.
	 */
	static final class SwellEffects implements LayerEffects
	{
		private static ShaderProgram perAxis, differ;

		private final int kind;
		private final GdxLayerEffects shipped = new GdxLayerEffects();
		private final float[] numbers = new float[15], glints = new float[14];
		private float viewWidth, viewHeight, viewLeft, viewFloor, wrapX, wrapY;
		private boolean byShipped;
		private ShaderProgram previous;

		SwellEffects(int kind)
		{this.kind = kind;}

		void wrap(boolean x, boolean y)
		{
			wrapX = x ? 1 : 0;
			wrapY = y ? 1 : 0;
		}

		@Override
		public void setView(float width, float height, float floor)
		{setView(width, height, 0, floor);}

		@Override
		public void setView(float width, float height, float left, float floor)
		{
			viewWidth = width;
			viewHeight = height;
			viewLeft = left;
			viewFloor = floor;
			shipped.setView(width, height, left, floor);
		}

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase)
		{return begin(batch, layer, phase, 0, Color.WHITE);}

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase, float fogAmount, Color fogColor)
		{
			byShipped = kind == SHIPS || layer.getKind() != Enum_LayerKind.SHADER || layer.getShaderEffect() != Enum_ShaderEffect.SWELL;
			if (byShipped)
				return shipped.begin(batch, layer, phase, fogAmount, fogColor);
			ShaderProgram program = program(kind);
			previous = batch.getShader();
			batch.setShader(program);
			if (!batch.isDrawing())
				program.bind();
			GdxLayerEffects.uniforms(layer, phase, viewWidth, viewHeight, viewLeft, viewFloor, numbers);
			LabKit.setEffect(program, numbers, fogAmount, fogColor.r, fogColor.g, fogColor.b);
			// As GdxLayerEffects': unset, u_glintB's zw are 0 and divide along() (r419's white sea).
			GdxLayerEffects.glintUniforms(layer, glints);
			program.setUniformf(program.fetchUniformLocation("u_glint", false), glints[0], glints[1], glints[2], glints[3]);
			program.setUniformf(program.fetchUniformLocation("u_sun", false), glints[4], glints[5]);
			program.setUniformf(program.fetchUniformLocation("u_glintA", false), glints[6], glints[7], glints[8], glints[9]);
			program.setUniformf(program.fetchUniformLocation("u_glintB", false), glints[10], glints[11], glints[12], glints[13]);
			program.setUniformf(program.fetchUniformLocation("u_wrap", false), wrapX, wrapY);
			return true;
		}

		@Override
		public void end(Batch batch, ParallaxLayer layer)
		{
			if (byShipped)
			{
				shipped.end(batch, layer);
				return;
			}
			batch.setShader(previous);
			previous = null;
		}

		@Override
		public void release(Batch batch)
		{shipped.release(batch);}

		static ShaderProgram program(int kind)
		{
			if (kind == PER_AXIS)
				return perAxis != null ? perAxis : (perAxis = compile(source(false), "B"));
			return differ != null ? differ : (differ = compile(source(true), "C"));
		}

		/** GdxLayerEffects' SWELL, its wrap B's, and with {@code differs} its draw C's. */
		static String source(boolean differs)
		{
			String swell = GdxLayerEffects.fragment(Enum_ShaderEffect.SWELL);
			if (!swell.contains(WRAP) || !swell.contains(DRAW) || !swell.contains("void main()"))
				throw new IllegalStateException("GdxLayerEffects' SWELL no longer has the wrap or the draw the lab replaces");
			swell = swell.replace("void main()", WRAPPED + "void main()").replace(WRAP, "	vec2 f = wrapped(l / u_size);\n");
			return differs ? swell.replace(DRAW, DIFFERS) : swell;
		}

		private static ShaderProgram compile(String fragment, String name)
		{
			ShaderProgram program = new ShaderProgram(GdxLayerEffects.vertex(), fragment);
			if (!program.isCompiled())
				throw new IllegalStateException("the lab's " + name + " does not compile: " + program.getLog());
			return program;
		}
	}
}
