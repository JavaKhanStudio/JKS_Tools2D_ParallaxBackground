package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.gvars.GVars_UI.baseSkin;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.currentlySelectedParallax;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.getDefaults;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.trashedValues;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.trashedValuesPosition;
import static jks.tools2d.parallax.editor.vue.Vue_Edition.parallax_Heart;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.spinner.IntSpinnerModel;
import com.kotcrab.vis.ui.widget.spinner.Spinner;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;

import jks.tools2d.libgdxutils.JksNumberSlider;
import jks.tools2d.libgdxutils.Utils_Interface;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.editor.driver.Names;
import jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition;

/** Settings of the selected layer: position in the stack, flips, offsets, size, speeds, padding. */
public class VE_Tab_Texture extends Tab
{
	private static final int totalColspan = 5;

	private final Table container = new Table();
	private final Table sliders = new Table();

	private final IntSpinnerModel indexSelectionModel = new IntSpinnerModel(0, 0, 0);
	private final Spinner indexSelectionSpinner = new Spinner("Layer Selection", indexSelectionModel);
	private final IntSpinnerModel indexPositionModel = new IntSpinnerModel(0, 0, 0);
	private final Spinner indexPositionSpinner = new Spinner("Layer Position", indexPositionModel);

	private final TextButton selectMiddle = new TextButton(" 0 ", baseSkin), selectLast = new TextButton("0", baseSkin);
	private final TextButton moveMiddle = new TextButton(" 0 ", baseSkin), moveLast = new TextButton("0", baseSkin);

	private final VisCheckBox flipX = new VisCheckBox("Flip X"), flipY = new VisCheckBox("Flip Y"), mirror = new VisCheckBox("Mirror");

	private final Image showSelect = new Image()
	{
		@Override
		public float getPrefHeight()
		{return GVars_Vue_Edition.size_Bloc_Selection_Parallax_Width / 2f;}
	};

	/** Sliders of the layer properties, refreshed from the selected layer. */
	private final List<LayerSlider> layerSliders = List.of(
			new LayerSlider("Decal X", -50, 50, 0.5f, ParallaxLayer::getDecalPercentX, ParallaxLayer::setDecalPercentX),
			new LayerSlider("Decal Y", -150, 150, 0.5f, ParallaxLayer::getDecalPercentY, ParallaxLayer::setDecalPercentY),
			new LayerSlider("Size Ratio", 0.01f, 3, 0.005f, ParallaxLayer::getSizeRatio, (layer, value) -> layer.setSizeRatio(Math.max(0.01f, value))),
			new LayerSlider("At rest Speed", -100, 100, 0.1f, ParallaxLayer::getSpeedAtRest, ParallaxLayer::setSpeedAtRest),
			new LayerSlider("-- Speed ratio X --", 0.005f, 0.1f, 0.001f, ParallaxLayer::getParallaxSpeedRatioX, ParallaxLayer::setParallaxSpeedRatioX),
			new LayerSlider("-- Speed ratio Y --", 0.005f, 0.1f, 0.001f, ParallaxLayer::getParallaxSpeedRatioY, ParallaxLayer::setParallaxSpeedRatioY),
			new LayerSlider("Pad X", 0, 50, 0.1f, ParallaxLayer::getPadX, ParallaxLayer::setPadX),
			new LayerSlider("Pad Y", 0, 50, 0.05f, ParallaxLayer::getPadY, ParallaxLayer::setPadY));

	private boolean updating;

