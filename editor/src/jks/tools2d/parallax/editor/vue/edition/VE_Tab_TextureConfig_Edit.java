package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.gvars.GVars_Ui.baseSkin;
import static jks.tools2d.parallax.editor.vue.edition.Vue_Edition.parallax_Heart;
import static jks.tools2d.parallax.editor.vue.edition.data.GVars_Vue_Edition.*;

import java.util.Collections;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.spinner.IntSpinnerModel;
import com.kotcrab.vis.ui.widget.spinner.Spinner;

import jks.tools2d.libgdxutils.JksNumberSlider;
import jks.tools2d.libgdxutils.Utils_Interface;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.editor.vue.edition.data.GVars_Vue_Edition;

public class VE_Tab_TextureConfig_Edit extends Table
{
	JksNumberSlider 
	decalX_Slider, decalY_Slider,
	sizeRatio_Slider,
	staticSpeed_Slider,
	speedX_ration_Slider,speedY_ration_Slider,
	pad_X_Slider,pad_Y_Slider
	;
	
	CopyUpAndDown 
	decalX_Slider_Clone,
	decalY_Slider_Clone,
	sizeRatio_Slider_Clone,
	staticSpeed_Slider_Clone, 
	speedX_ration_Slider_Clone, 
	speedY_ration_Slider_Clone,
	pad_X_Slider_Clone,
	pad_Y_Slider_Clone;

	public IntSpinnerModel indexPositionSpinner ; 
	Spinner indexPositionSpinerBody ; 
	
	VisCheckBox flipX, flipY, mirror ; 
	
	TextButton makeAsDefault,
	indexPositionSpinnerQuick_First,indexPositionSpinnerQuick_Last,indexPositionSpinnerQuick_Middle,
	clone
	; 
	
	
	public VE_Tab_TextureConfig_Edit()
	{
		
		makeAsDefault = new TextButton("Set default",baseSkin) ; 
		makeAsDefault.addListener(new InputListener()
		{		
			int befaureValue = 0;
			
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				getDefaults().copyValue(currentlySelectedParallax);			
			}
		}) ; 	
		
		clone = new TextButton("Clone",baseSkin) ; 
		clone.addListener(new InputListener()
		{					
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				cloneLayout() ; 
			}
		}) ; 	
		
