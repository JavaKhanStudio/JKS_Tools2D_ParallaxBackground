package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.gvars.GVars_UI.baseSkin;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.isPause;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.optionsControl;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.parr_Pos_X;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.parr_Pos_Y;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.parr_Size_X;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.parr_Size_Y;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.size_Bloc_Parallax;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.size_Bloc_Selection_Parallax_Width;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.size_Height_Bloc_Parallax_Controle;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.tabControl;
import static jks.tools2d.parallax.editor.vue.Vue_Edition.parallax_Heart;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox.CheckBoxStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.util.dialog.Dialogs;
import com.kotcrab.vis.ui.widget.VisTextButton;

import jks.tools2d.libgdxutils.JksCheckBox;
import jks.tools2d.libgdxutils.Utils_Interface;
import jks.tools2d.parallax.editor.gvars.EditorPaths;
import jks.tools2d.parallax.editor.gvars.FVars_Extensions;
import jks.tools2d.parallax.editor.gvars.GVars_UI;
import jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition;
import jks.tools2d.parallax.editor.vue.edition.data.Outside_Source;
import jks.tools2d.parallax.editor.vue.edition.data.Project_Data;
import jks.tools2d.parallax.editor.vue.edition.utils.Utils_LoadingImages;
import jks.tools2d.parallax.editor.vue.edition.utils.Utils_Texture;
import jks.tools2d.parallax.pages.WholePage_Model;

/** Preview area: play/pause, full screen, and the scrolling speed controls under the parallax. */
public class VE_Center_ParallaxShow extends Table
{
	float decalX = 2;
	float buttonSize;

	Slider parallaxSpeedXSlider;
	Slider parallaxSpeedYSlider;

	/** Loads what was selected on the start screen into the preview. */
	public static void loadPreview(Object ref)
	{
		try
		{
			if (ref instanceof TextureAtlas)
			{
				WholePage_Model page = new WholePage_Model();
				// A new page: stripped atlas regions keep their original size. Opened pages keep what they were saved with.
				page.useOriginalSize = true;
				page.pageModel.atlasName = GVars_Vue_Edition.projectInfos.projectName + "." + FVars_Extensions.ATLAS;
				page.forceLoad((TextureAtlas) ref);
				GVars_Vue_Edition.setPage(page);
			}
			else if (ref instanceof WholePage_Model)
			{
				EditorPaths.locateAtlas((WholePage_Model) ref);
				GVars_Vue_Edition.setPage((WholePage_Model) ref);
			}
			else if (ref instanceof Project_Data)
			{
				loadOutsideImages();
				EditorPaths.locateAtlas(((Project_Data) ref).saving);
				GVars_Vue_Edition.setPage(((Project_Data) ref).saving);
			}
			else
			{
				setEmptyPage();
			}
		}
		catch (RuntimeException e)
		{
			Gdx.app.error("VE_Center_ParallaxShow", "Cannot load the parallax", e);
			Dialogs.showErrorDialog(GVars_UI.mainUi, "Could not load the parallax.\nIs its atlas in the same folder?", e);
			GVars_Vue_Edition.loadedIncompletely = true;
			setEmptyPage();
		}
	}

	private static void setEmptyPage()
	{
		WholePage_Model page = new WholePage_Model();
		page.useOriginalSize = true;
		page.pageModel.atlasName = null;
		GVars_Vue_Edition.setPage(page);
	}

	/** Loads the loose PNGs of a project, so its layers can find them. */
	private static void loadOutsideImages()
	{
		if (GVars_Vue_Edition.projectDatas.outsideInfos == null)
			return;

		StringBuilder errors = new StringBuilder();
		for (Outside_Source source : GVars_Vue_Edition.projectDatas.outsideInfos)
		{
			TextureRegion region = Utils_Texture.getTextureRegionFromPath(EditorPaths.resolveProjectFile(source.url).toString());
			if (region == null)
				errors.append("Could not load ").append(source.url).append("\n");
			else
				Utils_LoadingImages.registerOutsideImage(source.url, region);
		}

		if (errors.length() > 0)
		{
			// Their layers are dropped when the page loads.
			GVars_Vue_Edition.loadedIncompletely = true;
			Dialogs.showErrorDialog(GVars_UI.mainUi, "Loading not possible", errors.toString());
		}
	}

	public VE_Center_ParallaxShow()
	{
		resize();
		buildOptions();
	}

	public void resize()
	{
		float sizeRatio = (float) Gdx.graphics.getHeight() / Gdx.graphics.getWidth();
		decalX = Gdx.graphics.getWidth() / 30f;
		parr_Size_X = (int) (Gdx.graphics.getWidth() - size_Bloc_Selection_Parallax_Width - decalX * 2);
		parr_Size_Y = (int) (parr_Size_X * sizeRatio);
		parr_Pos_X = (int) (size_Bloc_Selection_Parallax_Width + decalX);
		parr_Pos_Y = size_Height_Bloc_Parallax_Controle;

		buttonSize = size_Height_Bloc_Parallax_Controle / 1.5f;
	}

