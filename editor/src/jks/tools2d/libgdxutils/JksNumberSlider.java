package jks.tools2d.libgdxutils;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Event;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputEvent.Type;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldFilter;

import jks.tools2d.parallax.editor.gvars.GVars_Ui;
import jks.tools2d.parallax.editor.inputs.GVars_Inputs;

public abstract class JksNumberSlider extends Table implements SelectableItem
{

	public Slider slider ;
	public TextField textField ;  
	public boolean inTextField ; 
	boolean lockExit ; 
	
	public JksNumberSlider(float min, float max, float stepSize, Skin skin)
	{
		JksNumberSlider ref = this ; 
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
					
					if(textField.getText().equals("-"))
						textField.setText("-" + slider.getMinValue());
					
					slider.setValue(Float.parseFloat(textField.getText())) ; 	
					actionOnSliderMovement() ; 
				}
				else if(event.getType() == Type.enter)
				{
					GVars_Inputs.selectedItem = ref ;
					inTextField = true ; 
				}
				else if(event.getType() == Type.touchDown)
				{
					lockExit = true ; 
				}
				else if(event.getType() == Type.exit && !lockExit)
				{
				//	GVars_Inputs.quitSelectedItem(ref) ; 
					//GVars_Ui.mainUi.setKeyboardFocus(null) ;
					//textField.clearSelection();
				} 
				else if(event.getType() == Type.touchDragged 
						|| event.getType() == Type.touchUp
						|| event.getType() == Type.mouseMoved)
				{
					
				}
				else
				{
					lockExit = false ; 
				}
				
				return true;
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
			
			@Override
			public void enter (InputEvent event, float x, float y, int pointer, Actor fromActor) 
			{
				GVars_Inputs.selectedItem = ref ;
				inTextField = false ; 
			}
			public void exit (InputEvent event, float x, float y, int pointer, Actor toActor) 
			{
				GVars_Inputs.quitSelectedItem(ref) ; 
			}
		
		}) ; 
		
		this.add(slider) ; 
		this.add(textField).width(80) ; 
	}
	
	@Override
	public void scrolled (int amount)
	{
		if(inTextField)
		{
			int cursorPosition = textField.getCursorPosition();
			
			if(cursorPosition == 0)
				return ;
			
			double power = 0 ;
			float virgulePosition = textField.getText().indexOf(".") ; 
			
			if(virgulePosition >= cursorPosition)
			{
				power = Math.pow(10, virgulePosition - cursorPosition) ;
			}
			else
			{
				power = 1/Math.pow(10, cursorPosition - virgulePosition) ;
			}
		
			textField.setText((Float.parseFloat(textField.getText()) + power * amount) + "");
			textField.setCursorPosition(cursorPosition);
//			System.out.println(power + " Cursor " + textField.getCursorPosition() + " Virgule " + virgulePosition);
		}
		else
		{
			this.setValue(slider.getValue() + slider.getStepSize() * amount) ; 
		}
		
		actionOnSliderMovement() ; 
	}
	
	@Override
	public void quit() 
	{
		if(inTextField)
			textField.setCursorPosition(0);	
	}

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