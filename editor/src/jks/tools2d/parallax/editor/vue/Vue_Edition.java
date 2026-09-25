package jks.tools2d.parallax.editor.vue;

import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.allImage;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.centerControl;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.imageRef;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.isPause;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.optionsControl;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.parr_Pos_X;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.parr_Pos_Y;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.parr_Size_X;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.parr_Size_Y;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.projectDatas;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.showParallaxFullScreen;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.tabControl;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.glutils.HdpiUtils;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;

import jks.tools2d.parallax.editor.gvars.GVars_UI;
import jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition;
import jks.tools2d.parallax.editor.inputs.EditorInputProcessus;
import jks.tools2d.parallax.editor.inputs.GVars_Inputs;
import jks.tools2d.parallax.editor.vue.edition.VE_Center_ParallaxShow;
import jks.tools2d.parallax.editor.vue.edition.VE_Options;
import jks.tools2d.parallax.editor.vue.edition.VE_Tab_AControl;
import jks.tools2d.parallax.editor.vue.edition.data.ParallaxDefaultValues;
import jks.tools2d.parallax.editor.vue.edition.data.Position_Infos;
import jks.tools2d.parallax.editor.vue.edition.utils.Utils_LoadingImages;
import jks.tools2d.parallax.editor.vue.edition.utils.Utils_Saving;
import jks.tools2d.parallax.editor.vue.model.AVue_Model;
import jks.tools2d.parallax.heart.Parallax_Heart;

/** Edition of one parallax: tabs on the left, live preview in the middle, save options on top. */
public class Vue_Edition extends AVue_Model
{
	public static Parallax_Heart parallax_Heart;
	public static boolean isVSynch = true;

	private static final float circlePosition = 10;
	private static final float circleSize = 12;
	private static final float hideInterfaceQuota = 4;

	/** What to open: a TextureAtlas, a WholePage_Model (.plax/.jplax), a Project_Data (.plaxpj), or null for a new project. */
	private final Object preloadingValue;
	private Parallax_Heart heart;
	private ShapeRenderer shapeRender;

	private int builtWidth, builtHeight;
	/** Seconds until the widgets are rebuilt for the new window size, negative when none is pending. */
	private float rebuildCountdown = -1;

	private final ByteBuffer pixel = BufferUtils.newByteBuffer(4);
	private final Color pickedColor = new Color();
	private final Color pickedOutline = new Color();

	public Vue_Edition(Object preloadingValue)
	{this.preloadingValue = preloadingValue;}

	public Vue_Edition()
	{this(null);}

	@Override
	public void init()
	{
		GVars_Vue_Edition.clear();
		GVars_Inputs.reset();

		// Created here rather than in the constructor: the previous view is destroyed in between.
		heart = parallax_Heart = new Parallax_Heart();
		heart.relativePath = GVars_Vue_Edition.relativePath;
		GVars_Vue_Edition.buildSizes();
		shapeRender = new ShapeRenderer();

		VE_Center_ParallaxShow.loadPreview(preloadingValue);

		if (projectDatas.defaults == null)
			GVars_Vue_Edition.setDefaults(new ParallaxDefaultValues());
		addAtlasImages();

		buildInterface();
		Gdx.input.setInputProcessor(new InputMultiplexer(GVars_UI.mainUi, new EditorInputProcessus(), buildClickProcessor()));
	}

	/** The widgets read the sizes of the window when created, so they are rebuilt when it changes. */
	private void buildInterface()
	{
		builtWidth = Gdx.graphics.getWidth();
		builtHeight = Gdx.graphics.getHeight();

		centerControl = new VE_Center_ParallaxShow();
		tabControl = new VE_Tab_AControl();
		optionsControl = new VE_Options();

		GVars_UI.mainUi.addActor(optionsControl);
		GVars_UI.mainUi.addActor(tabControl);
		GVars_UI.mainUi.addActor(centerControl);
	}

	/** True between a resize and the rebuild of the widgets it causes: they still have the previous size. */
	public boolean isRebuildPending()
	{return rebuildCountdown >= 0;}

	private void rebuildInterface()
	{
		String path = VE_Options.parallaxPath.getText(), name = VE_Options.parallaxName.getText();
		boolean exportLibGDX = VE_Options.formatLibGDX.isChecked(), exportJson = VE_Options.formatJson.isChecked();

		tabControl.dispose();
		GVars_UI.mainUi.clear();
		GVars_Inputs.selectedItem = null;
		GVars_Vue_Edition.colorPicked = null;
		GVars_Vue_Edition.showParallaxFullScreen = false;
		GVars_UI.resize();
		buildInterface();

		VE_Options.parallaxPath.setText(path);
		VE_Options.parallaxName.setText(name);
		VE_Options.formatLibGDX.setChecked(exportLibGDX);
		VE_Options.formatJson.setChecked(exportJson);
	}

