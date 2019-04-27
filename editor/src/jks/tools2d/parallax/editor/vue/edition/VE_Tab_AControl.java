package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.vue.edition.GVars_Vue_Edition.size_Bloc_Selection;
import static jks.tools2d.parallax.editor.vue.edition.GVars_Vue_Edition.tabbedPane;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane.TabbedPaneStyle;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneAdapter;

import jks.tools2d.parallax.editor.gvars.GVars_Ui;

public class VE_Tab_AControl extends Table
{

	VE_Tab_ColorConfig colorConfig ;
	VE_Tab_TextureList textureSelection ; 
	VE_Tab_TextureConfig textureConfig ; 
	
	VE_Tab_AControl()
	{
		this.setWidth(size_Bloc_Selection);
		this.setHeight(Gdx.graphics.getHeight());
		final VisTable container = new VisTable();
		
		tabbedPane = new TabbedPane(GVars_Ui.baseSkin.get("default", TabbedPaneStyle.class));
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
		
		colorConfig = new VE_Tab_ColorConfig() ;
		textureSelection = new VE_Tab_TextureList() ;
		textureConfig =	new VE_Tab_TextureConfig() ;
		
		tabbedPane.add(colorConfig);
		tabbedPane.add(textureSelection);
		tabbedPane.add(textureConfig);

		tabbedPane.switchTab(colorConfig);

		this.add(tabbedPane.getTable()).expandX().fillX();
		this.row();
		this.add(container).expand().fill();
		container.setZIndex(0) ; 
	}
	
	
}
