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

public class Vue_Edition_SideBar_AControl extends Table
{

	Vue_Edition_SideBar_ColorConfig colorConfig ;
	Vue_Edition_SideBar_TexutreAdding textureSelection ; 
	Vue_Edition_SideBar_TextureConfig textureConfig ; 
	
	Vue_Edition_SideBar_AControl()
	{
//		super(GVars_Ui.baseSkin.get("default", TabbedPaneStyle.class)) ;
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
		
		colorConfig = new Vue_Edition_SideBar_ColorConfig() ;
		textureSelection = new Vue_Edition_SideBar_TexutreAdding() ;
		textureConfig =	new Vue_Edition_SideBar_TextureConfig() ;
		
		tabbedPane.add(colorConfig);
		tabbedPane.add(textureSelection);
		tabbedPane.add(textureConfig);

		tabbedPane.switchTab(colorConfig);
//		tabbedPane.switchTab(textureSelection);
		
		this.add(tabbedPane.getTable()).expandX().fillX();
		this.row();
		this.add(container).expand().fill();
		container.setZIndex(0) ; 
	}
	
	
}
