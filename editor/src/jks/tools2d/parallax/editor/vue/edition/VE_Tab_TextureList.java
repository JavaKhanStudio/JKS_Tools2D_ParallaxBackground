package jks.tools2d.parallax.editor.vue.edition;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneAdapter;

import jks.tools2d.libgdxutils.Utils_Interface;
import jks.tools2d.parallax.editor.driver.Names;
import jks.tools2d.parallax.editor.gvars.GVars_UI;

/** "Add texture" tab: the image list and the values given to new layers. */
public class VE_Tab_TextureList extends Tab
{
	private final Table mainTable = new Table();
	private final VE_Tab_TextureList_Adding add = new VE_Tab_TextureList_Adding();
	private final VE_Tab_TextureList_DefaultValue setDefault = new VE_Tab_TextureList_DefaultValue();

	VE_Tab_TextureList()
	{
		super(false, false);

		final VisTable container = new VisTable();
		TabbedPane tabbedPane = Utils_Interface.buildTabbedPane(GVars_UI.baseSkin);
		tabbedPane.addListener(new TabbedPaneAdapter()
		{
			@Override
			public void switchedTab(Tab tab)
			{
				container.clearChildren();
				container.add(tab.getContentTable()).expand().fill();
			}
		});

		tabbedPane.add(add);
		tabbedPane.add(setDefault);
		tabbedPane.switchTab(add);
		Names.tabs(tabbedPane, "tab.addTexture");

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