	/** Lists the atlas regions after the loose images, keeping the save info of those already used by layers. */
	private void addAtlasImages()
	{
		TextureAtlas atlas = GVars_Vue_Edition.getAtlas();
		if (atlas == null)
			return;

		Map<String, Integer> positions = new HashMap<>();
		for (AtlasRegion region : atlas.getRegions())
		{
			// Position among the regions sharing this name, as returned by TextureAtlas#findRegions.
			int position = positions.merge(region.name, 1, Integer::sum) - 1;
			allImage.add(region);
			imageRef.putIfAbsent(region, new Position_Infos(region, position));
		}
	}

	@Override
	public void destroy()
	{
		if (tabControl != null)
			tabControl.dispose();
		GVars_UI.reset();
		heart.dispose();
		shapeRender.dispose();
		if (preloadingValue instanceof TextureAtlas)
			((TextureAtlas) preloadingValue).dispose();
		GVars_Vue_Edition.clear();
		GVars_Inputs.reset();
	}

	/**
	 * Right click cancels the eyedropper, left click picks the color under the mouse. That color is read while
	 * rendering: during input handling the back buffer has already been swapped and its content is undefined.
	 */
	private InputAdapter buildClickProcessor()
	{
		return new InputAdapter()
		{
			@Override
			public boolean touchDown(int screenX, int screenY, int pointer, int button)
			{
				if (button == Buttons.RIGHT)
					GVars_Vue_Edition.colorPicked = null;

				if (GVars_Vue_Edition.colorPicked != null)
					GVars_Vue_Edition.colorPicked.setColor(new Color(pickedColor));

				return false;
			}
		};
	}

	/** Color of the back buffer under a screen position (y down, logical pixels). */
	private Color readPixel(int screenX, int screenY, Color out)
	{
		float scale = Gdx.graphics.getBackBufferScale();
		int x = (int) (screenX * scale);
		int y = Gdx.graphics.getBackBufferHeight() - 1 - (int) (screenY * scale);

		pixel.clear();
		Gdx.gl.glPixelStorei(GL20.GL_PACK_ALIGNMENT, 1);
		Gdx.gl.glReadPixels(x, y, 1, 1, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixel);
		return out.set((pixel.get(0) & 0xff) / 255f, (pixel.get(1) & 0xff) / 255f, (pixel.get(2) & 0xff) / 255f, 1);
	}

	@Override
	public void update(float delta)
	{
		// Rebuild once the size settles, not for every step of a window drag.
		if (rebuildCountdown >= 0 && (rebuildCountdown -= delta) < 0)
			rebuildInterface();

		GVars_UI.mainUi.act(delta);
		GVars_Inputs.updateInput();

		if (!isPause)
			parallax_Heart.act(delta);

		if (showParallaxFullScreen)
			GVars_Vue_Edition.hideInterfaceTimmer += delta;

		GVars_Vue_Edition.timeForAutoSaveTimmer += delta;
		if (GVars_Vue_Edition.timeForAutoSaveTimmer > GVars_Vue_Edition.timeForAutoSaveAt)
		{
			Utils_Saving.autoSave();
			GVars_Vue_Edition.timeForAutoSaveTimmer = 0;
		}
	}

	@Override
	public void resize(int width, int height)
	{
		GVars_Vue_Edition.buildSizes();
		centerControl.resize();
		parallax_Heart.resize(width, height);
		shapeRender.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
		shapeRender.updateMatrices();

		if (width != builtWidth || height != builtHeight)
			rebuildCountdown = 0.2f;
	}

	@Override
	public void render()
	{
		ScreenUtils.clear(0, 0, 0, 1, true);

		if (showParallaxFullScreen)
		{
			parallax_Heart.render();
			if (GVars_Vue_Edition.hideInterfaceTimmer < hideInterfaceQuota)
				GVars_UI.mainUi.draw();
			return;
		}

		HdpiUtils.glViewport(parr_Pos_X, parr_Pos_Y, parr_Size_X, parr_Size_Y);
		parallax_Heart.render();
		HdpiUtils.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

		if (GVars_Vue_Edition.colorPicked != null)
			drawEyedropper();

		GVars_UI.mainUi.draw();
	}

	/** Preview of the color that a click would pick, next to the mouse. */
	private void drawEyedropper()
	{
		readPixel(Gdx.input.getX(), Gdx.input.getY(), pickedColor);
		pickedOutline.set(1 - pickedColor.r, 1 - pickedColor.g, 1 - pickedColor.b, 1);

		float x = Gdx.input.getX() + circlePosition;
		float y = Gdx.graphics.getHeight() - Gdx.input.getY() + circlePosition;

		shapeRender.begin(ShapeType.Filled);
		shapeRender.setColor(pickedColor);
		shapeRender.circle(x, y, circleSize);
		shapeRender.end();
		shapeRender.begin(ShapeType.Line);
		shapeRender.setColor(pickedOutline);
		shapeRender.circle(x, y, circleSize);
		shapeRender.end();
	}

	@Override
	public void receiveFiles(String[] files)
	{
		Utils_LoadingImages.fileReception(files);
		GVars_Vue_Edition.refreshActiveTab();
	}
}
