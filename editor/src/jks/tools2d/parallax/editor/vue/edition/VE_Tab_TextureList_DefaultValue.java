package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.gvars.GVars_UI.baseSkin;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.getDefaults;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;

import jks.tools2d.libgdxutils.JksNumberSlider;
import jks.tools2d.libgdxutils.Utils_Interface;
import jks.tools2d.parallax.editor.driver.Names;
import jks.tools2d.parallax.pages.Parallax_Model;

/**
 * Values given to the next added layer, and how they change after each addition (speeds are multiplied by their
 * increment, the rest is added).
 */
public class VE_Tab_TextureList_DefaultValue extends Tab
{
	private static final int colspan = 3;
	private static final float frontButtonSize = 70;

	private final Table mainTable = new Table();
	private final VisCheckBox increment = new VisCheckBox("Increment Each Time");
	private final VisCheckBox front = new VisCheckBox("Add at Front"), back = new VisCheckBox("Add at Back");
	private final VisCheckBox flipX = new VisCheckBox("Flip X"), flipY = new VisCheckBox("Flip Y");
	private final VisCheckBox flipXAlternate = new VisCheckBox("Alternate Flip X"), flipYAlternate = new VisCheckBox("Alternate Flip Y");

	/** Sliders editing the default layer (first of each pair) and its increment (second). */
	private final List<ModelSlider> defaultSliders = new ArrayList<>();
	private final List<ModelSlider> incrementSliders = new ArrayList<>();

	private boolean updating;

	public VE_Tab_TextureList_DefaultValue()
	{
		super(false, false);

		increment.setName("defaults.increment");
		front.setName("defaults.front");
		back.setName("defaults.back");
		flipX.setName("defaults.flipX");
		flipY.setName("defaults.flipY");
		flipXAlternate.setName("defaults.alternateFlipX");
		flipYAlternate.setName("defaults.alternateFlipY");

		ImageButton setBackToFrontButton = Utils_Interface.buildSquareButton("editor/interfaces/addInBack.png", frontButtonSize);
		setBackToFrontButton.setName("defaults.backToFront");
		setBackToFrontButton.addListener(onChange(() -> getDefaults().setIncrementBackToFront()));
		ImageButton setFrontToBackButton = Utils_Interface.buildSquareButton("editor/interfaces/addInFront.png", frontButtonSize);
		setFrontToBackButton.setName("defaults.frontToBack");
		setFrontToBackButton.addListener(onChange(() -> getDefaults().setIncrementFrontToBack()));

		increment.addListener(onChange(() -> getDefaults().increment = increment.isChecked()));

		TextButton incrementOnce = new TextButton("Increment once +", baseSkin);
		incrementOnce.setName("defaults.incrementOnce");
		incrementOnce.addListener(onChange(() -> getDefaults().doIncrement(true)));
		TextButton decrementOnce = new TextButton("Decrement once -", baseSkin);
		decrementOnce.setName("defaults.decrementOnce");
		decrementOnce.addListener(onChange(() -> getDefaults().doIncrement(false)));

		ButtonGroup<VisCheckBox> frontOrBack = new ButtonGroup<>(front, back);
		frontOrBack.setMaxCheckCount(1);
		front.addListener(onChange(() -> getDefaults().addInFront = front.isChecked()));

		flipX.addListener(onChange(() -> getDefaults().defaultModel.setFlipX(flipX.isChecked())));
		flipY.addListener(onChange(() -> getDefaults().defaultModel.setFlipY(flipY.isChecked())));
		flipXAlternate.addListener(onChange(() -> getDefaults().setAlternateFlipX(flipXAlternate.isChecked())));
		flipYAlternate.addListener(onChange(() -> getDefaults().setAlternateFlipY(flipYAlternate.isChecked())));

		mainTable.add(setBackToFrontButton);
		mainTable.add(setFrontToBackButton).row();
		mainTable.add(back);
		mainTable.add(front).row();
		mainTable.add(increment).colspan(2).row();
		mainTable.add(incrementOnce).padRight(10);
		mainTable.add(decrementOnce).row();
		mainTable.add(flipX);
		mainTable.add(flipY).row();
		mainTable.add(flipXAlternate);
		mainTable.add(flipYAlternate).row();

		// Default value range, then increment range (multiplicative factor for the speeds).
		addPair("decal X", -50, 50, 1, -50, 50, 1, Parallax_Model::getDecal_X_Ratio, Parallax_Model::setDecal_X_Ratio);
		addPair("decal Y", -150, 150, 1, -150, 150, 1, Parallax_Model::getDecal_Y_Ratio, Parallax_Model::setDecal_Y_Ratio);
		addPair("Size Ratio", 0.01f, 3, 0.01f, -0.5f, 0.5f, 0.01f, Parallax_Model::getSizeRatio,
				(model, value) -> model.setSizeRatio(model == getDefaults().defaultModel ? Math.max(0.01f, value) : value));
		addPair("At rest Speed", -10, 10, 0.5f, -10, 10, 0.5f, model -> model.speedXAtRest, (model, value) -> model.speedXAtRest = value);
		addPair("-- Speed ratio X --", 0.005f, 0.2f, 0.001f, 0.1f, 3, 0.01f, Parallax_Model::getParallaxScalingSpeedX, Parallax_Model::setParallaxScalingSpeedX);
		addPair("-- Speed ratio Y --", 0.005f, 0.2f, 0.001f, 0.1f, 3, 0.01f, Parallax_Model::getParallaxScalingSpeedY, Parallax_Model::setParallaxScalingSpeedY);
	}

