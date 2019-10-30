package jks.tools2d.parallax.editor.vue.edition;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane.TabbedPaneStyle;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneAdapter;

import jks.tools2d.parallax.editor.gvars.GVars_UI; 

public class VE_Tab_ParallaxConfig extends Tab
{

	private Table mainTable ;
	VE_Tab_ParallaxConfig_Configuration config ; 
	VE_Tab_ParallaxConfig_AddTransfert transfert ;
	
	VE_Tab_ParallaxConfig()
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
		
		config = new VE_Tab_ParallaxConfig_Configuration() ; 
		transfert = new VE_Tab_ParallaxConfig_AddTransfert() ;
		
		
		tabbedPane.add(config);
		tabbedPane.add(transfert); // TODO WORK IN PROGRESS
		
		tabbedPane.switchTab(config);
		mainTable = new Table() ; 
		mainTable.add(tabbedPane.getTable()).expandX().fillX();
		mainTable.row();
		mainTable.add(container).expand().fill();
		
	}

	@Override
	public String getTabTitle()
	{return "Configs";}

	@Override
	public Table getContentTable()
	{
		return mainTable;
	}	
	
}