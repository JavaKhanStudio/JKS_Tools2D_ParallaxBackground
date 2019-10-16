package jks.tools2d.parallax.editor.vue.edition;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane.TabbedPaneStyle;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneAdapter;

import jks.tools2d.parallax.editor.gvars.GVars_UI; 

public class VE_Tab_TextureList extends Tab
{

	private Table mainTable ; 
	VE_Tab_TextureList_Adding add ;
	VE_Tab_TextureList_DefaultValue setDefault ; 
	
	VE_Tab_TextureList()
	{
		super(false, false);
		mainTable = new Table() ; 
		buildTextureSelector() ; 
	}
	
	public void buildTextureSelector()
	{
		final VisTable container = new VisTable();
		TabbedPane tabbedPane = new TabbedPane(GVars_UI.baseSkin.get("default", TabbedPaneStyle.class));
		tabbedPane.setAllowTabDeselect(false);
		tabbedPane.addListener(new TabbedPaneAdapter()
		{
			@Override
			public void switchedTab(Tab tab)
			{
				container.clearChildren();
				container.add(tab.getContentTable()).expand().fill();
			}
		});
		
		add = new VE_Tab_TextureList_Adding() ;
		setDefault = new VE_Tab_TextureList_DefaultValue() ; 
		
		tabbedPane.add(add);
		tabbedPane.add(setDefault);
		
		tabbedPane.switchTab(add);
		mainTable = new Table() ; 
		mainTable.add(tabbedPane.getTable()).expandX().fillX();
		mainTable.row();
		mainTable.add(container).expand().fill();
		
	}

	@Override
	public String getTabTitle()
	{return "Add texture";}

	@Override
	public Table getContentTable()
	{
		add.update();
		setDefault.update();
	
		return mainTable;
	}	
	
}