package jks.tools2d.libgdxutils;

import com.badlogic.gdx.scenes.scene2d.Event;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputEvent.Type;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldFilter;

public abstract class JksNumberSlider extends Table
{

	public Slider slider ;
	public TextField textField ; 
	boolean dontSet ; 
	
	public JksNumberSlider(float min, float max, float stepSize, Skin skin)
	{
		slider = new Slider(min, max, stepSize, false, skin);
		
		textField = new TextField("", skin) ;
		textField.setTextFieldFilter(floatFilter);
		textField.setText(slider.getValue() + "");
		textField.addListener(new InputListener()
		{
			@Override
			public boolean handle(Event e)
			{
				if (!(e instanceof InputEvent)) return false;
				InputEvent event = (InputEvent) e;
				
				if(event.getType() == Type.keyTyped)
				{
					if(textField.getText().equals("") || textField.getText().equals("."))
						textField.setText(slider.getMinValue() + "");
					
					slider.setValue(Float.parseFloat(textField.getText())) ; 	
					actionOnSliderMovement() ; 
				}
				
				return false;
			}
		}) ; 
		
		slider.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchDragged(InputEvent event, float x, float y, int pointer)
			{
				textField.setText(slider.getValue() + "");
				actionOnSliderMovement() ; 
			}			
		}) ; 
		
		this.add(slider) ; 
		this.add(textField).width(80) ; 
	}
	
//	this.add(staticSpeed_Slider).pad(10).expandX().fillX() ; 
//	this.add(staticSpeed_tf).width(40).row();

	TextFieldFilter floatFilter = new TextFieldFilter()
	{	
		@Override
		public boolean acceptChar(TextField textField, char c)
		{
			if(c == '.' && !textField.getText().contains("."))
				return true ; 
			if(c == '-' && !textField.getText().contains("-") && textField.getCursorPosition() == 0)
				return true ; 
			return Character.isDigit(c);
		}
	};
	
	public float getValue()
	{
		return Float.parseFloat(textField.getText()) ; 
	}
	
	public void setValue(float value)
	{
		slider.setValue(value) ; 
		textField.setText(value + "");
	}
		
	public abstract void actionOnSliderMovement() ;	
}