	public VE_Tab_Texture()
	{
		super(false, false);

		indexSelectionSpinner.setName("texture.selection");
		indexPositionSpinner.setName("texture.position");
		selectMiddle.setName("texture.selectMiddle");
		selectLast.setName("texture.selectLast");
		moveMiddle.setName("texture.moveMiddle");
		moveLast.setName("texture.moveLast");
		flipX.setName("texture.flipX");
		flipY.setName("texture.flipY");
		mirror.setName("texture.mirror");
		showSelect.setName("texture.preview");

		indexSelectionSpinner.setProgrammaticChangeEvents(false);
		indexPositionSpinner.setProgrammaticChangeEvents(false);
		indexSelectionSpinner.addListener(onChange(() -> select(indexSelectionModel.getValue())));
		indexPositionSpinner.addListener(onChange(() -> moveSelectedTo(indexPositionModel.getValue())));

		TextButton selectFirst = new TextButton("-0 ", baseSkin);
		selectFirst.setName("texture.selectFirst");
		selectFirst.addListener(onChange(() -> select(0)));
		selectMiddle.addListener(onChange(() -> select(layers().size() / 2)));
		selectLast.addListener(onChange(() -> select(layers().size() - 1)));

		TextButton moveFirst = new TextButton("-0 ", baseSkin);
		moveFirst.setName("texture.moveFirst");
		moveFirst.addListener(onChange(() -> moveSelectedTo(0)));
		moveMiddle.addListener(onChange(() -> moveSelectedTo(layers().size() / 2)));
		moveLast.addListener(onChange(() -> moveSelectedTo(layers().size() - 1)));

		TextButton makeAsDefault = new TextButton("Set default", baseSkin);
		makeAsDefault.setName("texture.setDefault");
		makeAsDefault.addListener(onChange(() ->
		{
			getDefaults().copyValue(currentlySelectedParallax);
			getDefaults().doIncrement(true);
		}));

		TextButton clone = new TextButton("Clone", baseSkin);
		clone.setName("texture.clone");
		clone.addListener(onChange(this::cloneLayout));

		flipX.addListener(onChange(() -> currentlySelectedParallax.setFlipX(flipX.isChecked())));
		flipY.addListener(onChange(() -> currentlySelectedParallax.setFlipY(flipY.isChecked())));
		mirror.addListener(onChange(() -> currentlySelectedParallax.setMirror(mirror.isChecked())));

		ImageButton delete = Utils_Interface.buildSquareButton("editor/interfaces/delete.png", 50);
		delete.setName("texture.delete");
		delete.addListener(onChange(this::deleteSelected));
		ImageButton unDelete = Utils_Interface.buildSquareButton("editor/interfaces/cancelAction.png", 50);
		unDelete.setName("texture.undelete");
		unDelete.addListener(onChange(this::restoreDeleted));

		for (LayerSlider slider : layerSliders)
		{
			sliders.add(new VisLabel(slider.title)).colspan(totalColspan).row();
			sliders.add();
			sliders.add(slider.slider).colspan(totalColspan - 3);
			sliders.add(slider.copyFromFront);
			sliders.add(slider.copyFromBack).row();
		}

		container.add(new VisLabel("SECTION SELECTED")).pad(6).colspan(totalColspan).row();
		container.add(showSelect).colspan(totalColspan).row();
		container.add(indexSelectionSpinner).colspan(2);
		container.add(selectFirst);
		container.add(selectMiddle);
		container.add(selectLast).row();
		container.add(indexPositionSpinner).colspan(2);
		container.add(moveFirst);
		container.add(moveMiddle);
		container.add(moveLast).row();
		container.add(clone);
		container.add(makeAsDefault);
		container.add(delete);
		container.add(unDelete).row();
		container.add(flipX);
		container.add(flipY);
		container.add(mirror).row();
		container.add(sliders).expand().fill().colspan(totalColspan);
	}

	private ChangeListener onChange(Runnable action)
	{
		return new ChangeListener()
		{
			@Override
			public void changed(ChangeEvent event, Actor actor)
			{
				if (!updating)
					action.run();
			}
		};
	}

	private static List<ParallaxLayer> layers()
	{return parallax_Heart.parallaxReader.layers;}

	private void select(int index)
	{
		if (index >= 0 && index < layers().size())
			GVars_Vue_Edition.selectLayer(layers().get(index));
		GVars_Vue_Edition.refreshActiveTab();
	}

	/** Moves the selected layer to another place in the stack, shifting the layers in between. */
	private void moveSelectedTo(int target)
	{
		List<ParallaxLayer> layers = layers();
		int from = layers.indexOf(currentlySelectedParallax);
		if (from < 0 || target < 0 || target >= layers.size())
			return;

		layers.add(target, layers.remove(from));
		GVars_Vue_Edition.refreshActiveTab();
	}

	public ParallaxLayer cloneLayout()
	{
		ParallaxLayer layer = currentlySelectedParallax.clone();
		VE_Tab_TextureList_Adding.addItem(layer, layers().indexOf(currentlySelectedParallax) + 1);
		GVars_Vue_Edition.refreshActiveTab();
		return layer;
	}

