package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.vue.edition.GVars_Vue_Edition.currentlySelectedParallax;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab; 


public class Vue_Edition_SideBar_TextureConfig extends Tab
{

	Table secondTable ; 
	Vue_Edition_SideBar_TextureConfig_Edit editTable; 
	Vue_Edition_SideBar_TextureConfig_Select selectTable ; 
	
	Vue_Edition_SideBar_TextureConfig()
	{
		super(false, false);
		secondTable = new Table() ; 
		
		selectTable = new Vue_Edition_SideBar_TextureConfig_Select() ; 
		editTable = new Vue_Edition_SideBar_TextureConfig_Edit() ; 
		
		secondTable.add(selectTable).row();
		secondTable.add(editTable) ; 
	}
	
	@Override
	public String getTabTitle()
	{
		return "Texture Config";
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
		else
		{
			editTable.setVisible(false);
		}
		
		return secondTable;
	}	
}