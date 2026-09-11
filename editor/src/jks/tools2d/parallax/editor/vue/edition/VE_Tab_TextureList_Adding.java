package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.gvars.GVars_UI.baseSkin;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.getDefaults;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.sizeTabsBar;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.size_Bloc_Selection_Parallax_Width;
import static jks.tools2d.parallax.editor.vue.Vue_Edition.parallax_Heart;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.util.dialog.Dialogs;
import com.kotcrab.vis.ui.util.dialog.Dialogs.OptionDialogType;
import com.kotcrab.vis.ui.util.dialog.OptionDialogAdapter;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;

import jks.tools2d.libgdxutils.JksTextureList;
import jks.tools2d.libgdxutils.Utils_Interface;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.editor.gvars.GVars_UI;
import jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition;
import jks.tools2d.parallax.editor.vue.edition.data.Position_Infos;
import jks.tools2d.parallax.editor.vue.edition.utils.Utils_LoadingImages;
import jks.tools2d.parallax.editor.vue.edition.utils.Utils_Texture;
import jks.tools2d.parallax.heart.Gvars_Parallax;

/**
 * List of the project images. The selected image gets buttons to add it as a layer, to make the layers using another
 * image use this one instead, or to remove it.
 */
public class VE_Tab_TextureList_Adding extends Tab
{
	public static JksTextureList imageList;

	private static final int divisionPower_small = 5;
	private static final int divisionPower_large = 4;

	private final Table mainTable = new Table();
	private final float buttonSize_small, buttonSize_large;

	private boolean baseButtonShow, switchButtonShow;

	private final ImageButton button_addData, button_changeData, button_removeData;
	private final ImageButton button_switchFor, button_cancel;

	/** Image whose layers will switch to the next selected image. */
	private TextureRegion changingRegion;

	VE_Tab_TextureList_Adding()
	{
		super(false, false);

		buttonSize_small = size_Bloc_Selection_Parallax_Width / (float) divisionPower_small;
		buttonSize_large = size_Bloc_Selection_Parallax_Width / (float) divisionPower_large;

		button_addData = squareButton("editor/interfaces/button_add.png", buttonSize_small);
		button_changeData = squareButton("editor/interfaces/button_transform.png", buttonSize_small);
		button_removeData = squareButton("editor/interfaces/delete.png", buttonSize_small);
		button_switchFor = squareButton("editor/interfaces/button_transform.png", buttonSize_large);
		button_cancel = squareButton("editor/interfaces/delete.png", buttonSize_large);

		button_addData.addListener(onChange(this::addSelectedAsLayer));
		button_changeData.addListener(onChange(() ->
		{
			changingRegion = imageList.getSelected();
			showBaseButton(false);
			showSwitchButton(true);
		}));
		button_removeData.addListener(onChange(this::askRemoveSelected));
		button_switchFor.addListener(onChange(() ->
		{
			Utils_Texture.changeTextureInPage(changingRegion, imageList.getSelected());
			showBaseButton(true);
			showSwitchButton(false);
		}));
		button_cancel.addListener(onChange(() ->
		{
			showBaseButton(true);
			showSwitchButton(false);
		}));

		showBaseButton(false);
		showSwitchButton(false);

		imageList = buildImageList();
		GVars_Vue_Edition.setItems();

		ScrollPane scrollPane = new ScrollPane(imageList, baseSkin);
		scrollPane.setFadeScrollBars(false);
		scrollPane.setWidth(size_Bloc_Selection_Parallax_Width);
		scrollPane.setHeight(Gdx.graphics.getHeight() - sizeTabsBar * 2);

		mainTable.setSize(scrollPane.getWidth(), scrollPane.getHeight());
		mainTable.addActor(scrollPane);
		mainTable.addActor(button_addData);
		mainTable.addActor(button_changeData);
		mainTable.addActor(button_removeData);
		mainTable.addActor(button_switchFor);
		mainTable.addActor(button_cancel);
	}

	private static ImageButton squareButton(String image, float size)
	{
		ImageButton button = Utils_Interface.buildSquareButton(image, size);
		button.setSize(size, size);
		return button;
	}

	private static ChangeListener onChange(Runnable action)
	{
		return new ChangeListener()
		{
			@Override
			public void changed(ChangeEvent event, Actor actor)
			{action.run();}
		};
	}

