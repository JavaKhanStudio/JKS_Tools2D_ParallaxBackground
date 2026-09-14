package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.vue.Vue_Edition.parallax_Heart;
import static jks.tools2d.parallax.editor.vue.edition.VE_Options.parallaxName;
import static jks.tools2d.parallax.editor.vue.edition.VE_Options.parallaxPath;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.util.dialog.Dialogs;
import com.kotcrab.vis.ui.util.dialog.Dialogs.OptionDialogType;
import com.kotcrab.vis.ui.util.dialog.OptionDialogAdapter;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;

import jks.tools2d.parallax.editor.gvars.GVars_Heart_Editor;
import jks.tools2d.parallax.editor.gvars.GVars_UI;
import jks.tools2d.parallax.editor.vue.Vue_Selection;
import jks.tools2d.parallax.editor.vue.edition.utils.Utils_Saving;

/** Parallax settings: tiling axes, atlas in use, packing of loose images, back to the start screen. */
public class VE_Tab_Meta_ConfigParallax extends Tab
{
	private final Table mainTable = new Table();
	private final VisCheckBox repeatOnX, repeatOnY;
	private final VisLabel atlasNameLabel = new VisLabel();

	VE_Tab_Meta_ConfigParallax()
	{
		super(false, false);

		repeatOnX = new VisCheckBox("Repeat On X");
		repeatOnX.addListener(new ChangeListener()
		{
			@Override
			public void changed(ChangeEvent event, Actor actor)
			{
				parallax_Heart.parallaxReader.setRepeatOnX(repeatOnX.isChecked());
				parallax_Heart.parallaxReader.resetPositions();
			}
		});

		repeatOnY = new VisCheckBox("Repeat On Y");
		repeatOnY.addListener(new ChangeListener()
		{
			@Override
			public void changed(ChangeEvent event, Actor actor)
			{
				parallax_Heart.parallaxReader.setRepeatOnY(repeatOnY.isChecked());
				parallax_Heart.parallaxReader.resetPositions();
			}
		});

		VisTextButton returnOption = new VisTextButton("Return to selection");
		returnOption.addListener(new ChangeListener()
		{
			@Override
			public void changed(ChangeEvent event, Actor actor)
			{
				Dialogs.showOptionDialog(GVars_UI.mainUi, "Leaving", "Do you want to save the project before leaving?", OptionDialogType.YES_NO_CANCEL, new OptionDialogAdapter()
				{
					@Override
					public void yes()
					{
						// A failed save says why and stays here, rather than leaving with the work unsaved.
						if (Utils_Saving.saving_Parallax_Project(parallaxPath.getText(), parallaxName.getText(), false))
							GVars_Heart_Editor.changeVue(new Vue_Selection(), true);
					}

					@Override
					public void no()
					{GVars_Heart_Editor.changeVue(new Vue_Selection(), true);}
				});
			}
		});

		VisTextButton packUpTextures = new VisTextButton("Copy loose images next to the project");
		packUpTextures.addListener(new ChangeListener()
		{
			@Override
			public void changed(ChangeEvent event, Actor actor)
			{
				if (!Utils_Saving.hasLooseImages())
				{
					Utils_Saving.showNoLooseImages();
					return;
				}

				Dialogs.showOptionDialog(GVars_UI.mainUi, "Packing", "Copy the loose images into \"" + parallaxName.getText() + "_images\""
						+ "\nnext to the project, so the project folder can be moved?", OptionDialogType.YES_NO, new OptionDialogAdapter()
						{
							@Override
							public void yes()
							{Utils_Saving.packTextures();}
						});
			}
		});

		mainTable.add(new VisLabel("-- Configuration --")).colspan(2).row();
		mainTable.add(repeatOnX);
		mainTable.add(repeatOnY).row();
		mainTable.add(atlasNameLabel).colspan(2).row();
		mainTable.add(packUpTextures).colspan(2).row();
		mainTable.add(new VisLabel("-- Parameter --")).colspan(2).row();
		mainTable.add(returnOption).colspan(2).row();
	}

	public void update()
	{
		repeatOnX.setProgrammaticChangeEvents(false);
		repeatOnY.setProgrammaticChangeEvents(false);
		repeatOnX.setChecked(parallax_Heart.parallaxReader.isRepeatOnX());
		repeatOnY.setChecked(parallax_Heart.parallaxReader.isRepeatOnY());
		repeatOnX.setProgrammaticChangeEvents(true);
		repeatOnY.setProgrammaticChangeEvents(true);

		String atlasName = parallax_Heart.getAtlasName();
		atlasNameLabel.setText("Current atlas : " + (atlasName == null || atlasName.isEmpty() ? "none selected" : atlasName));
	}

	@Override
	public String getTabTitle()
	{return "Parallax";}

	@Override
	public Table getContentTable()
	{
		update();
		return mainTable;
	}
}