	private void addPair(String title, float min, float max, float step, float incMin, float incMax, float incStep,
			Function<Parallax_Model, Float> getter, BiConsumer<Parallax_Model, Float> setter)
	{
		ModelSlider value = new ModelSlider(min, max, step, getter, setter, false);
		ModelSlider incrementValue = new ModelSlider(incMin, incMax, incStep, getter, setter, true);
		value.slider.setName("defaults." + Names.slug(title));
		incrementValue.slider.setName("defaults." + Names.slug(title) + ".increment");
		defaultSliders.add(value);
		incrementSliders.add(incrementValue);

		mainTable.add(new VisLabel(title)).colspan(2).row();
		mainTable.add(value.slider).colspan(colspan).row();
		mainTable.add(incrementValue.slider).colspan(colspan).row();
	}

	private ChangeListener onChange(Runnable action)
	{
		return new ChangeListener()
		{
			@Override
			public void changed(ChangeEvent event, Actor actor)
			{
				if (updating)
					return;
				action.run();
				update();
			}
		};
	}

	public void update()
	{
		updating = true;

		increment.setChecked(getDefaults().increment);
		front.setChecked(getDefaults().addInFront);
		back.setChecked(!getDefaults().addInFront);
		flipX.setChecked(getDefaults().defaultModel.isFlipX());
		flipY.setChecked(getDefaults().defaultModel.isFlipY());
		flipXAlternate.setChecked(getDefaults().alternateFlipX);
		flipYAlternate.setChecked(getDefaults().alternateFlipY);

		for (ModelSlider slider : defaultSliders)
			slider.refresh();
		for (ModelSlider slider : incrementSliders)
		{
			slider.refresh();
			slider.slider.setVisible(getDefaults().increment);
		}
		flipXAlternate.setVisible(getDefaults().increment);
		flipYAlternate.setVisible(getDefaults().increment);

		updating = false;
	}

	@Override
	public String getTabTitle()
	{return "Default Value";}

	@Override
	public Table getContentTable()
	{
		update();
		return mainTable;
	}

	/** Slider bound to a field of the default model or of the increment model. */
	private static final class ModelSlider
	{
		final JksNumberSlider slider;
		final Function<Parallax_Model, Float> getter;
		final boolean ofIncrement;

		ModelSlider(float min, float max, float step, Function<Parallax_Model, Float> getter, BiConsumer<Parallax_Model, Float> setter, boolean ofIncrement)
		{
			this.getter = getter;
			this.ofIncrement = ofIncrement;
			this.slider = new JksNumberSlider(min, max, step, baseSkin)
			{
				@Override
				public void actionOnSliderMovement()
				{setter.accept(model(), getValue());}
			};
		}

		Parallax_Model model()
		{return ofIncrement ? getDefaults().incrementValue : getDefaults().defaultModel;}

		void refresh()
		{slider.setValue(getter.apply(model()));}
	}
}
