package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.currentlySelectedParallax;
import static jks.tools2d.parallax.editor.vue.edition.Vue_Edition.parallax_Heart;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;

import jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition; 


public class VE_Tab_TextureConfig extends Tab
{

	Table secondTable ; 
	VE_Tab_TextureConfig_Edit editTable; 
	VE_Tab_TextureConfig_Select selectTable ; 
	
	VE_Tab_TextureConfig()
	{
		super(false, false);
		secondTable = new Table() ; 
		
		selectTable = new VE_Tab_TextureConfig_Select() ; 
		editTable = new VE_Tab_TextureConfig_Edit() ; 
		
		//secondTable.add(selectTable).row();
		secondTable.add(editTable) ; 
	}
	
	@Override
	public String getTabTitle()
	{
		return "Textures";
	}

	@Override
	public Table getContentTable()
	{
		selectTable.update();
		if(currentlySelectedParallax != null)
		{
			editTable.update(); 
			editTable.setVisible(true);
		}
		else if(parallax_Heart.parallaxReader.layers.size() > 0)
		{
			GVars_Vue_Edition.selectLayer(parallax_Heart.parallaxReader.layers.get(0)); 			
		}
		else
		{
			editTable.setVisible(false);
		}
		
		return secondTable;
	}	
}