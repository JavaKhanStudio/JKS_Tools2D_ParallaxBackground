package jks.tools2d.parallax.editor.vue.edition;

import java.util.TreeSet;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics.DisplayMode;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;

import jks.tools2d.parallax.editor.gvars.GVars_UI;
import jks.tools2d.parallax.editor.vue.Vue_Edition;

/** Window settings: VSync, full screen and window size. */
public class VE_Tab_Meta_ConfigApplication extends Tab
{
	private static final int MIN_WIDTH = 1100;

	private final Table mainTable = new Table();
	private final VisCheckBox vSynchCheckBox;
	private final VisCheckBox fullScreenCheckBox;
	private final SelectBox<String> resolutionBox;

	VE_Tab_Meta_ConfigApplication()
	{
		super(false, false);

		vSynchCheckBox = new VisCheckBox("VSync");
		vSynchCheckBox.setName("application.vsync");
		vSynchCheckBox.addListener(new ChangeListener()
		{
			@Override
			public void changed(ChangeEvent event, Actor actor)
			{
				Vue_Edition.isVSynch = vSynchCheckBox.isChecked();
				Gdx.graphics.setVSync(Vue_Edition.isVSynch);
			}
		});

		resolutionBox = new SelectBox<>(GVars_UI.baseSkin);
		resolutionBox.setName("application.windowSize");
		resolutionBox.setItems(windowSizes());
		resolutionBox.setSelected(Gdx.graphics.getWidth() + "x" + Gdx.graphics.getHeight());

		fullScreenCheckBox = new VisCheckBox("Full screen");
		fullScreenCheckBox.setName("application.fullScreen");
		fullScreenCheckBox.setChecked(Gdx.graphics.isFullscreen());
		resolutionBox.setDisabled(fullScreenCheckBox.isChecked());
		fullScreenCheckBox.addListener(new ChangeListener()
		{
			@Override
			public void changed(ChangeEvent event, Actor actor)
			{resolutionBox.setDisabled(fullScreenCheckBox.isChecked());}
		});

		VisTextButton apply = new VisTextButton("Apply");
		apply.setName("application.apply");
		apply.getLabel().setStyle(GVars_UI.labelStyle_OptionsTitle);
		apply.addListener(new ChangeListener()
		{
			@Override
			public void changed(ChangeEvent event, Actor actor)
			{applyWindowMode();}
		});

		mainTable.add(new VisLabel("Window size")).align(Align.left).padRight(10);
		mainTable.add(resolutionBox).align(Align.left).row();
		mainTable.add(fullScreenCheckBox).colspan(2).align(Align.left).row();
		mainTable.add(vSynchCheckBox).colspan(2).align(Align.left).row();
		mainTable.add(apply).colspan(2).padTop(10);
	}

	/** 16:9-ish sizes offered by the current monitor, plus the current window size. */
	private static String[] windowSizes()
	{
		TreeSet<DisplayMode> modes = new TreeSet<>((a, b) -> a.width != b.width ? a.width - b.width : a.height - b.height);
		for (DisplayMode mode : Gdx.graphics.getDisplayModes())
		{
			float ratio = (float) mode.width / mode.height;
			if (mode.width >= MIN_WIDTH && ratio > 1.5f && ratio < 1.8f)
				modes.add(mode);
		}

		TreeSet<String> sizes = new TreeSet<>((a, b) -> Integer.compare(Integer.parseInt(a.split("x")[0]), Integer.parseInt(b.split("x")[0])));
		sizes.add(Gdx.graphics.getWidth() + "x" + Gdx.graphics.getHeight());
		for (DisplayMode mode : modes)
			sizes.add(mode.width + "x" + mode.height);
		return sizes.toArray(new String[0]);
	}

	private void applyWindowMode()
	{
		Gdx.graphics.setVSync(vSynchCheckBox.isChecked());

		if (fullScreenCheckBox.isChecked())
		{
			Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
		}
		else
		{
			String[] size = resolutionBox.getSelected().split("x");
			// The window is centered by the backend; Vue_Edition rebuilds its layout on the resize event.
			Gdx.graphics.setWindowedMode(Integer.parseInt(size[0]), Integer.parseInt(size[1]));
		}
	}

	@Override
	public String getTabTitle()
	{return "Application";}

	@Override
	public Table getContentTable()
	{
		vSynchCheckBox.setProgrammaticChangeEvents(false);
		vSynchCheckBox.setChecked(Vue_Edition.isVSynch);
		vSynchCheckBox.setProgrammaticChangeEvents(true);
		return mainTable;
	}
}
