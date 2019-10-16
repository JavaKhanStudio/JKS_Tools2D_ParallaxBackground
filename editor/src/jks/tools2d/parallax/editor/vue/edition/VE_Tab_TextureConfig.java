package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.gvars.GVars_UI.baseSkin;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.currentlySelectedParallax;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.getDefaults;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.tabbedPane;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.trashedValues;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.trashedValuesPosition;
import static jks.tools2d.parallax.editor.vue.edition.Vue_Edition.parallax_Heart;

import java.util.Collections;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.spinner.IntSpinnerModel;
import com.kotcrab.vis.ui.widget.spinner.Spinner;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneAdapter;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane.TabbedPaneStyle;

import jks.tools2d.libgdxutils.JksNumberSlider;
import jks.tools2d.libgdxutils.Utils_Interface;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.editor.gvars.GVars_UI;
import jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition;

public class VE_Tab_TextureConfig extends Tab
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
	
	IntSpinnerModel indexSelectionSpinner ; 
	Spinner indexSelectionSpinerBody ; 
	Image showSelect ; 
	
	TextButton
	indexSelectionSpinnerQuick_First,
	indexSelectionSpinnerQuick_Last,
	indexSelectionSpinnerQuick_Middle ; 
	
	ImageButton delete, unDelete ;
	
	Tab tabBasic, tabAdv, tabColorChange ;
	TabbedPane tabChoice ; 
	Table container ; 
	Table innerContainer ;
	
	int totalColspan = 5 ; 
	
	public VE_Tab_TextureConfig()
	{
		super(false,false) ;
		
		buildIndex(); 
				
		buildButtons(); 	
		
		buildImageFlip(); 
				
		buildDeleteSection();
		
		buildBasicsSliders();
		
		buildAdvSliders() ; 	
		
		buildColorChange() ; 
		
		innerContainer = new Table();
		
		tabChoice = new TabbedPane(GVars_UI.baseSkin.get("default", TabbedPaneStyle.class));
		tabChoice.setAllowTabDeselect(false);
		tabChoice.addListener(new TabbedPaneAdapter()
		{
			@Override
			public void switchedTab(Tab tab)
			{
				innerContainer.clearChildren();
				innerContainer.add(tab.getContentTable()).expand().fill();
			}
		});
		
		tabBasic = buildBasicTab() ;
		
		
		tabChoice.add(tabBasic);
		
		showSelect = new Image() 
		{
			@Override
			public float getPrefHeight()
			{return GVars_Vue_Edition.size_Bloc_Selection_Parallax_Width/2 ;}
		}; 
		
		container = new Table() ; 
		container.add(new VisLabel("SECTION SELECTED")).padTop(indexSelectionSpinerBody.getHeight()/4).padBottom(indexSelectionSpinerBody.getHeight()/4).colspan(4).row();
		container.add(showSelect).colspan(5) ; 
		container.row();
		container.add(indexSelectionSpinerBody).colspan(2) ;
		container.add(indexSelectionSpinnerQuick_First) ;
		container.add(indexSelectionSpinnerQuick_Middle) ;
		container.add(indexSelectionSpinnerQuick_Last) ;
		container.row() ; 
		container.add(indexPositionSpinerBody).colspan(2) ; 
		container.add(indexPositionSpinnerQuick_First) ;
		container.add(indexPositionSpinnerQuick_Middle) ;
		container.add(indexPositionSpinnerQuick_Last) ;
		container.row() ;
	
		container.add(clone) ; 
		container.add(makeAsDefault) ; 
		container.add(delete) ; 
		container.add(unDelete); 
		container.row();
		
		container.add(flipX) ; 
		container.add(flipY) ; 
		container.add(mirror) ; 
		
		container.row();
		container.add(tabChoice.getTable()).expandX().fillX().colspan(totalColspan);
		container.row();
		container.add(innerContainer).expandX().fillX().expandY().fillY().colspan(totalColspan);
	}

	private Tab buildBasicTab() 
	{
		Table contentTable = new Table() ; 
		Tab buildingTab = new Tab(false,false) 
		{	
			@Override
			public String getTabTitle() 
			{
				return "Basic" ; 
			}
			
			@Override
			public Table getContentTable() 
			{		
				return contentTable;
			}
		};
		
		contentTable.add(new VisLabel("Decal X")).colspan(totalColspan);
		contentTable.row() ;
		contentTable.add() ;
		contentTable.add(decalX_Slider).colspan(totalColspan - 3) ; 
		contentTable.add(decalX_Slider_Clone.cloneFromFront) ;
		contentTable.add(decalX_Slider_Clone.cloneFromBack) ;
		contentTable.row() ; 

		contentTable.add(new VisLabel("Decal Y")).colspan(totalColspan);
		contentTable.row() ;
		contentTable.add() ;
		contentTable.add(decalY_Slider).colspan(totalColspan - 3) ; 
		contentTable.add(decalY_Slider_Clone.cloneFromFront) ;
		contentTable.add(decalY_Slider_Clone.cloneFromBack) ;
		contentTable.row();
		
		contentTable.add(new VisLabel("Size Ratio")).colspan(totalColspan);
		contentTable.row() ;
		contentTable.add() ;
		contentTable.add(sizeRatio_Slider).colspan(totalColspan - 3) ;
		contentTable.add(sizeRatio_Slider_Clone.cloneFromFront) ;
		contentTable.add(sizeRatio_Slider_Clone.cloneFromBack) ;
		contentTable.row();
		
		contentTable.add(new VisLabel("At rest Speed")).colspan(totalColspan);
		contentTable.row() ;
		contentTable.add() ;
		contentTable.add(staticSpeed_Slider).colspan(totalColspan - 3);
		contentTable.add(staticSpeed_Slider_Clone.cloneFromFront) ;
		contentTable.add(staticSpeed_Slider_Clone.cloneFromBack) ;
		contentTable.row();
		
		contentTable.add(new VisLabel("-- Speed ratio X --")).colspan(totalColspan);
		contentTable.row() ;
		contentTable.add() ;
		contentTable.add(speedX_ration_Slider).colspan(totalColspan - 3);
		contentTable.add(speedX_ration_Slider_Clone.cloneFromFront) ;
		contentTable.add(speedX_ration_Slider_Clone.cloneFromBack) ;
		contentTable.row();
		
		contentTable.add(new VisLabel("-- Speed ratio Y --")).colspan(totalColspan);
		contentTable.row() ;
		contentTable.add() ;
		contentTable.add(speedY_ration_Slider).colspan(totalColspan - 3);
		contentTable.add(speedY_ration_Slider_Clone.cloneFromFront) ;
		contentTable.add(speedY_ration_Slider_Clone.cloneFromBack) ;
		contentTable.row();
		
		contentTable.add(new VisLabel("Pad X")).colspan(totalColspan);
		contentTable.row() ;
		contentTable.add() ;
		contentTable.add(pad_X_Slider).colspan(totalColspan - 3) ;
		contentTable.add(pad_X_Slider_Clone.cloneFromFront) ;
		contentTable.add(pad_X_Slider_Clone.cloneFromBack) ;
		contentTable.row();
		
		contentTable.add(new VisLabel("Pad Y")).colspan(totalColspan);
		contentTable.row() ;
		contentTable.add() ;
		contentTable.add(pad_Y_Slider).colspan(totalColspan - 3) ;
		contentTable.add(pad_Y_Slider_Clone.cloneFromFront) ;
		contentTable.add(pad_Y_Slider_Clone.cloneFromBack) ;
		
		return buildingTab ; 
	}

	private void buildColorChange() 
	{}

	private void buildAdvSliders() 
	{}

	private void buildButtons() 
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
				getDefaults().doIncrement(true);
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
	}

	private void buildImageFlip() 
	{
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
	}

	private void buildBasicsSliders() 
	{
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
		
		staticSpeed_Slider = new JksNumberSlider(-100, 100, 0.1f,baseSkin)
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
	}

	private void buildIndex() 
	{
		indexSelectionSpinner = new IntSpinnerModel(0,0,0); 
		indexSelectionSpinerBody = new Spinner("Layer Selection", indexSelectionSpinner);
		
		indexSelectionSpinerBody.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				if(parallax_Heart.parallaxReader.layers.size() != 0)
					GVars_Vue_Edition.selectLayer(parallax_Heart.parallaxReader.layers.get(indexSelectionSpinner.getValue())); 
				
				GVars_Vue_Edition.tabbedPane.getActiveTab().getContentTable() ; 
			}
		}) ; 
		
		indexSelectionSpinnerQuick_First = new TextButton("-0 ", baseSkin) ; 
		indexSelectionSpinnerQuick_First.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				GVars_Vue_Edition.selectLayer(parallax_Heart.parallaxReader.layers.get(0)); 			
				GVars_Vue_Edition.tabbedPane.getActiveTab().getContentTable() ; 	
			}
		}) ; 
		
		indexSelectionSpinnerQuick_Middle = new TextButton(" 0 ", baseSkin) ; 
		indexSelectionSpinnerQuick_Middle.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				GVars_Vue_Edition.selectLayer(parallax_Heart.parallaxReader.layers.get(getMiddle())); 			
				GVars_Vue_Edition.tabbedPane.getActiveTab().getContentTable() ; 	
			}
		}) ; 
		
		indexSelectionSpinnerQuick_Last = new TextButton("0", baseSkin) ; 
		indexSelectionSpinnerQuick_Last.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				GVars_Vue_Edition.selectLayer(parallax_Heart.parallaxReader.layers.get(parallax_Heart.parallaxReader.layers.size() - 1)); 			
				GVars_Vue_Edition.tabbedPane.getActiveTab().getContentTable() ; 	
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
	}
	
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
		
		indexSelectionSpinnerQuick_Middle.setText("~" + getMiddle() + "~");
		indexSelectionSpinnerQuick_Last.setText((parallax_Heart.parallaxReader.layers.size() - 1) + "+");
		
		try
		{
			showSelect.setDrawable(null) ; 
			indexSelectionSpinner.setMax(parallax_Heart.parallaxReader.layers.size() - 1);
			indexSelectionSpinner.setMin(0);
			indexSelectionSpinner.setValue(parallax_Heart.parallaxReader.layers.indexOf(currentlySelectedParallax)) ;
		}
		catch(Exception e)
		{
			indexSelectionSpinner.setValue(0); indexSelectionSpinner.setMax(0); indexSelectionSpinner.setMin(0);
		}
		
		if(currentlySelectedParallax !=null)
			showSelect.setDrawable(new TextureRegionDrawable(currentlySelectedParallax.getTexRegion()));
		
	}
	
	private void buildDeleteSection() 
	{
		delete = Utils_Interface.buildSquareButton("editor/interfaces/delete.png",50) ; 
		
		delete.addListener(new InputListener()
		{
			@Override
			public boolean touchDown(InputEvent event, float x, float y, int pointer, int button)
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				if(currentlySelectedParallax == null)
					return ; 
				
				trashedValues.add(parallax_Heart.parallaxReader.layers.get(indexSelectionSpinner.getValue())) ; 
				trashedValuesPosition.add(indexSelectionSpinner.getValue()) ; 
				
				parallax_Heart.parallaxReader.layers.remove(indexSelectionSpinner.getValue()) ;
				
				if(parallax_Heart.parallaxReader.layers.size() <= indexSelectionSpinner.getValue())
					indexSelectionSpinner.decrement() ; 
				
				if(parallax_Heart.parallaxReader.layers.size() != 0)
				{GVars_Vue_Edition.selectLayer(parallax_Heart.parallaxReader.layers.get(indexSelectionSpinner.getValue())); }
				else
				{currentlySelectedParallax = null ;}
				
				GVars_Vue_Edition.tabbedPane.getActiveTab().getContentTable() ; 
			}		
		}) ;
		
		unDelete = Utils_Interface.buildSquareButton("editor/interfaces/cancelAction.png",50) ; 
		
		unDelete.addListener(new InputListener()
		{
			@Override
			public boolean touchDown(InputEvent event, float x, float y, int pointer, int button)
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				if(trashedValuesPosition.size == 0)
					return ;
				
				if(parallax_Heart.parallaxReader.layers.size() > trashedValuesPosition.peek()) 
					parallax_Heart.parallaxReader.layers.add(trashedValuesPosition.peek(), trashedValues.peek());
				else
					parallax_Heart.parallaxReader.layers.add(trashedValues.peek());	
				
				GVars_Vue_Edition.selectLayer(trashedValues.peek()); 			
				trashedValues.removeIndex(trashedValues.size -1) ; 
				trashedValuesPosition.removeIndex(trashedValuesPosition.size - 1) ;
				GVars_Vue_Edition.tabbedPane.getActiveTab().getContentTable() ; 
			}
		}) ;
	}
	

	@Override
	public String getTabTitle()
	{
		return "Textures";
	}

	@Override
	public Table getContentTable()
	{
		if(currentlySelectedParallax != null)
		{
			this.update(); 
			container.setVisible(true);
		}
		else if(parallax_Heart.parallaxReader.layers.size() > 0)
		{
			GVars_Vue_Edition.selectLayer(parallax_Heart.parallaxReader.layers.get(0)); 			
		}
		else
		{
			container.setVisible(false);
		}
		
		return container;
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