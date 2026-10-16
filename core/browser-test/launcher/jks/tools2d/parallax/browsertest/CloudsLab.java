package jks.tools2d.parallax.browsertest;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
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
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.pages.Enum_LayerKind;
import jks.tools2d.parallax.pages.Enum_ShaderEffect;
import jks.tools2d.parallax.pages.Parallax_Model;
import jks.tools2d.parallax.pages.Utils_Page_Json;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * The clouds lab (r451, Simon on r419: "Also move cloud to it's own lab"): r375's BILLOW, out of the animation lab, as
 * lab GLSL (GLSL ES 1.0, sines only) drawn by the real reader in WebGL beside the clouds still. Round1's calm page, its
 * clouds on a layer of their own (r409: a generated page will have them so): A under BILLOW and B still in the page, C
 * and D the same alone (the layers in front left out). BILLOW is written in world units over {@code along()}, as WAVE
 * and FOG are, so it runs on across the tiles; its amplitude, wavelength and speed are the layer's shader numbers, as a
 * shipped effect's would be, under FOG's periods (8 wavelengths across, 10 up), where every term is a whole number of
 * turns. Opened with clouds-lab.html: tools/clouds-lab.sh.
 * <p>
 * {@code &clip=1}: nothing moves by itself, {@code window.cloudsLabAct(seconds)} steps every heart at 1/60 s.
 */
public class CloudsLab extends ApplicationAdapter implements LabKit.Clip
{
	static final String PAGE = "fog-lab/s02.jplax", CLOUDS = "Clouds";
	static final float WORLD_WIDTH = FogLab.WORLD_WIDTH, WORLD_HEIGHT = FogLab.WORLD_HEIGHT;
	/** FogLab's panels, two by two. */
	static final int PANEL_WIDTH = FogLab.FRAME_WIDTH, PANEL_HEIGHT = FogLab.PANEL_HEIGHT;

	/** Each heart, in the order of the panels' titles: BILLOW or still, in the page or alone. */
	static final boolean[] HEART_BILLOW = { true, false, true, false };
	static final boolean[] HEART_ALONE = { false, false, true, true };
	static final String[] TITLES = { "A: BILLOW, in the page", "B: still, in the page", "C: BILLOW, the clouds alone", "D: still, the clouds alone" };
	/** The views the choice picks: all four, or two side by side, as tall as the stage. Each: its hearts. */
	static final String[] VIEWS = { "in the page and alone", "in the page, side by side", "alone, side by side" };
	static final int[][] VIEW_HEARTS = { { 0, 1, 2, 3 }, { 0, 1 }, { 2, 3 } };
	/** Where a frame is held, as a fraction of its height from its bottom, at the panel's middle: calm's clouds. */
	static final float FOCUS = 0.82f;

	private final Parallax_Heart[] hearts = new Parallax_Heart[HEART_BILLOW.length];
	private final ParallaxLayer[] clouds = new ParallaxLayer[hearts.length];
	/** Each clouds layer's own drift (its at rest speed), which the drift slider scales. */
	private final float[] drifts = new float[hearts.length];
	private SelectElement view;
	private InputElement zoom, scroll, drift, amplitude, wavelength, speed;
	private Element readout;
	private final Element[] labels = new Element[4];
	private boolean clip;
	private SpriteBatch batch;

	@Override
	public void create()
	{
		clip = "1".equals(com.google.gwt.user.client.Window.Location.getParameter("clip"));
		batch = new SpriteBatch();
		for (int i = 0; i < hearts.length; i++)
		{
			WholePage_Model page = Utils_Page_Json.loadPage(PAGE);
			int index = -1;
			for (int k = page.pageModel.pageList.size() - 1; k >= 0; k--)
				if (CLOUDS.equals(page.pageModel.pageList.get(k).regionName))
					index = k;
			if (index < 0)
				throw new IllegalStateException(PAGE + " has no layer " + CLOUDS);
			Parallax_Model model = page.pageModel.pageList.get(index);
			if (HEART_ALONE[i])
				while (page.pageModel.pageList.size() > index + 1)
					page.pageModel.pageList.remove(index + 1);
			if (HEART_BILLOW[i])
			{
				// What a page would store: a SHADER layer, under FOG's periods until BILLOW ships (the lab draws its own).
				model.kind = Enum_LayerKind.SHADER;
				model.shaderEffect = Enum_ShaderEffect.FOG;
			}
			OrthographicCamera camera = new OrthographicCamera();
			camera.setToOrtho(false, WORLD_WIDTH, WORLD_HEIGHT);
			hearts[i] = new Parallax_Heart(camera, batch, WORLD_WIDTH, WORLD_HEIGHT);
			hearts[i].setPage(page);
			clouds[i] = hearts[i].parallaxReader.layers.get(index);
			drifts[i] = clouds[i].getSpeedAtRest();
			hearts[i].parallaxReader.setLayerEffects(new BillowEffects(HEART_BILLOW[i] ? clouds[i] : null));
		}
		buildControls();
		if (clip)
			LabKit.exportClip("cloudsLab", this);
	}