	public void buildOptions()
	{
		setBounds(size_Bloc_Selection_Parallax_Width, 1, size_Bloc_Parallax, Gdx.graphics.getHeight() - parr_Pos_Y / 2f - parr_Size_Y);

		CheckBoxStyle playStyle = new CheckBoxStyle();
		playStyle.checkboxOff = Utils_Interface.buildDrawingRegionTexture("editor/interfaces/button_play.png");
		playStyle.checkboxOn = Utils_Interface.buildDrawingRegionTexture("editor/interfaces/button_pause.png");
		playStyle.font = baseSkin.getFont("default-font");

		CheckBoxStyle fullScreenStyle = new CheckBoxStyle();
		fullScreenStyle.checkboxOff = Utils_Interface.buildDrawingRegionTexture("editor/interfaces/expand.png");
		fullScreenStyle.checkboxOn = Utils_Interface.buildDrawingRegionTexture("editor/interfaces/contract.png");
		fullScreenStyle.font = baseSkin.getFont("default-font");

		JksCheckBox startStop = new JksCheckBox("", playStyle, false);
		startStop.setName("preview.play");
		startStop.setChecked(!isPause);
		startStop.addListener(new ChangeListener()
		{
			@Override
			public void changed(ChangeEvent event, Actor actor)
			{isPause = !startStop.isChecked();}
		});
		startStop.setSize(buttonSize, buttonSize);
		startStop.setPosition(parr_Size_X / 2f, size_Height_Bloc_Parallax_Controle / 2f - buttonSize / 2);

		JksCheckBox fullScreen = new JksCheckBox("", fullScreenStyle, false);
		fullScreen.setName("preview.fullScreen");
		fullScreen.addListener(new ChangeListener()
		{
			@Override
			public void changed(ChangeEvent event, Actor actor)
			{
				GVars_Vue_Edition.showParallaxFullScreen = fullScreen.isChecked();
				tabControl.setVisible(!fullScreen.isChecked());
				optionsControl.setVisible(!fullScreen.isChecked());
			}
		});
		fullScreen.setSize(buttonSize, buttonSize);
		fullScreen.setPosition(getWidth() - fullScreen.getWidth(), getHeight() - fullScreen.getHeight() / 2);

		parallaxSpeedXSlider = new Slider(-15, 15, 0.05f, false, baseSkin);
		parallaxSpeedXSlider.setName("preview.speedX");
		parallaxSpeedXSlider.setValue(parallax_Heart.screenSpeedConstantX / 100);
		parallaxSpeedXSlider.addListener(new ChangeListener()
		{
			@Override
			public void changed(ChangeEvent event, Actor actor)
			{parallax_Heart.screenSpeedConstantX = parallaxSpeedXSlider.getValue() * 100;}
		});

		parallaxSpeedYSlider = new Slider(-20, 20, 0.2f, false, baseSkin);
		parallaxSpeedYSlider.setName("preview.speedY");
		parallaxSpeedYSlider.setValue(parallax_Heart.screenSpeedConstantY / 100);
		parallaxSpeedYSlider.addListener(new ChangeListener()
		{
			@Override
			public void changed(ChangeEvent event, Actor actor)
			{parallax_Heart.screenSpeedConstantY = parallaxSpeedYSlider.getValue() * 100;}
		});

		VisTextButton resetSpeedX = new VisTextButton("X = 0");
		resetSpeedX.setName("preview.speedX.reset");
		resetSpeedX.addListener(new ChangeListener()
		{
			@Override
			public void changed(ChangeEvent event, Actor actor)
			{parallaxSpeedXSlider.setValue(0);}
		});

		VisTextButton resetSpeedY = new VisTextButton("Y = 0");
		resetSpeedY.setName("preview.speedY.reset");
		resetSpeedY.addListener(new ChangeListener()
		{
			@Override
			public void changed(ChangeEvent event, Actor actor)
			{parallaxSpeedYSlider.setValue(0);}
		});

		VisTextButton resetPosition = new VisTextButton("Reset position");
		resetPosition.setName("preview.resetPosition");
		resetPosition.addListener(new ChangeListener()
		{
			@Override
			public void changed(ChangeEvent event, Actor actor)
			{parallax_Heart.parallaxReader.resetPositions();}
		});

		Table speedSlider = new Table();
		speedSlider.setSize(getWidth() / 2 - buttonSize, buttonSize);
		speedSlider.setPosition(buttonSize / 3, getHeight() / 2 - speedSlider.getHeight() / 2);
		speedSlider.add(new Label("Speed X", baseSkin));
		speedSlider.add(parallaxSpeedXSlider);
		speedSlider.add(resetSpeedX);
		speedSlider.row();
		speedSlider.add(new Label("Speed Y", baseSkin));
		speedSlider.add(parallaxSpeedYSlider);
		speedSlider.add(resetSpeedY);
		speedSlider.row();
		speedSlider.add(resetPosition).colspan(3);

		addActor(startStop);
		addActor(speedSlider);
		addActor(fullScreen);
	}
}
