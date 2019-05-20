package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.gvars.GVars_Ui.baseSkin;
import static jks.tools2d.parallax.editor.vue.edition.Vue_Edition.parallax_Heart;
import static jks.tools2d.parallax.editor.vue.edition.data.GVars_Vue_Edition.currentlySelectedParallax;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.spinner.IntSpinnerModel;
import com.kotcrab.vis.ui.widget.spinner.Spinner;

import jks.tools2d.libgdxutils.JksNumberSlider;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.editor.vue.edition.data.GVars_Vue_Edition;

public class VE_Tab_TextureConfig_Edit extends Table
{
	JksNumberSlider 
	decalX_Slider, decalY_Slider,
	sizeRatio_Slider,
	staticSpeed_Slider,
	speedX_ration_Slider,speedY_ration_Slider; 

	IntSpinnerModel indexPositionSpinner ; 
	Spinner indexPositionSpinerBody ; 
	
	VisCheckBox flipX, flipY ; 
	
	TextButton makeAsDefault,
	indexPositionSpinnerQuick_First,indexPositionSpinnerQuick_Last,indexPositionSpinnerQuick_Middle ; 
	
	public VE_Tab_TextureConfig_Edit()
	{
		
		makeAsDefault = new TextButton("Make default Values",baseSkin) ; 
		makeAsDefault.addListener(new InputListener()
		{		
			int befaureValue = 0;
			
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				System.out.println("TODO");
			}
		}) ; 
		
		
		indexPositionSpinner = new IntSpinnerModel(0,0,0); 
		indexPositionSpinerBody = new Spinner("Layer Position", indexPositionSpinner);
		indexPositionSpinerBody.addListener(new InputListener()
		{		
			int befaureValue = 0;
			
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{
				befaureValue = indexPositionSpinner.getValue();
				return true ;
			}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				parallax_Heart.parallaxPage.layers.swap(befaureValue, indexPositionSpinner.getValue());	
				GVars_Vue_Edition.tabbedPane.getActiveTab().getContentTable() ;
			}
		}) ; 
		
		indexPositionSpinnerQuick_First = new TextButton("-0 ", baseSkin) ; 
		indexPositionSpinnerQuick_First.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				ParallaxLayer layer = parallax_Heart.parallaxPage.layers.removeIndex(indexPositionSpinner.getValue()) ;
				parallax_Heart.parallaxPage.layers.insert(0, layer);
				GVars_Vue_Edition.tabbedPane.getActiveTab().getContentTable() ;
			}
		}) ; 
		
		indexPositionSpinnerQuick_Middle = new TextButton(" 0 ", baseSkin) ; 
		indexPositionSpinnerQuick_Middle.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				int value = getMiddle() ;
				ParallaxLayer layer = parallax_Heart.parallaxPage.layers.removeIndex(indexPositionSpinner.getValue()) ;
				parallax_Heart.parallaxPage.layers.insert(value, layer);
				GVars_Vue_Edition.tabbedPane.getActiveTab().getContentTable() ;
			}
		}) ; 
		
		indexPositionSpinnerQuick_Last = new TextButton("0", baseSkin) ; 
		indexPositionSpinnerQuick_Last.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				ParallaxLayer layer = parallax_Heart.parallaxPage.layers.removeIndex(indexPositionSpinner.getValue()) ;
				parallax_Heart.parallaxPage.layers.add(layer);
				GVars_Vue_Edition.tabbedPane.getActiveTab().getContentTable() ;
			}
		}) ; 
		
				
		flipX = new VisCheckBox("Flip X") ;
		flipX.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				currentlySelectedParallax.setFlipX(flipX.isChecked(),true);
			}
		}) ; 
		
		flipY = new VisCheckBox("Flip Y") ;
		flipY.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				currentlySelectedParallax.setFlipY(flipY.isChecked(),true);
			}
		}) ; 
		
		decalX_Slider = new JksNumberSlider(-50, 50, 0.5f, baseSkin)
		{	
			@Override
			public void actionOnSliderMovement()
			{
				currentlySelectedParallax.setDecalPercentX(Float.parseFloat(decalX_Slider.textField.getText()));		
			}
		};
					
		decalY_Slider = new JksNumberSlider(-150, 150, 0.5f,baseSkin)
		{		
			@Override
			public void actionOnSliderMovement()
			{
				currentlySelectedParallax.setDecalPercentY(decalY_Slider.getValue());
			}
		} ; 
			
		sizeRatio_Slider = new JksNumberSlider(0.01f, 3, 0.005f,baseSkin)
		{		
			@Override
			public void actionOnSliderMovement()
			{
				currentlySelectedParallax.setSizeRatio(sizeRatio_Slider.getValue());
				if(currentlySelectedParallax.getSizeRatio() <= 0)
					currentlySelectedParallax.setSizeRatio(0.01f);
			}
		} ; 
		
		staticSpeed_Slider = new JksNumberSlider(-10, 10, 0.1f,baseSkin)
		{		
			@Override
			public void actionOnSliderMovement()
			{
				currentlySelectedParallax.setSpeedAtRest(staticSpeed_Slider.getValue());
			}
		} ; 
		
		speedX_ration_Slider = new JksNumberSlider(0.005f, 0.2f, 0.001f,baseSkin)
		{		
			@Override
			public void actionOnSliderMovement()
			{
				currentlySelectedParallax.getParallaxSpeedRatio().x = (speedX_ration_Slider.getValue());
			}
		} ; 
		
		speedY_ration_Slider = new JksNumberSlider(0.005f, 0.2f, 0.001f,baseSkin)
		{		
			@Override
			public void actionOnSliderMovement()
			{
				currentlySelectedParallax.getParallaxSpeedRatio().y = (speedY_ration_Slider.getValue());
			}
		} ; 
		
		
		this.add(new VisLabel("SECTION EDITION")).row();
		this.add(makeAsDefault).row();
		this.add(indexPositionSpinerBody) ; 
		this.add(indexPositionSpinnerQuick_First) ;
		this.add(indexPositionSpinnerQuick_Middle) ;
		this.add(indexPositionSpinnerQuick_Last) ;
		this.row();
		this.add(flipX) ; 
		this.add(flipY).row() ; 
		
		this.add(new VisLabel("decal X")).row();
		this.add(decalX_Slider).colspan(colspan).row() ; 

		this.add(new VisLabel("decal Y")).row();
		this.add(decalY_Slider).colspan(colspan).row(); ; 
		
		this.add(new VisLabel("Size Ratio")).row();
		this.add(sizeRatio_Slider).colspan(colspan).row();
		
		this.add(new VisLabel("At rest Speed")).row();
		this.add(staticSpeed_Slider).colspan(colspan).row();
		
		this.add(new VisLabel("-- Speed ratio --")).row();
		this.add(new VisLabel("-- X --")).row();
		this.add(speedX_ration_Slider).colspan(colspan).row();
		
		this.add(new VisLabel("-- Y --")).row();
		this.add(speedY_ration_Slider).colspan(colspan).row();
	}
	
	int colspan = 3 ; 
	
	public int getMiddle()
	{return parallax_Heart.parallaxPage.layers.size/2 ;}
	
	public void update()
	{
		indexPositionSpinnerQuick_Middle.setText("~" + getMiddle() + "~");
		indexPositionSpinnerQuick_Last.setText((parallax_Heart.parallaxPage.layers.size - 1) + "+");
		
		flipX.setChecked(currentlySelectedParallax.isFlipX());
		flipY.setChecked(currentlySelectedParallax.isFlipY());
		
		indexPositionSpinner.setMax(parallax_Heart.parallaxPage.layers.size - 1);
		indexPositionSpinner.setMin(0);
		indexPositionSpinner.setValue(parallax_Heart.parallaxPage.layers.indexOf(currentlySelectedParallax, true)) ;
			
		decalX_Slider.setValue(currentlySelectedParallax.getDecalPercentX()) ; 
		
		decalY_Slider.setValue(currentlySelectedParallax.getDecalPercentY()) ; 

		sizeRatio_Slider.setValue(currentlySelectedParallax.getSizeRatio()) ; 
	
		staticSpeed_Slider.setValue(currentlySelectedParallax.getSpeedAtRest()) ; 

		speedX_ration_Slider.setValue(currentlySelectedParallax.getParallaxSpeedRatio().x) ; 
		
		speedY_ration_Slider.setValue(currentlySelectedParallax.getParallaxSpeedRatio().y) ; 
		
	}
	
}