package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.gvars.GVars_Ui.baseSkin;
import static jks.tools2d.parallax.editor.vue.edition.GVars_Vue_Edition.currentlySelectedParallax;
import static jks.tools2d.parallax.editor.vue.edition.GVars_Vue_Edition.trashedValues;
import static jks.tools2d.parallax.editor.vue.edition.GVars_Vue_Edition.trashedValuesPosition;
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

public class VE_Tab_TextureConfig_Select extends Table 
{
	IntSpinnerModel indexPositionSpinner ; 
	Spinner indexPositionSpinerBody ; 
	Image showSelect ; 
	
	TextButton
	indexPositionSpinnerQuick_First,
	indexPositionSpinnerQuick_Last,
	indexPositionSpinnerQuick_Middle ; 
	
	ImageButton selectDirect, delete, unDelete ; 
	
	public VE_Tab_TextureConfig_Select()
	{
		indexPositionSpinner = new IntSpinnerModel(0,0,0); 
		indexPositionSpinerBody = new Spinner("Layer Selection", indexPositionSpinner);
		
		indexPositionSpinerBody.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				if(parallax_Heart.parallaxPage.layers.size != 0)
					GVars_Vue_Edition.selectLayer(parallax_Heart.parallaxPage.layers.get(indexPositionSpinner.getValue())); 
				
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
				GVars_Vue_Edition.selectLayer(parallax_Heart.parallaxPage.layers.get(0)); 			
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
				GVars_Vue_Edition.selectLayer(parallax_Heart.parallaxPage.layers.get(getMiddle())); 			
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
				GVars_Vue_Edition.selectLayer(parallax_Heart.parallaxPage.layers.get(parallax_Heart.parallaxPage.layers.size - 1)); 			
				GVars_Vue_Edition.tabbedPane.getActiveTab().getContentTable() ; 	
			}
		}) ; 
		
		selectDirect = Utils_Interface.buildSquareButton("editor/interfaces/click.png",50) ; 
		
		selectDirect.addListener(new InputListener()
		{
			@Override
			public boolean touchDown(InputEvent event, float x, float y, int pointer, int button)
			{return true ;}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				super.touchUp(event, x, y, pointer, button);
			}
			
		}) ;
		
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
				
				trashedValues.add(parallax_Heart.parallaxPage.layers.get(indexPositionSpinner.getValue())) ; 
				trashedValuesPosition.add(indexPositionSpinner.getValue()) ; 
				
				parallax_Heart.parallaxPage.layers.removeIndex(indexPositionSpinner.getValue()) ;
				
				if(parallax_Heart.parallaxPage.layers.size <= indexPositionSpinner.getValue())
					indexPositionSpinner.decrement() ; 
				
				if(parallax_Heart.parallaxPage.layers.size != 0)
				{GVars_Vue_Edition.selectLayer(parallax_Heart.parallaxPage.layers.get(indexPositionSpinner.getValue())); }
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
				
				if(parallax_Heart.parallaxPage.layers.size > trashedValuesPosition.peek()) 
					parallax_Heart.parallaxPage.layers.insert(trashedValuesPosition.peek(), trashedValues.peek());
				else
					parallax_Heart.parallaxPage.layers.add(trashedValues.peek());	
				
				GVars_Vue_Edition.selectLayer(trashedValues.peek()); 			
				trashedValues.removeIndex(trashedValues.size -1) ; 
				trashedValuesPosition.removeIndex(trashedValuesPosition.size - 1) ;
				GVars_Vue_Edition.tabbedPane.getActiveTab().getContentTable() ; 
			}
		}) ; 
		showSelect = new Image() 
		{
			@Override
			public float getPrefHeight()
			{
				return 130 ; 
			}
			
		}; 
						
		this.add(new VisLabel("SECTION SELECTION")).colspan(4).row();
		this.add(showSelect).colspan(4).row();
		this.add(indexPositionSpinerBody) ;
		this.add(indexPositionSpinnerQuick_First) ;
		this.add(indexPositionSpinnerQuick_Middle) ;
		this.add(indexPositionSpinnerQuick_Last) ;
		this.row() ; 
		this.add(new VisLabel("Select Direct : "));
		this.add(selectDirect).center().row() ; 
		this.add(new VisLabel("Delete : "));
		this.add(delete) ; 
		this.add(unDelete); 
		this.row() ;
	}
	
	public int getMiddle()
	{return parallax_Heart.parallaxPage.layers.size/2 ;}
	
	public void update()
	{
		indexPositionSpinnerQuick_Middle.setText("~" + getMiddle() + "~");
		indexPositionSpinnerQuick_Last.setText((parallax_Heart.parallaxPage.layers.size - 1) + "+");
		
		try
		{
			showSelect.setDrawable(null) ; 
			indexPositionSpinner.setMax(parallax_Heart.parallaxPage.layers.size - 1);
			indexPositionSpinner.setMin(0);
			indexPositionSpinner.setValue(parallax_Heart.parallaxPage.layers.indexOf(currentlySelectedParallax, true)) ;
		}
		catch(Exception e)
		{
			indexPositionSpinner.setValue(0); indexPositionSpinner.setMax(0); indexPositionSpinner.setMin(0);
		}
		
		if(currentlySelectedParallax !=null)
			showSelect.setDrawable(new TextureRegionDrawable(currentlySelectedParallax.getTexRegion()));
		
	}
	
}