	private void buildControls()
	{
		Document document = Document.get();
		Element labelGrid = document.getElementById("clouds-labels");
		// Each chip's title in a span of its own: the frame rewrites it, the view's choice before it in the first stays.
		for (int i = 0; i < labels.length; i++)
		{
			Element chip = document.createDivElement();
			chip.setClassName("panel-label");
			labels[i] = document.createSpanElement();
			chip.appendChild(labels[i]);
			labelGrid.appendChild(chip);
		}
		view = LabControls.stageChoice(document, "clouds-labels", "view", "view (all four, or the two in the page or the two alone, side by side)", VIEWS, 0);
		Element controls = document.getElementById("clouds-controls");
		zoom = LabControls.slider(document, controls, "zoom", "zoom (x the page's frame)", 1, 4, 0.25f, 1.5f);
		scroll = LabControls.slider(document, controls, "cameraScroll", "camera scroll (world units/s)", 0, 120, 5, 20);
		drift = LabControls.slider(document, controls, "cloudDrift", "clouds' drift (x their page's at rest speed: 0 to judge the churn alone)", 0, 1, 0.05f, 1);
		Element billow = LabControls.section(document, controls, "BILLOW (the clouds churn in place: a slow warp of their pixels, panels A and C)");
		amplitude = LabControls.slider(document, billow, "billowAmplitude", "amplitude (world units)", 0, 1.5f, 0.02f, 0.3f);
		wavelength = LabControls.slider(document, billow, "billowWavelength", "wavelength (world units)", 1, 20, 0.25f, 6);
		speed = LabControls.slider(document, billow, "billowSpeed", "speed (world units/s)", 0, 2, 0.05f, 0.4f);
		readout = LabControls.readout(document, controls, "clouds-readout");
	}

	/** Puts the sliders into every heart. */
	private void applyControls()
	{
		int v = LabControls.read(view);
		LabControls.read(zoom);
		float sc = LabControls.read(scroll), dr = LabControls.read(drift);
		float ba = LabControls.read(amplitude), bw = LabControls.read(wavelength), bs = LabControls.read(speed);
		for (int i = 0; i < hearts.length; i++)
		{
			ParallaxLayer layer = clouds[i];
			if (HEART_BILLOW[i])
			{
				layer.setShaderAmplitude(ba);
				layer.setShaderWavelength(bw);
				layer.setShaderSpeed(bs);
			}
			layer.setSpeedAtRest(drifts[i] * dr);
			hearts[i].screenSpeedConstantX = sc;
		}
		int[] shown = VIEW_HEARTS[v];
		for (int k = 0; k < labels.length; k++)
		{
			boolean titled = k < shown.length;
			labels[k].setInnerText(titled ? TITLES[shown[k]] : "");
			// A chip over no panel is hidden, not a dark dash; it keeps its cell in the grid.
			labels[k].getParentElement().getStyle().setProperty("visibility", titled ? "visible" : "hidden");
		}
		ParallaxLayer cloud = clouds[0];
		float px = cloud.getDrawnRegion().getRegionWidth() / (cloud.getWidth() * cloud.getPackedWidthRatio());
		readout.setInnerText("calm's clouds " + round(cloud.getWidth()) + " x " + round(cloud.getHeight()) + " world units, their image "
				+ round(px) + " px a unit: BILLOW " + round(ba * px) + " px, wavelength " + round(bw * px) + " px. The world is "
				+ round(WORLD_WIDTH) + " units wide.");
	}

