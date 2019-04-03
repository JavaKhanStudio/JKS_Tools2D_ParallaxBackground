package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.vue.edition.GVars_Vue_Edition.currentlySelectedParallax;
import static jks.tools2d.parallax.editor.vue.edition.Vue_Edition.parallax_Heart;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.spinner.IntSpinnerModel;
import com.kotcrab.vis.ui.widget.spinner.Spinner;

import jks.tools2d.libgdxutils.Utils_Interface;

public class Vue_Edition_SideBar_TextureConfig_Select extends Table 
{
	IntSpinnerModel indexPositionSpinner ; 
	Spinner indexPositionSpinerBody ; 
	
	public Vue_Edition_SideBar_TextureConfig_Select()
	{
		indexPositionSpinner = new IntSpinnerModel(0,0,0); 
		indexPositionSpinerBody = new Spinner("Layer Selection", indexPositionSpinner);
		
		indexPositionSpinerBody.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{
				return true ; 
			}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				if(parallax_Heart.parallaxPage.layers.size != 0)
					GVars_Vue_Edition.selectLayer(parallax_Heart.parallaxPage.layers.get(indexPositionSpinner.getValue())); 
				
				GVars_Vue_Edition.tabbedPane.getActiveTab().getContentTable() ; 
			}
		}) ; 
		
		ImageButton selectDirect = Utils_Interface.buildSquareButton("editor/interfaces/placeholder.png",50) ; 
		
		selectDirect.addListener(new InputListener()
		{
			@Override
			public boolean touchDown(InputEvent event, float x, float y, int pointer, int button)
			{
				return true ; 
			}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				super.touchUp(event, x, y, pointer, button);
			}
			
		}) ;
		
		ImageButton delete = Utils_Interface.buildSquareButton("editor/interfaces/placeholder.png",50) ; 
		
		delete.addListener(new InputListener()
		{
			@Override
			public boolean touchDown(InputEvent event, float x, float y, int pointer, int button)
			{
				return true ; 
			}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				if(currentlySelectedParallax == null)
					return ; 
				
				parallax_Heart.parallaxPage.layers.removeIndex(indexPositionSpinner.getValue()) ;
				
				if(parallax_Heart.parallaxPage.layers.size != 0)
				{GVars_Vue_Edition.selectLayer(parallax_Heart.parallaxPage.layers.get(indexPositionSpinner.getValue())); }
				else
				{currentlySelectedParallax = null ;}
					
				
				GVars_Vue_Edition.tabbedPane.getActiveTab().getContentTable() ; 
			}
			
		}) ;
		
		
		
		this.add(new VisLabel("SECTION SELECTION")).row();
		this.add(indexPositionSpinerBody).row() ; 
		this.add(new VisLabel("Select Direct : "));
		this.add(selectDirect).row() ; 
		this.add(new VisLabel("Delete : "));
		this.add(delete).row() ; 
		
	}
	
	public void update()
	{
		try
		{
			indexPositionSpinner.setMax(parallax_Heart.parallaxPage.layers.size - 1);
			indexPositionSpinner.setMin(0);
			indexPositionSpinner.setValue(parallax_Heart.parallaxPage.layers.indexOf(currentlySelectedParallax, true)) ;
		}
		catch(Exception e)
		{
			indexPositionSpinner.setValue(0); indexPositionSpinner.setMax(0); indexPositionSpinner.setMin(0);
		}
	}
	
}
