package jks.tools2d.libgdxutils;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldFilter;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

import jks.tools2d.parallax.editor.inputs.GVars_Inputs;

/**
 * A slider with an editable number field. The mouse wheel moves the slider by one step, or, over the text field, the
 * digit left of the cursor (so the value can go past the slider range).
 */
public abstract class JksNumberSlider extends Table implements SelectableItem
{
	public Slider slider;
	public TextField textField;
	public boolean inTextField;

	private float value;

	private static final TextFieldFilter floatFilter = new TextFieldFilter()
	{
		@Override
		public boolean acceptChar(TextField textField, char c)
		{
			if (c == '.')
				return !textField.getText().contains(".");
			if (c == '-')
				return !textField.getText().contains("-") && textField.getCursorPosition() == 0;
			return Character.isDigit(c);
		}
	};

	public JksNumberSlider(float min, float max, float stepSize, Skin skin)
	{
		slider = new Slider(min, max, stepSize, false, skin);
		value = slider.getValue();

		textField = new TextField(format(value), skin);
		textField.setTextFieldFilter(floatFilter);
		textField.setTextFieldListener((field, c) ->
		{
			Float typed = parse(field.getText());
			if (typed == null)
				return;
			value = typed;
			slider.setProgrammaticChangeEvents(false);
			slider.setValue(typed);
			slider.setProgrammaticChangeEvents(true);
			actionOnSliderMovement();
		});

		slider.addListener(new ChangeListener()
		{
			@Override
			public void changed(ChangeEvent event, Actor actor)
			{
				value = slider.getValue();
				textField.setText(format(value));
				actionOnSliderMovement();
			}
		});

		InputListener hover = new InputListener()
		{
			@Override
			public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor)
			{
				GVars_Inputs.selectedItem = JksNumberSlider.this;
				inTextField = event.getListenerActor() == textField;
			}

			@Override
			public void exit(InputEvent event, float x, float y, int pointer, Actor toActor)
			{
				if (toActor == null || !toActor.isDescendantOf(JksNumberSlider.this))
					GVars_Inputs.quitSelectedItem(JksNumberSlider.this);
			}
		};
		slider.addListener(hover);
		textField.addListener(hover);

		add(slider);
		add(textField).width(80);
	}

	@Override
	public void scrolled(float amount)
	{
		float step = slider.getStepSize();
		if (inTextField)
		{
			// Change the digit left of the cursor: 12|3.4 changes the tens, 123.|4 the units, 123.4| the tenths.
			String text = textField.getText();
			int cursor = textField.getCursorPosition();
			if (cursor == 0)
				return;
			int dot = text.indexOf('.');
			if (dot < 0)
				dot = text.length();
			int exponent = cursor <= dot ? dot - cursor : dot - cursor + 1;
			step = (float) Math.pow(10, exponent);
		}

		setValue(round(value + Math.signum(amount) * step, step));
		actionOnSliderMovement();
	}

	@Override
	public void quit()
	{
		if (inTextField)
			textField.setCursorPosition(0);
	}

	public float getValue()
	{return value;}

	public void setValue(float newValue)
	{
		value = newValue;
		slider.setProgrammaticChangeEvents(false);
		slider.setValue(newValue);
		slider.setProgrammaticChangeEvents(true);

		int cursor = textField.getCursorPosition();
		textField.setText(format(newValue));
		textField.setCursorPosition(Math.min(cursor, textField.getText().length()));
	}

	private static Float parse(String text)
	{
		try
		{return Float.parseFloat(text);}
		catch (NumberFormatException e)
		{return null;} // "", "-", "." while typing
	}

	/** Avoids 0.30000001-style noise after repeated float additions. */
	private static float round(float number, float step)
	{
		int decimals = Math.max(0, new BigDecimal(Float.toString(step)).stripTrailingZeros().scale());
		return new BigDecimal(Float.toString(number)).setScale(decimals, RoundingMode.HALF_UP).floatValue();
	}

	private static String format(float number)
	{return new BigDecimal(Float.toString(number)).stripTrailingZeros().toPlainString();}

	public abstract void actionOnSliderMovement();
}
