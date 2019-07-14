package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.gvars.GVars_Ui.baseSkin;
import static jks.tools2d.parallax.editor.vue.edition.data.GVars_Vue_Edition.getDefaults;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;

import jks.tools2d.libgdxutils.JksNumberSlider;
import jks.tools2d.libgdxutils.Utils_Interface; 

public class VE_Tab_TextureList_DefaultValue extends Tab
{
	JksNumberSlider 
	decalX_Slider, decalY_Slider,
	sizeRatio_Slider,
	staticSpeed_Slider,
	speedX_ration_Slider,speedY_ration_Slider; 
	
	JksNumberSlider 
	decalX_SliderIncrement, decalY_SliderIncrement,
	sizeRatio_SliderIncrement,
	staticSpeed_SliderIncrement,
	speedX_ration_SliderIncrement,speedY_ration_SliderIncrement; 

	VisCheckBox flipX, flipY ; 
	VisCheckBox flipXAlternate, flipYAlternate ; 
	
	TextButton incrementOnce , decrementOnce; 
	
	Table mainTable ; 
	
	ButtonGroup<VisCheckBox> groupeRadio ; 
	VisCheckBox front,back, increment ; 
	
	ImageButton setBackToFrontButton, setFrontToBackButton ; 
	float frontButtonSize = 70 ; 
	
