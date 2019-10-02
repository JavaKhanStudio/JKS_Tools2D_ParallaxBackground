package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.gvars.GVars_Ui.baseSkin;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.currentlySelectedParallax;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.trashedValues;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.trashedValuesPosition;
import static jks.tools2d.parallax.editor.vue.edition.Vue_Edition.parallax_Heart;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.spinner.IntSpinnerModel;
import com.kotcrab.vis.ui.widget.spinner.Spinner;

import jks.tools2d.libgdxutils.Utils_Interface;
import jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition;

public class VE_Tab_TextureConfig_Select extends Table 
{
	IntSpinnerModel indexSelectionSpinner ; 
	Spinner indexSelectionSpinerBody ; 
	Image showSelect ; 
	
	TextButton
	indexSelectionSpinnerQuick_First,
	indexSelectionSpinnerQuick_Last,
	indexSelectionSpinnerQuick_Middle ; 
	
	ImageButton delete, unDelete ; 
	
	public VE_Tab_TextureConfig_Select()
	{
		buildLayerPosition(); 
		
		buildDeleteSection(); 
		
		showSelect = new Image() 
		{
			@Override
			public float getPrefHeight()
			{
				return 130 ; 
			}
			
		}; 
						
		this.add(new VisLabel("SECTION SELECTION")).padTop(indexSelectionSpinerBody.getHeight()/4).padBottom(indexSelectionSpinerBody.getHeight()/4).colspan(4).row();
		this.add(showSelect).colspan(4).row();
		this.add(indexSelectionSpinerBody) ;
		this.add(indexSelectionSpinnerQuick_First) ;
		this.add(indexSelectionSpinnerQuick_Middle) ;
		this.add(indexSelectionSpinnerQuick_Last) ;
		this.row() ; 
		this.add(new VisLabel("Delete : "));
		this.add(delete) ; 
		this.add(unDelete); 
		this.row() ;
	}




	private void buildDeleteSection() {
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




	private void buildLayerPosition() {
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
	}
	
	
	public int getMiddle()
	{return parallax_Heart.parallaxReader.layers.size()/2 ;}
	
	public void update()
	{
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
	
}