//		cloneIncrement = new TextButton("Inc + Clone",baseSkin) ; 
//		cloneIncrement.addListener(new InputListener()
//		{					
//			@Override
//			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
//			{return true ;}
//			
//			@Override
//			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
//			{
//				getDefaults().doIncrement(true) ; 
//				cloneLayout(); 
//			}
//		}) ; 	
		
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
				Collections.swap(parallax_Heart.parallaxReader.layers, befaureValue, indexPositionSpinner.getValue());
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
				ParallaxLayer layer = parallax_Heart.parallaxReader.layers.remove(indexPositionSpinner.getValue()) ;
				parallax_Heart.parallaxReader.layers.add(0, layer);
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
				ParallaxLayer layer = parallax_Heart.parallaxReader.layers.remove(indexPositionSpinner.getValue()) ;
				parallax_Heart.parallaxReader.layers.add(value, layer);
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
				ParallaxLayer layer = parallax_Heart.parallaxReader.layers.remove(indexPositionSpinner.getValue()) ;
				parallax_Heart.parallaxReader.layers.add(layer);
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
				currentlySelectedParallax.setFlipX(flipX.isChecked());
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
				currentlySelectedParallax.setFlipY(flipY.isChecked());
			}
		}) ; 
		
		mirror = new VisCheckBox("Mirror") ;
		mirror.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				currentlySelectedParallax.setMirror(mirror.isChecked());
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
		
		decalX_Slider_Clone = new CopyUpAndDown(indexPositionSpinner) 
		{		
			@Override
			void setValueOn(ParallaxLayer currentlySelect, ParallaxLayer layerFrom) 
			{
				currentlySelect.setDecalPercentX((layerFrom.getDecalPercentX()));
				decalX_Slider.setValue(layerFrom.getDecalPercentX());
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
		
		decalY_Slider_Clone = new CopyUpAndDown(indexPositionSpinner) 
		{
			@Override
			void setValueOn(ParallaxLayer currentlySelect, ParallaxLayer layerFrom) 
			{
				currentlySelect.setDecalPercentY(layerFrom.getDecalPercentY());
				decalY_Slider.setValue(layerFrom.getDecalPercentY());		
			}
		};
			
			
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
		
		sizeRatio_Slider_Clone = new CopyUpAndDown(indexPositionSpinner) 
		{
			@Override
			void setValueOn(ParallaxLayer currentlySelect, ParallaxLayer layerFrom) 
			{
				currentlySelect.setSizeRatio(layerFrom.getSizeRatio());
				sizeRatio_Slider.setValue(layerFrom.getSizeRatio());		
			}
		};
		
		staticSpeed_Slider = new JksNumberSlider(-10, 10, 0.1f,baseSkin)
		{		
			@Override
			public void actionOnSliderMovement()
			{
				currentlySelectedParallax.setSpeedAtRest(staticSpeed_Slider.getValue());
			}
		} ; 
		
		staticSpeed_Slider_Clone = new CopyUpAndDown(indexPositionSpinner) 
		{
			@Override
			void setValueOn(ParallaxLayer currentlySelect, ParallaxLayer layerFrom) 
			{
				currentlySelect.setSpeedAtRest(layerFrom.getSpeedAtRest());
				staticSpeed_Slider.setValue(layerFrom.getSpeedAtRest());		
			}
		};
		
		speedX_ration_Slider = new JksNumberSlider(0.005f, 0.1f, 0.001f,baseSkin)
		{		
			@Override
			public void actionOnSliderMovement()
			{
				currentlySelectedParallax.setParallaxSpeedRatioX(speedX_ration_Slider.getValue()) ;
			}
		} ;
		
		speedX_ration_Slider_Clone = new CopyUpAndDown(indexPositionSpinner) 
		{
			@Override
			void setValueOn(ParallaxLayer currentlySelect, ParallaxLayer layerFrom) 
			{
				currentlySelect.setParallaxSpeedRatioX(layerFrom.getParallaxSpeedRatioX());
				speedX_ration_Slider.setValue(layerFrom.getParallaxSpeedRatioX());		
			}
		};
		
		speedY_ration_Slider = new JksNumberSlider(0.005f, 0.1f, 0.001f,baseSkin)
		{		
			@Override
			public void actionOnSliderMovement()
			{
				currentlySelectedParallax.setParallaxSpeedRatioY(speedY_ration_Slider.getValue());
			}
		} ; 
		
		speedY_ration_Slider_Clone = new CopyUpAndDown(indexPositionSpinner) 
		{
			@Override
			void setValueOn(ParallaxLayer currentlySelect, ParallaxLayer layerFrom) 
			{
				currentlySelect.setParallaxSpeedRatioY(layerFrom.getParallaxSpeedRatioY());
				speedY_ration_Slider.setValue(layerFrom.getParallaxSpeedRatioY());		
			}
		};
		
		pad_X_Slider = new JksNumberSlider(0, 50, 0.1f,baseSkin)
		{		
			@Override
			public void actionOnSliderMovement()
			{
				currentlySelectedParallax.setPadX(pad_X_Slider.getValue());
			}
		} ; 
		
		pad_X_Slider_Clone = new CopyUpAndDown(indexPositionSpinner) 
		{
			@Override
			void setValueOn(ParallaxLayer currentlySelect, ParallaxLayer layerFrom) 
			{
				currentlySelect.setPadX(layerFrom.getPadX()) ;
				pad_X_Slider.setValue(layerFrom.getPadX());		
			}
		};
		
		pad_Y_Slider = new JksNumberSlider(0, 50, 0.05f,baseSkin)
		{		
			@Override
			public void actionOnSliderMovement()
			{
				currentlySelectedParallax.setPadY(pad_Y_Slider.getValue());
			}
		} ; 
		
		pad_Y_Slider_Clone = new CopyUpAndDown(indexPositionSpinner) 
		{
			@Override
			void setValueOn(ParallaxLayer currentlySelect, ParallaxLayer layerFrom) 
			{
				currentlySelect.setPadY(layerFrom.getPadY()) ;
				pad_Y_Slider.setValue(layerFrom.getPadY());		
			}
		};
		
		this.add(new VisLabel("SECTION EDITION")).colspan(4).row();
		this.add(clone) ; 
//		this.add(cloneIncrement) ; 
		this.add(makeAsDefault).colspan(3).row();
		this.add(indexPositionSpinerBody) ; 
		this.add(indexPositionSpinnerQuick_First) ;
		this.add(indexPositionSpinnerQuick_Middle) ;
		this.add(indexPositionSpinnerQuick_Last) ;
		this.row();
		this.add(flipX) ; 
		this.add(flipY) ; 
		this.add(mirror).row() ; 
		
		this.add(new VisLabel("Decal X")).row();
		this.add(decalX_Slider).colspan(2) ; 
		this.add(decalX_Slider_Clone.cloneFromFront) ;
		this.add(decalX_Slider_Clone.cloneFromBack) ;
		this.row() ; 

		this.add(new VisLabel("Decal Y")).row();
		this.add(decalY_Slider).colspan(2) ; 
		this.add(decalY_Slider_Clone.cloneFromFront) ;
		this.add(decalY_Slider_Clone.cloneFromBack) ;
		this.row();
		
		this.add(new VisLabel("Size Ratio")).row();
		this.add(sizeRatio_Slider).colspan(2) ;
		this.add(sizeRatio_Slider_Clone.cloneFromFront) ;
		this.add(sizeRatio_Slider_Clone.cloneFromBack) ;
		this.row();
		
		this.add(new VisLabel("At rest Speed")).row();
		this.add(staticSpeed_Slider).colspan(2);
		this.add(staticSpeed_Slider_Clone.cloneFromFront) ;
		this.add(staticSpeed_Slider_Clone.cloneFromBack) ;
		this.row();
		
		this.add(new VisLabel("-- Speed ratio X --")).row();
		this.add(speedX_ration_Slider).colspan(2);
		this.add(speedX_ration_Slider_Clone.cloneFromFront) ;
		this.add(speedX_ration_Slider_Clone.cloneFromBack) ;
		this.row();
		
		this.add(new VisLabel("-- Speed ratio Y --")).row();
		this.add(speedY_ration_Slider).colspan(2);
		this.add(speedY_ration_Slider_Clone.cloneFromFront) ;
		this.add(speedY_ration_Slider_Clone.cloneFromBack) ;
		this.row();
		
		this.add(new VisLabel("Pad X")).row();
		this.add(pad_X_Slider).colspan(2) ;
		this.add(pad_X_Slider_Clone.cloneFromFront) ;
		this.add(pad_X_Slider_Clone.cloneFromBack) ;
		this.row();
		
		this.add(new VisLabel("Pad Y")).row();
		this.add(pad_Y_Slider).colspan(2) ;
		this.add(pad_Y_Slider_Clone.cloneFromFront) ;
		this.add(pad_Y_Slider_Clone.cloneFromBack) ;
		this.row();

	}
	
	int colspan = 3 ; 
	
	public int getMiddle()
	{return parallax_Heart.parallaxReader.layers.size()/2 ;}
	
	public ParallaxLayer cloneLayout()
	{
		ParallaxLayer layer = currentlySelectedParallax.clone() ; 
		
		VE_Tab_TextureList_Adding.addItem(layer,
				indexPositionSpinner.getValue() + 1) ; 

		GVars_Vue_Edition.selectLayer(layer) ; 
		
		GVars_Vue_Edition.tabbedPane.getActiveTab().getContentTable() ; 
		
		return layer ; 
	}
	
	public void update()
	{
		indexPositionSpinnerQuick_Middle.setText("~" + getMiddle() + "~");
		indexPositionSpinnerQuick_Last.setText((parallax_Heart.parallaxReader.layers.size() - 1) + "+");
		
		flipX.setChecked(currentlySelectedParallax.isFlipX());
		flipY.setChecked(currentlySelectedParallax.isFlipY());
		
		mirror.setChecked(currentlySelectedParallax.isMirror()) ; 
		
		if(parallax_Heart.parallaxReader.layers.size() > 0)
			indexPositionSpinner.setMax(parallax_Heart.parallaxReader.layers.size() - 1);
		else
			indexPositionSpinner.setMax(0);
		
		indexPositionSpinner.setMin(0);
		indexPositionSpinner.setValue(parallax_Heart.parallaxReader.layers.indexOf(currentlySelectedParallax)) ;
			
		decalX_Slider.setValue(currentlySelectedParallax.getDecalPercentX()) ; 
		
		decalY_Slider.setValue(currentlySelectedParallax.getDecalPercentY()) ; 

		sizeRatio_Slider.setValue(currentlySelectedParallax.getSizeRatio()) ; 
	
		staticSpeed_Slider.setValue(currentlySelectedParallax.getSpeedAtRest()) ; 

		speedX_ration_Slider.setValue(currentlySelectedParallax.getParallaxSpeedRatioX()) ; 
		
		speedY_ration_Slider.setValue(currentlySelectedParallax.getParallaxSpeedRatioY()) ; 
		
		pad_X_Slider.setValue(currentlySelectedParallax.getPadX()) ; 
		
		pad_Y_Slider.setValue(currentlySelectedParallax.getPadY()) ; 
		
	}
}