	private JksTextureList buildImageList()
	{
		return new JksTextureList(baseSkin, size_Bloc_Selection_Parallax_Width, size_Bloc_Selection_Parallax_Width / 2f)
		{
			@Override
			public void choiceAction(TextureRegion item)
			{
				if (!baseButtonShow && !switchButtonShow)
					showBaseButton(true);
			}

			/** Keeps the buttons on the selected row, hidden when it scrolls under the tab bar. */
			@Override
			public void drawOnSelected(Batch batch, float x, float y, float width, float itemHeight)
			{
				hideAll(y > Gdx.graphics.getHeight() - size_Bloc_Selection_Parallax_Width / 2.3f);

				float centerY = y + size_Bloc_Selection_Parallax_Width / 4f;
				button_addData.setPosition(x + buttonSize_small * 0.5f, centerY - buttonSize_small / 2);
				button_changeData.setPosition(x + buttonSize_small * 2.0f, centerY - buttonSize_small / 2);
				button_removeData.setPosition(x + buttonSize_small * 3.5f, centerY - buttonSize_small / 2);

				button_switchFor.setPosition(x + buttonSize_large * 0.5f, centerY - buttonSize_large / 2);
				button_cancel.setPosition(x + buttonSize_large * 2.0f, centerY - buttonSize_large / 2);
			}
		};
	}

	private void addSelectedAsLayer()
	{
		TextureRegion region = imageList.getSelected();
		if (region == null)
			return;

		ParallaxLayer layer = new ParallaxLayer(region, true, Gvars_Parallax.getWorldWidth(), .01f, .01f, 1);
		layer.setUpEverything(getDefaults().defaultModel);
		addItem(layer);
	}

	private void askRemoveSelected()
	{
		TextureRegion region = imageList.getSelected();
		Position_Infos position = region == null ? null : GVars_Vue_Edition.imageRef.get(region);
		if (position == null)
			return;

		String message = (position.fromAtlas
				? "Do you really want to delete this part of the atlas? You won't be able to add it back"
				: "Do you really want to delete this image and all its uses?")
				+ "\n YES: delete from the parallax AND the list"
				+ "\n NO: delete only from the parallax";

		Dialogs.showOptionDialog(GVars_UI.mainUi, "Delete image", message, OptionDialogType.YES_NO_CANCEL, new OptionDialogAdapter()
		{
			@Override
			public void yes()
			{
				Utils_LoadingImages.removeFile(region, true);
				imageList.clearSelected();
				showBaseButton(false);
				GVars_Vue_Edition.refreshActiveTab();
			}

			@Override
			public void no()
			{
				Utils_LoadingImages.removeFile(region, false);
				GVars_Vue_Edition.refreshActiveTab();
			}
		});
	}

	/** Adds a layer in front of or behind the others depending on the defaults, then moves the defaults on. */
	public static void addItem(ParallaxLayer layer)
	{addItem(layer, getDefaults().addInFront ? parallax_Heart.parallaxReader.layers.size() : 0);}

	public static void addItem(ParallaxLayer layer, int position)
	{
		parallax_Heart.parallaxReader.layers.add(Math.min(position, parallax_Heart.parallaxReader.layers.size()), layer);

		if (getDefaults().increment)
			getDefaults().doIncrement(true);

		GVars_Vue_Edition.selectLayer(layer);
		GVars_Vue_Edition.addToLinks(layer);
	}

	public void update()
	{
		boolean hasImages = imageList.getItems().size > 0;
		showBaseButton(hasImages && imageList.getSelected() != null && !switchButtonShow);
		if (!hasImages)
			showSwitchButton(false);
	}

	private void hideAll(boolean hide)
	{
		boolean base = baseButtonShow, switching = switchButtonShow;
		button_addData.setVisible(!hide && base);
		button_changeData.setVisible(!hide && base);
		button_removeData.setVisible(!hide && base);
		button_switchFor.setVisible(!hide && switching);
		button_cancel.setVisible(!hide && switching);
	}

	private void showBaseButton(boolean show)
	{
		baseButtonShow = show;
		button_addData.setVisible(show);
		button_changeData.setVisible(show);
		button_removeData.setVisible(show);
	}

	private void showSwitchButton(boolean show)
	{
		switchButtonShow = show;
		button_switchFor.setVisible(show);
		button_cancel.setVisible(show);
		if (!show)
			changingRegion = null;
	}

	@Override
	public String getTabTitle()
	{return "Adding new";}

	@Override
	public Table getContentTable()
	{
		update();
		return mainTable;
	}
}
