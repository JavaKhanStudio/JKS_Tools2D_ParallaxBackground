package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.size_Bloc_Selection_Parallax_Width;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.tabbedPane;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane.TabbedPaneStyle;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneAdapter;

import jks.tools2d.parallax.editor.gvars.GVars_UI;

public class VE_Tab_AControl extends Table
{

	VE_Tab_Meta parallaxConfig ;
	VE_Tab_ColorConfig colorConfig ;
	VE_Tab_TextureList textureSelection ; 
	VE_Tab_TextureConfig textureConfig ; 
	
	VE_Tab_AControl()
	{
		
		final VisTable container = new VisTable();
		container.setWidth(size_Bloc_Selection_Parallax_Width);
		
		tabbedPane = new TabbedPane(GVars_UI.baseSkin.get("default", TabbedPaneStyle.class));
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
		
		parallaxConfig = new VE_Tab_Meta() ; 
		colorConfig = new VE_Tab_ColorConfig() ;
		textureSelection = new VE_Tab_TextureList() ;
		textureConfig =	new VE_Tab_TextureConfig() ;
		
		tabbedPane.add(parallaxConfig);
		tabbedPane.add(textureSelection);
		tabbedPane.add(textureConfig);
		tabbedPane.add(colorConfig);

		tabbedPane.switchTab(parallaxConfig);

		this.add(tabbedPane.getTable()).expandX().fillX();
		this.row();
		this.add(container).expand().fill();
		container.setZIndex(0) ; 
		resize() ; 
	}
	
	public void resize()
	{
		this.setWidth(size_Bloc_Selection_Parallax_Width);
		this.setHeight(Gdx.graphics.getHeight());
	}
		
}