abstract class CopyUpAndDown
{
	abstract void setValueOn(ParallaxLayer currentlySelect, ParallaxLayer layerFrom) ; 
	
	ImageButton cloneFromFront ; 
	ImageButton cloneFromBack ; 
	
	private static String cloneFrontImage = "editor/interfaces/down-card.png" ; 
	private static String cloneBackImage = "editor/interfaces/up-card.png" ; 
	
	private static float buttonSize = 30; 
	
	CopyUpAndDown(IntSpinnerModel posRef)
	{
		cloneFromFront = Utils_Interface.buildSquareButton(cloneFrontImage,buttonSize) ; 
		cloneFromFront.addListener(new InputListener() // ONE MORE
		{
			@Override
			public boolean touchDown(InputEvent event, float x, float y, int pointer, int button)
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				if(posRef.getValue() < parallax_Heart.parallaxReader.layers.size() - 1)
				{setValueOn(currentlySelectedParallax, parallax_Heart.parallaxReader.layers.get(posRef.getValue() + 1));}
			}
			
		}) ;
		
		cloneFromBack = Utils_Interface.buildSquareButton(cloneBackImage,buttonSize) ; 
		cloneFromBack.addListener(new InputListener() // ONE LESS
		{
			@Override
			public boolean touchDown(InputEvent event, float x, float y, int pointer, int button)
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				if(posRef.getValue() != 0)
				{setValueOn(currentlySelectedParallax, parallax_Heart.parallaxReader.layers.get(posRef.getValue() - 1));}
			}
			
		}) ;	
	}	
}