	public VE_Tab_TextureList_DefaultValue()
	{
		super(false,false) ; 
		mainTable = new Table() ; 
		
		setBackToFrontButton = Utils_Interface.buildSquareButton("editor/interfaces/addInBack.png",frontButtonSize) ; 	
		setBackToFrontButton.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				getDefaults().setIncrementBackToFront(); update();
			}
		}) ; 
		
		setFrontToBackButton = Utils_Interface.buildSquareButton("editor/interfaces/addInFront.png",frontButtonSize) ;  ;
		setFrontToBackButton.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				getDefaults().setIncrementFrontToBack(); update();
			}
		}) ; 
		
		increment = new VisCheckBox("Increment Each Time") ;
		increment.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				getDefaults().increment = increment.isChecked(); 
				showIncrement(increment.isChecked()) ; 
			}
		}) ; 
		
		incrementOnce = new TextButton("increment Once +",baseSkin) ; 
		incrementOnce.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{getDefaults().doIncrement(true) ; update() ; }
		}) ; 
		
		decrementOnce = new TextButton("decrementOnce Once -",baseSkin) ; 
		decrementOnce.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{getDefaults().doIncrement(false) ; update() ; }
		}) ; 

		groupeRadio = new ButtonGroup<VisCheckBox>() ;
	
		front = new VisCheckBox("Add at Front");
		front.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				front.setChecked(true);
				
			}
		}) ; 
		
		back = new VisCheckBox("Add at Back");
		back.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				back.setChecked(true);
			
			}
		}) ; 
		
		groupeRadio.add(front);
		groupeRadio.add(back);
		
		flipX = new VisCheckBox("Flip X") ;
		flipX.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{getDefaults().defaultModel.setFlipX(flipX.isChecked());}
		}) ; 
		
		flipY = new VisCheckBox("Flip Y") ;
		flipY.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{getDefaults().defaultModel.setFlipY(flipY.isChecked());}
		}) ; 
		
		flipXAlternate = new VisCheckBox("Alternate Flip X") ;
		flipXAlternate.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{getDefaults().setAlternateFlipX(flipXAlternate.isChecked());}
		}) ; 
		
		flipYAlternate = new VisCheckBox("Alternate Flip Y") ;
		flipYAlternate.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{getDefaults().setAlternateFlipX(flipYAlternate.isChecked());}
		}) ; 
		
		
		
		decalX_Slider = new JksNumberSlider(-50, 50, 1, baseSkin)
		{	
			@Override
			public void actionOnSliderMovement()
			{getDefaults().defaultModel.setDecal_X_Ratio(Float.parseFloat(decalX_Slider.textField.getText()));}
		};
		
		decalX_SliderIncrement = new JksNumberSlider(-50, 50, 1, baseSkin)
		{
			@Override
			public void actionOnSliderMovement()
			{getDefaults().incrementValue.setDecal_X_Ratio(Float.parseFloat(decalX_SliderIncrement.textField.getText()));}
		};
		
			
		decalY_Slider = new JksNumberSlider(-150, 150, 1,baseSkin)
		{		
			@Override
			public void actionOnSliderMovement()
			{getDefaults().defaultModel.setDecal_Y_Ratio(decalY_Slider.getValue());}
		} ; 
		
		decalY_SliderIncrement = new JksNumberSlider(-150, 150, 1,baseSkin)
		{		
			@Override
			public void actionOnSliderMovement()
			{getDefaults().incrementValue.setDecal_Y_Ratio(decalY_SliderIncrement.getValue());}
		} ; 
			
		sizeRatio_Slider = new JksNumberSlider(0.01f, 3, 0.01f,baseSkin)
		{		
			@Override
			public void actionOnSliderMovement()
			{getDefaults().defaultModel.setSizeRatio(sizeRatio_Slider.getValue());}
		} ; 
		
		sizeRatio_SliderIncrement = new JksNumberSlider(-0.5f,0.5f, 0.01f,baseSkin)
		{		
			@Override
			public void actionOnSliderMovement()
			{getDefaults().incrementValue.setSizeRatio(sizeRatio_SliderIncrement.getValue());}
		} ; 
		
		staticSpeed_Slider = new JksNumberSlider(-10, 10, 0.5f,baseSkin)
		{		
			@Override
			public void actionOnSliderMovement()
			{getDefaults().defaultModel.setSpeed(staticSpeed_Slider.getValue());}
		} ;
		
		staticSpeed_SliderIncrement = new JksNumberSlider(-10, 10, 0.5f,baseSkin)
		{		
			@Override
			public void actionOnSliderMovement()
			{getDefaults().incrementValue.setSpeed(staticSpeed_SliderIncrement.getValue());}
		} ;
		
		speedX_ration_Slider = new JksNumberSlider(0.005f, 0.2f, 0.001f,baseSkin)
		{		
			@Override
			public void actionOnSliderMovement()
			{getDefaults().defaultModel.setParallaxScalingSpeedX(speedX_ration_Slider.getValue());}
		} ; 
		
		speedX_ration_SliderIncrement = new JksNumberSlider(-0.01f, 0.01f, 0.001f,baseSkin)
		{		
			@Override
			public void actionOnSliderMovement()
			{getDefaults().incrementValue.setParallaxScalingSpeedX(speedX_ration_Slider.getValue());}
		} ; 
		
		speedY_ration_Slider = new JksNumberSlider(0.005f, 0.2f, 0.001f,baseSkin)
		{		
			@Override
			public void actionOnSliderMovement()
			{getDefaults().defaultModel.setParallaxScalingSpeedY(speedY_ration_Slider.getValue());}
		} ; 
		
		speedY_ration_SliderIncrement = new JksNumberSlider(0.005f, 0.2f, 0.001f,baseSkin)
		{		
			@Override
			public void actionOnSliderMovement()
			{getDefaults().incrementValue.setParallaxScalingSpeedY(speedY_ration_SliderIncrement.getValue());}
		} ; 
		
		
		increment.center() ; 
		mainTable.add(setBackToFrontButton).colspan(1) ;
		mainTable.add(setFrontToBackButton).colspan(1) ;
		mainTable.row() ; 
		mainTable.add(back) ; 
		mainTable.add(front) ;
		mainTable.row() ; 
				
		mainTable.add(increment).colspan(2).row()  ; 
		mainTable.add(incrementOnce).padRight(10) ; 
		mainTable.add(decrementOnce).row() ;
		
		mainTable.add(flipX) ; 
		mainTable.add(flipY).row() ; 
		mainTable.add(flipXAlternate) ;
		mainTable.add(flipYAlternate).row() ;
		
		mainTable.add(new VisLabel("decal X")).colspan(2).row();
		mainTable.add(decalX_Slider).colspan(colspan).row() ; 
		mainTable.add(decalX_SliderIncrement).colspan(colspan).row() ; 
		
		mainTable.add(new VisLabel("decal Y")).colspan(2).row();
		mainTable.add(decalY_Slider).colspan(colspan).row();
		mainTable.add(decalY_SliderIncrement).colspan(colspan).row(); ; 
		
		mainTable.add(new VisLabel("Size Ratio")).colspan(2).row();
		mainTable.add(sizeRatio_Slider).colspan(colspan).row();
		mainTable.add(sizeRatio_SliderIncrement).colspan(colspan).row();
		
		mainTable.add(new VisLabel("At rest Speed")).colspan(2).row();
		mainTable.add(staticSpeed_Slider).colspan(colspan).row();
		mainTable.add(staticSpeed_SliderIncrement).colspan(colspan).row();
		
		mainTable.add(new VisLabel("-- Speed ratio --")).colspan(2).row();
		mainTable.add(new VisLabel("- X -")).colspan(2).row();
		mainTable.add(speedX_ration_Slider).colspan(colspan).row();
		mainTable.add(speedX_ration_SliderIncrement).colspan(colspan).row();
		
		mainTable.add(new VisLabel("- Y -")).colspan(2).row();
		mainTable.add(speedY_ration_Slider).colspan(colspan).row();
		mainTable.add(speedY_ration_SliderIncrement).colspan(colspan).row();
	}
	
	int colspan = 3 ; 
	
	public void update()
	{	
		increment.setChecked(getDefaults().increment);
		
		if(getDefaults().addInFront)
			front.setChecked(true);
		else
			back.setChecked(true);
		
		flipX.setChecked(getDefaults().defaultModel.isFlipX());
		flipY.setChecked(getDefaults().defaultModel.isFlipY());
		
		flipXAlternate.setChecked(getDefaults().alternateFlipX);
		flipYAlternate.setChecked(getDefaults().alternateFlipY);
		
		decalX_Slider.setValue(getDefaults().defaultModel.getDecal_X_Ratio()) ; 
		decalX_SliderIncrement.setValue(getDefaults().incrementValue.getDecal_X_Ratio()) ; 
		
		decalY_Slider.setValue(getDefaults().defaultModel.getDecal_Y_Ratio()) ; 
		decalY_SliderIncrement.setValue(getDefaults().incrementValue.getDecal_Y_Ratio()) ; 

		sizeRatio_Slider.setValue(getDefaults().defaultModel.getSizeRatio()) ; 
		sizeRatio_SliderIncrement.setValue(getDefaults().incrementValue.getSizeRatio()) ; 
	
		staticSpeed_Slider.setValue(getDefaults().defaultModel.getSpeed()) ; 
		staticSpeed_SliderIncrement.setValue(getDefaults().incrementValue.getSpeed()) ; 

		speedX_ration_Slider.setValue(getDefaults().defaultModel.getParallaxScalingSpeedX()) ; 
		speedX_ration_SliderIncrement.setValue(getDefaults().incrementValue.getParallaxScalingSpeedX()) ;
	
		speedY_ration_Slider.setValue(getDefaults().defaultModel.getParallaxScalingSpeedY()) ; 
		speedY_ration_SliderIncrement.setValue(getDefaults().incrementValue.getParallaxScalingSpeedY()) ; 
	}
	
	public void showIncrement(boolean doShow)
	{
		flipXAlternate.setVisible(doShow);
		flipYAlternate.setVisible(doShow);
		decalX_SliderIncrement.setVisible(doShow);
		decalY_SliderIncrement.setVisible(doShow);
		sizeRatio_SliderIncrement.setVisible(doShow);
		staticSpeed_SliderIncrement.setVisible(doShow);
		speedX_ration_SliderIncrement.setVisible(doShow);
		speedY_ration_SliderIncrement.setVisible(doShow);
	}
	

	@Override
	public String getTabTitle()
	{return "Default Value";}

	@Override
	public Table getContentTable()
	{update() ; return mainTable;}
	
}