	private static float round(float value)
	{return Math.round(value * 100) / 100f;}

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
		int height = Gdx.graphics.getBackBufferHeight();
		int[] shown = VIEW_HEARTS[LabControls.read(view)];
		float z = Float.parseFloat(zoom.getValue());
		LabKit.beginPanels();
		// Two side by side are as tall as the stage: the quarters' framing, that much larger, the clouds as close.
		for (int k = 0; k < shown.length; k++)
			if (shown.length == 2)
				panel(shown[k], k * (PANEL_WIDTH + 4), height, PANEL_WIDTH, height, z * height / PANEL_HEIGHT);
			else
				panel(shown[k], (k % 2) * (PANEL_WIDTH + 4), k < 2 ? height : height - PANEL_HEIGHT - 4, PANEL_WIDTH, PANEL_HEIGHT, z);
		LabKit.endPanels();
	}

	/**
	 * Draws heart {@code i} into the panel {@code w} by {@code h} whose top-left is at {@code x}, {@code top}: through a
	 * 16:9 frame {@code z} times the panel's width, its middle across at the panel's, its {@link #FOCUS} at the panel's
	 * middle up, kept over the whole panel.
	 */
	private void panel(int i, int x, int top, int w, int h, float z)
	{
		int frameWidth = Math.round(w * z), frameHeight = Math.round(frameWidth * WORLD_HEIGHT / WORLD_WIDTH);
		int bottom = Math.round(top - h / 2f - FOCUS * frameHeight);
		bottom = Math.max(top - frameHeight, Math.min(top - h, bottom));
		if (LabKit.frame(x, top - h, w, h, x - (frameWidth - w) / 2, bottom, frameWidth, frameHeight))
			hearts[i].render();
	}

	@Override
	public void dispose()
	{batch.dispose();}

	/** A heart's effects: its clouds through {@link #FRAGMENT} when they billow, every other layer through GdxLayerEffects. */
	static final class BillowEffects extends LabKit.Effects
	{
		private static ShaderProgram billow;

		/** The layer that billows; null when none does. */
		private final ParallaxLayer target;
		private final GdxLayerEffects shipped = new GdxLayerEffects();
		private boolean byShipped;
		private ShaderProgram previous;

		BillowEffects(ParallaxLayer target)
		{this.target = target;}

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase)
		{return begin(batch, layer, phase, 0, Color.WHITE);}

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase, float fogAmount, Color fogColor)
		{
			byShipped = layer != target;
			if (byShipped)
				return shipped.begin(batch, layer, phase, fogAmount, fogColor);
			ShaderProgram program = program();
			previous = batch.getShader();
			batch.setShader(program);
			if (!batch.isDrawing())
				program.bind();
			LabKit.setEffect(program, numbers(layer, phase), fogAmount, fogColor.r, fogColor.g, fogColor.b);
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

		static ShaderProgram program()
		{
			if (billow == null)
			{
				billow = new ShaderProgram(GdxLayerEffects.vertex(), FRAGMENT);
				if (!billow.isCompiled())
					throw new IllegalStateException("the lab's BILLOW does not compile: " + billow.getLog());
			}
			return billow;
		}
	}

	/**
	 * BILLOW (r375's billow), as GdxLayerEffects writes its own effects (its head: the region, the layer's size,
	 * u_effect = amplitude, wavelength, phase in world units, the haze, along()), the dissolve and the grade left out:
	 * the clouds churn in place, their pixels pushed by a slow warp of wavelength L. {@code at(l)}: the image at local
	 * position l, in world units from the tile's top-left, wrapping across (the layer tiles), held at its top and bottom
	 * rows. On a layer of its own every pixel is cloud: r375's colour mask, made for clouds painted into a back layer, is
	 * left out (r409: clouds always get their own layer).
	 */
	static final String FRAGMENT = "#ifdef GL_ES\n"
			+ "#ifdef GL_FRAGMENT_PRECISION_HIGH\n"
			+ "precision highp float;\n"
			+ "#else\n"
			+ "precision mediump float;\n"
			+ "#endif\n"
			+ "#endif\n"
			+ "varying vec4 v_color;\n"
			+ "varying vec2 v_texCoords;\n"
			+ "varying vec2 v_view;\n"
			+ "uniform sampler2D u_texture;\n"
			+ "uniform vec4 u_region;\n"
			+ "uniform vec2 u_size;\n"
			+ "uniform vec3 u_effect;\n"
			+ "uniform float u_haze;\n"
			+ "uniform vec3 u_fog;\n"
			+ "uniform vec3 u_across;\n"
			+ "uniform vec3 u_up;\n"
			+ "const float TAU = 6.2831853;\n"
			+ "vec4 hazed(vec4 color)\n"
			+ "{\n"
			+ "	return vec4(mix(color.rgb, u_fog, u_haze), color.a);\n"
			+ "}\n"
			+ "vec2 local()\n"
			+ "{\n"
			+ "	return vec2((v_texCoords.x - u_region.x) / (u_region.z - u_region.x) * u_size.x,\n"
			+ "		(u_region.w - v_texCoords.y) / (u_region.w - u_region.y) * u_size.y);\n"
			+ "}\n"
			+ "vec2 along()\n"
			+ "{\n"
			+ "	return vec2(u_across.z * (v_view.x * u_across.x - u_across.y), u_up.z * (v_view.y * u_up.x - u_up.y));\n"
			+ "}\n"
			+ "vec4 at(vec2 l)\n"
			+ "{\n"
			+ "	vec2 f = vec2(fract(l.x / u_size.x), clamp(l.y / u_size.y, 0.0, 1.0));\n"
			+ "	return texture2D(u_texture, vec2(mix(u_region.x, u_region.z, f.x), mix(u_region.w, u_region.y, f.y)));\n"
			+ "}\n"
			+ "void main()\n"
			+ "{\n"
			+ "	vec2 a = along();\n"
			+ "	vec2 l = local();\n"
			+ "	float L = u_effect.y;\n"
			+ "	float t = u_effect.z / L;\n"
			+ "	vec2 p = a / L;\n"
			+ "	float dx = sin(TAU * (p.x + 0.7 * sin(TAU * p.y) - t)) + 0.5 * sin(TAU * (2.0 * p.x - 1.3 * p.y + t + 0.3));\n"
			+ "	float dy = sin(TAU * (2.0 * p.x + p.y - t + 0.6)) + 0.5 * sin(TAU * (p.x - 2.0 * p.y - 2.0 * t));\n"
			+ "	l += u_effect.x / 1.5 * vec2(dx, -dy);\n"
			+ "	gl_FragColor = hazed(v_color * at(l));\n"
			+ "}\n";
}
