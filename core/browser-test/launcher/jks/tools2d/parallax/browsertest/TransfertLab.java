package jks.tools2d.parallax.browsertest;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.SelectElement;

import java.util.ArrayList;

import jks.tools2d.parallax.GdxLayerEffects;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.TransfertStyle;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.pages.Utils_Page_Json;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * The transfert lab (r220): the transfert (the cross-fade from one page into another) as it ships, and three ways an
 * effect could carry it, side by side, going back and forth between two of round1's pages. Every panel runs the
 * reader's own transfert ({@link Parallax_Heart#transfertIntoPage}: both pages scroll, the gradients fade); they differ
 * in how they draw it.
 * <ul>
 * <li>A: as it ships, {@link Parallax_Heart#render}: each layer slot fades from the old page's layer to the new one's, in
 * the library's depth stagger ({@link TransfertStyle#depthStagger}, r249) at the stagger slider's value.</li>
 * <li>B: the same, through a colour grade, with what a game has today: {@link ParallaxPageReader#addColorTransfert}
 * tints the page toward the grade colour over the first half, back to white over the second. The tint multiplies, so
 * it can darken and colour, never lighten.</li>
 * <li>C: fog creep: the mist's white rolls in in patches, far layers first, the page is swapped under the full fog, and
 * the fog clears, near layers first.</li>
 * <li>D: dissolve: in every layer slot, the new page's layer eats the old one in patches, back slots first: the
 * library's {@link TransfertStyle#dissolve} (r250) at the patch, softness and stagger sliders' values.</li>
 * <li>E: through a colour: in every layer slot, the old page's layer goes to the colour, then the new one comes out of
 * it, and the gradients with them: the library's {@link TransfertStyle#throughColor} (r251), which, unlike B's tint,
 * can go to white, at E's colour and the stagger slider's value.</li>
 * </ul>
 * A, B and C on top, D and E below.
 * C is this lab's shader, not the library's: GdxLayerEffects' PLAIN with a mask from FOG's noise (r216), in screen
 * pixels. The lab hands the reader one layer at a time, as {@link FogLab} does. Opened with transfert-lab.html:
 * tools/transfert-lab.sh.
 * <p>
 * {@code &clip=1}: nothing moves by itself, {@code window.transfertLabAct(seconds)} steps the lab at 1/60 s.
 */
public class TransfertLab extends ApplicationAdapter
{
	static final float WORLD_WIDTH = FogLab.WORLD_WIDTH, WORLD_HEIGHT = FogLab.WORLD_HEIGHT;
	static final int FRAME_WIDTH = FogLab.FRAME_WIDTH, FRAME_HEIGHT = FogLab.FRAME_HEIGHT, PANEL_HEIGHT = FogLab.PANEL_HEIGHT;
	/** round1's pages, as the FOG lab ships them: they sit at the bottom of the world, the panel shows its lower part. */
	static final String[] PAGES = { "fog-lab/s02.jplax", "fog-lab/s05.jplax", "fog-lab/s06.jplax" };
	static final String[] PAGE_NAMES = { "calm", "PurpleFairy", "OneNight" };
	/** The pairs the slider picks: each goes from its first page into its second and back. */
	static final int[][] PAIRS = { { 0, 2 }, { 2, 1 }, { 1, 0 } };
	static final String[] TITLES = { "A: the transfert as it ships (each layer cross-fades, depth stagger)", "B: through a colour grade (tint, shipped API)",
			"C: fog creep (the mist rolls in, the page swaps under it)", "D: dissolve (patches, back layers first)",
			"E: through a colour (the library's, white possible)" };
	/** B's grade colours: what the tint goes through at the middle of the transfert. */
	static final String[] GRADE_NAMES = { "dusk", "night blue", "black" };
	static final float[][] GRADES = { { 1, 0.5f, 0.35f }, { 0.25f, 0.3f, 0.6f }, { 0, 0, 0 } };
	/** E's colours: what each layer slot and the gradients go through; white first, which B's tint cannot reach. */
	static final String[] THROUGH_NAMES = { "white", "dusk", "night blue", "black" };
	static final float[][] THROUGH = { { 1, 1, 1 }, GRADES[0], GRADES[1], GRADES[2] };
	/** Panels a row: A, B, C on top, D, E below. */
	static final int COLUMNS = 3;
	/** What the lab opens on. */
	static final float SECONDS = 3, HOLD = 1.5f, SCROLL = 40, STAGGER = 0.5f, PATCH = 5, SOFT = 0.08f;
	static final int PAIR = 0, GRADE = 0, THROUGH_COLOR = 0;

	/** Each pair as its choice names it, an arrow between: "calm \u2194 OneNight". */
	static String[] pairNames()
	{
		String[] names = new String[PAIRS.length];
		for (int i = 0; i < PAIRS.length; i++)
			names[i] = PAGE_NAMES[PAIRS[i][0]] + " \u2194 " + PAGE_NAMES[PAIRS[i][1]];
		return names;
	}

	/** Mask modes of the lab's shader: keep where the noise is over the ramp, under it, or haze by it. */
	static final int OUTGOING = 0, INCOMING = 1, HAZE = 2;

	private final Parallax_Heart[] hearts = new Parallax_Heart[TITLES.length];
	/** Each heart's own copy of every page: a page's layers belong to the heart that shows them. */
	private final WholePage_Model[][] models = new WholePage_Model[TITLES.length][PAGES.length];
	private final ArrayList<ParallaxLayer> one = new ArrayList<>(1), none = new ArrayList<>(0);
	private InputElement seconds, hold, scroll, stagger, patch, soft;
	private SelectElement pair, grade, through;
	private Element readout;
	private ShaderProgram masked;
	private SpriteBatch batch;
	private Texture white;
	private boolean clip;

	/** The pair shown, the page on screen (0 or 1 of it), the seconds left on hold, the seconds into the transfert. */
	private int shown = -1, side;
	private float holdLeft, since, duration;
	private boolean inTransfert;
	private float clock;

	@Override
	public void create()
	{
		clip = "1".equals(com.google.gwt.user.client.Window.Location.getParameter("clip"));
		batch = new SpriteBatch();
		Pixmap pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
		pixel.setColor(Color.WHITE);
		pixel.fill();
		white = new Texture(pixel);
		pixel.dispose();
		for (int i = 0; i < hearts.length; i++)
		{
			OrthographicCamera camera = new OrthographicCamera();
			camera.setToOrtho(false, WORLD_WIDTH, WORLD_HEIGHT);
			hearts[i] = new Parallax_Heart(camera, batch, WORLD_WIDTH, WORLD_HEIGHT);
			for (int k = 0; k < PAGES.length; k++)
				models[i][k] = Utils_Page_Json.loadPage(PAGES[k]);
		}
		buildControls();
		applyControls();
		if (clip)
			exportAct(this);
	}

	private void buildControls()
	{
		Document document = Document.get();
		LabControls.panelLabels(document, "transfert-labels", TITLES);
		Element controls = document.getElementById("transfert-controls");
		pair = LabControls.choice(document, controls, "pagePair", "pages", pairNames(), PAIR);
		seconds = LabControls.slider(document, controls, "seconds", "transfert length (s)", 0.5f, 8, 0.25f, SECONDS);
		hold = LabControls.slider(document, controls, "hold", "hold between transferts (s)", 0.5f, 5, 0.25f, HOLD);
		scroll = LabControls.slider(document, controls, "cameraScroll", "camera scroll", 0, 240, 5, SCROLL);
		grade = LabControls.choice(document, controls, "gradeColour", "B: grade colour", GRADE_NAMES, GRADE);
		through = LabControls.choice(document, controls, "throughColour", "E: through colour", THROUGH_NAMES, THROUGH_COLOR);
		stagger = LabControls.slider(document, controls, "depthStagger", "A, C, D, E: depth stagger (0 = every layer at once)", 0, TransfertStyle.MAX_STAGGER, 0.1f, STAGGER);
		patch = LabControls.slider(document, controls, "patches", "C, D: patches across the panel", 1, 16, 0.5f, PATCH);
		soft = LabControls.slider(document, controls, "softEdge", "C, D: patch edge softness", 0.01f, 0.4f, 0.01f, SOFT);
		readout = LabControls.readout(document, controls, "transfert-readout");
	}




	/** Puts the sliders into the hearts; a new pair starts over on its first page. */
	private void applyControls()
	{
		int p = LabControls.read(pair);
		LabControls.read(seconds);
		LabControls.read(hold);
		LabControls.read(stagger);
		LabControls.read(patch);
		LabControls.read(soft);
		if (p != shown)
		{
			shown = p;
			side = 0;
			for (int i = 0; i < hearts.length; i++)
			{
				hearts[i].setPage(models[i][PAIRS[p][0]]);
				hearts[i].parallaxReader.addColorTransfert(Color.WHITE, 0);
			}
			inTransfert = false;
			holdLeft = LabControls.read(hold);
		}
		float sc = LabControls.read(scroll);
		for (Parallax_Heart heart : hearts)
			heart.screenSpeedConstantX = sc;
		readout.setInnerText(PAGE_NAMES[PAIRS[shown][side]] + (inTransfert
				? " -> " + PAGE_NAMES[PAIRS[shown][1 - side]] + ", " + Math.round(100 * progress()) + "% into the transfert"
				: ", on hold " + LabControls.text(Math.max(0, holdLeft)) + " s"));
	}

	/** How far into the transfert, 0 to 1; 0 on hold. */
	float progress()
	{return inTransfert ? Math.min(1, since / duration) : 0;}

	/** One step of the lab's clock: holds, starts the transferts and B's grade, and acts every heart. */
	private void step(float delta)
	{
		clock += delta;
		if (!inTransfert)
		{
			holdLeft -= delta;
			if (holdLeft <= 0)
			{
				duration = LabControls.read(seconds);
				int into = PAIRS[shown][1 - side];
				for (int i = 0; i < hearts.length; i++)
					hearts[i].transfertIntoPage(models[i][into], duration, i == 0 ? style() : i == 3 ? dissolve() : i == 4 ? throughColor() : TransfertStyle.FADE);
				float[] g = GRADES[LabControls.read(grade)];
				hearts[1].parallaxReader.addColorTransfert(new Color(g[0], g[1], g[2], 1), duration / 2);
				inTransfert = true;
				since = 0;
			}
		}
		else
		{
			float before = since;
			since += delta;
			if (before < duration / 2 && since >= duration / 2)
				hearts[1].parallaxReader.addColorTransfert(Color.WHITE, duration / 2);
			if (since >= duration && !hearts[0].parallaxReader.isInTransfer())
			{
				inTransfert = false;
				side = 1 - side;
				holdLeft = LabControls.read(hold);
			}
		}
		for (Parallax_Heart heart : hearts)
			heart.act(delta);
	}

	/** Steps the lab by {@code seconds}, at 1/60 s: the clip's clock. */
	void act(float seconds)
	{
		applyControls();
		for (float t = 0; t < seconds - 1e-4f; t += 1 / 60f)
			step(1 / 60f);
		applyControls();
	}

	private static native void exportAct(TransfertLab lab) /*-{
		$wnd.transfertLabAct = $entry(function(seconds) {
			lab.@jks.tools2d.parallax.browsertest.TransfertLab::act(F)(seconds);
		});
		$wnd.transfertLabProgress = $entry(function() {
			return lab.@jks.tools2d.parallax.browsertest.TransfertLab::progress()();
		});
		$wnd.transfertLabReady = true;
	}-*/;

	@Override
	public void render()
	{
		if (!clip)
		{
			applyControls();
			step(Math.min(Gdx.graphics.getDeltaTime(), 1 / 15f));
		}
		int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
		Gdx.gl.glViewport(0, 0, width, height);
		Gdx.gl.glClearColor(0, 0, 0, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
		for (int i = 0; i < hearts.length; i++)
		{
			// A, B, C on top; D, E below; a 4 px gap. round1's pages sit at the bottom of the world: no frame drop.
			int x = (i % COLUMNS) * (FRAME_WIDTH + 4), top = i < COLUMNS ? height : height - PANEL_HEIGHT - 4;
			Gdx.gl.glScissor(x, top - PANEL_HEIGHT, FRAME_WIDTH, PANEL_HEIGHT);
			Gdx.gl.glViewport(x, top - PANEL_HEIGHT, FRAME_WIDTH, FRAME_HEIGHT);
			// The reader may end the transfert a frame before the lab's clock does: then the new page is all there is.
			if (i != 2 || !hearts[i].parallaxReader.isInTransfer())
				hearts[i].render();
			else
				renderFogCreep(hearts[i], x, top - PANEL_HEIGHT);
		}
		Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
		Gdx.gl.glViewport(0, 0, width, height);
	}

	/** The library's depth stagger at the slider's value: A draws its transferts in it, C and D take its windows. */
	private TransfertStyle style()
	{return TransfertStyle.depthStagger(LabControls.read(stagger));}

	/** D: the library's dissolve at the patch, softness and stagger sliders' values. */
	private TransfertStyle dissolve()
	{return TransfertStyle.dissolve(LabControls.read(patch), LabControls.read(soft), LabControls.read(stagger));}

	/** E: the library's transfert through E's colour, at the stagger slider's value. */
	private TransfertStyle throughColor()
	{
		float[] c = THROUGH[LabControls.read(through)];
		return TransfertStyle.throughColor(new Color(c[0], c[1], c[2], 1), LabControls.read(stagger));
	}

	/** How far along layer slot {@code slot} of {@code total} (0 at the back) is when the whole is at {@code ramp}. */
	private float rampOf(float ramp, int slot, int total)
	{return style().slotRamp(ramp, slot, total);}

	/**
	 * C: the first half, the old page's layers hazed toward the mist's white in patches, the far ones first, over a
	 * white veil that hides the gradients; the second half, the new page's, clearing near ones first.
	 */
	private void renderFogCreep(Parallax_Heart heart, int panelX, int panelY)
	{
		ParallaxPageReader reader = heart.parallaxReader;
		float t = progress();
		boolean first = t < 0.5f;
		float ramp = first ? 2 * t : 2 - 2 * t;
		ArrayList<ParallaxLayer> drawnFrom = first ? reader.layers : reader.transferLayers;
		begin(heart, panelX, panelY);
		// The veil over the gradients: the mist's white, kept where the noise is under the ramp.
		mask(INCOMING, ramp);
		batch.setColor(GdxLayerEffects.HAZE_R, GdxLayerEffects.HAZE_G, GdxLayerEffects.HAZE_B, 1);
		OrthographicCamera camera = heart.worldCamera;
		float w = camera.viewportWidth * camera.zoom, h = camera.viewportHeight * camera.zoom;
		batch.draw(white, camera.position.x - w / 2, camera.position.y - h / 2, w, h);
		batch.setColor(Color.WHITE);
		ArrayList<ParallaxLayer> layers = reader.layers, transfer = reader.transferLayers;
		for (int j = 0, n = drawnFrom.size(); j < n; j++)
		{
			mask(HAZE, rampOf(ramp, j, n));
			drawOne(reader, camera, drawnFrom.get(j));
		}
		reader.layers = layers;
		reader.transferLayers = transfer;
		end();
	}

	private void begin(Parallax_Heart heart, int panelX, int panelY)
	{
		heart.worldCamera.update();
		heart.drawBackGround();
		batch.setProjectionMatrix(heart.worldCamera.combined);
		batch.enableBlending();
		batch.begin();
		ShaderProgram program = masked();
		batch.setShader(program);
		float px = FRAME_WIDTH / LabControls.read(patch);
		program.setUniformf("u_noise", panelX, panelY, px);
		program.setUniformf("u_drift", clock * 0.15f);
		program.setUniformf("u_haze", 0);
	}

	private void end()
	{
		batch.setShader(null);
		batch.end();
	}

	/** The lab's shader set to {@code mode} at {@code ramp}: flushed, so what was drawn before keeps its own. */
	private void mask(int mode, float ramp)
	{
		batch.flush();
		masked.setUniformf("u_mask", ramp, mode, LabControls.read(soft));
	}

	/** The reader, handed {@code layer} alone, draws it: at full opacity, in the page's tint. */
	private void drawOne(ParallaxPageReader reader, OrthographicCamera camera, ParallaxLayer layer)
	{
		one.clear();
		one.add(layer);
		reader.layers = one;
		reader.transferLayers = none;
		reader.draw(camera, batch);
	}

	/**
	 * GdxLayerEffects' PLAIN with a mask: m, 0 to 1, rises where FOG's noise (r216's sum of sines, in screen pixels from
	 * the panel's corner, u_noise.z px a patch, drifting; y stretched 2.5x, so its patches are about round, not FOG's
	 * 3-4x taller than wide) is under u_mask.x, with u_mask.z of soft edge. OUTGOING keeps
	 * the layer where m is 0, INCOMING where it is 1, HAZE mixes it toward the mist's white by m.
	 */
	private ShaderProgram masked()
	{
		if (masked != null)
			return masked;
		String plain = GdxLayerEffects.fragment(null);
		int main = plain.indexOf("void main()");
		if (main < 0)
			throw new IllegalStateException("GdxLayerEffects' PLAIN no longer has a main() this lab can replace");
		String source = plain.substring(0, main)
				+ "uniform vec3 u_mask;\n"
				+ "uniform vec3 u_noise;\n"
				+ "uniform float u_drift;\n"
				+ "float noise(vec2 p)\n"
				+ "{\n"
				+ "	return (sin(TAU * 0.875 * p.x + 2.0 * sin(0.5 * TAU * p.y))\n"
				+ "		+ sin(TAU * (2.125 * p.x - 0.5 * p.y) + 1.3)\n"
				+ "		+ sin(TAU * (2.875 * p.x + 0.8 * p.y) + 2.9)) / 6.0 + 0.5;\n"
				+ "}\n"
				+ "void main()\n"
				+ "{\n"
				+ "	vec4 color = v_color * texture2D(u_texture, v_texCoords);\n"
				+ "	float n = noise((gl_FragCoord.xy - u_noise.xy) / u_noise.z * vec2(1.0, 2.5) + vec2(u_drift, 0.0));\n"
				+ "	float e = u_mask.z;\n"
				+ "	float m = smoothstep(n - e, n + e, u_mask.x * (1.0 + 2.0 * e) - e);\n"
				+ "	if (u_mask.y < 0.5)\n"
				+ "		color.a *= 1.0 - m;\n"
				+ "	else if (u_mask.y < 1.5)\n"
				+ "		color.a *= m;\n"
				+ "	else\n"
				+ "		color.rgb = mix(color.rgb, vec3(0.93, 0.95, 0.97), m);\n"
				+ "	gl_FragColor = hazed(color);\n"
				+ "}\n";
		masked = new ShaderProgram(GdxLayerEffects.vertex(), source);
		if (!masked.isCompiled())
			throw new IllegalStateException("the transfert lab's shader does not compile: " + masked.getLog());
		return masked;
	}
}