	private void deleteSelected()
	{
		int index = layers().indexOf(currentlySelectedParallax);
		if (index < 0)
			return;

		trashedValues.add(layers().remove(index));
		trashedValuesPosition.add(index);

		if (layers().isEmpty())
			currentlySelectedParallax = null;
		else
			GVars_Vue_Edition.selectLayer(layers().get(Math.min(index, layers().size() - 1)));
		GVars_Vue_Edition.refreshActiveTab();
	}

	private void restoreDeleted()
	{
		if (trashedValues.isEmpty())
			return;

		ParallaxLayer layer = trashedValues.pop();
		int position = trashedValuesPosition.pop();
		layers().add(Math.min(position, layers().size()), layer);
		GVars_Vue_Edition.selectLayer(layer);
		GVars_Vue_Edition.refreshActiveTab();
	}

	public void update()
	{
		updating = true;

		int count = layers().size();
		int selected = Math.max(0, layers().indexOf(currentlySelectedParallax));
		for (IntSpinnerModel model : new IntSpinnerModel[] { indexSelectionModel, indexPositionModel })
		{
			model.setMin(0);
			model.setMax(Math.max(0, count - 1));
			model.setValue(selected, false);
		}
		selectMiddle.setText("~" + count / 2 + "~");
		moveMiddle.setText("~" + count / 2 + "~");
		selectLast.setText((count - 1) + "+");
		moveLast.setText((count - 1) + "+");

		flipX.setChecked(currentlySelectedParallax.isFlipX());
		flipY.setChecked(currentlySelectedParallax.isFlipY());
		mirror.setChecked(currentlySelectedParallax.isMirror());

		for (LayerSlider slider : layerSliders)
			slider.slider.setValue(slider.getter.apply(currentlySelectedParallax));

		showSelect.setDrawable(new TextureRegionDrawable(currentlySelectedParallax.getTexRegion().get(0)));

		updating = false;
	}

	@Override
	public String getTabTitle()
	{return "Textures";}

	@Override
	public Table getContentTable()
	{
		if (currentlySelectedParallax == null && !layers().isEmpty())
			currentlySelectedParallax = layers().get(0);

		container.setVisible(currentlySelectedParallax != null);
		if (currentlySelectedParallax != null)
			update();

		return container;
	}

	/** A layer property: its slider, and buttons copying the value from the layer in front of / behind the selected one. */
	private static final class LayerSlider
	{
		final String title;
		final Function<ParallaxLayer, Float> getter;
		final JksNumberSlider slider;
		final ImageButton copyFromFront = Utils_Interface.buildSquareButton("editor/interfaces/down-card.png", 30);
		final ImageButton copyFromBack = Utils_Interface.buildSquareButton("editor/interfaces/up-card.png", 30);

		LayerSlider(String title, float min, float max, float step, Function<ParallaxLayer, Float> getter, BiConsumer<ParallaxLayer, Float> setter)
		{
			this.title = title;
			this.getter = getter;
			this.slider = new JksNumberSlider(min, max, step, baseSkin)
			{
				@Override
				public void actionOnSliderMovement()
				{
					if (currentlySelectedParallax != null)
						setter.accept(currentlySelectedParallax, getValue());
				}
			};

			String name = "texture." + Names.slug(title);
			slider.setName(name);
			copyFromFront.setName(name + ".fromFront");
			copyFromBack.setName(name + ".fromBack");

			copyFromFront.addListener(copyFrom(+1, setter));
			copyFromBack.addListener(copyFrom(-1, setter));
		}

		private ChangeListener copyFrom(int offset, BiConsumer<ParallaxLayer, Float> setter)
		{
			return new ChangeListener()
			{
				@Override
				public void changed(ChangeEvent event, Actor actor)
				{
					List<ParallaxLayer> layers = layers();
					int other = layers.indexOf(currentlySelectedParallax) + offset;
					if (currentlySelectedParallax == null || other < 0 || other >= layers.size())
						return;

					float value = getter.apply(layers.get(other));
					setter.accept(currentlySelectedParallax, value);
					slider.setValue(value);
				}
			};
		}
	}
}
