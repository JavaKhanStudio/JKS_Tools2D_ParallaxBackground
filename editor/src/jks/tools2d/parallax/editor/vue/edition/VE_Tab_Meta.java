package jks.tools2d.parallax.editor.vue.edition;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneAdapter;

import jks.tools2d.libgdxutils.Utils_Interface;
import jks.tools2d.parallax.editor.driver.Names;
import jks.tools2d.parallax.editor.gvars.GVars_UI;

/** "Controls" tab: help, parallax settings and application settings. */
public class VE_Tab_Meta extends Tab
{
	private final Table mainTable;

	VE_Tab_Meta()
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

		VE_Tab_Meta_Informations infos = new VE_Tab_Meta_Informations();
		tabbedPane.add(infos);
		tabbedPane.add(new VE_Tab_Meta_ConfigParallax());
		tabbedPane.add(new VE_Tab_Meta_ConfigApplication());
		tabbedPane.switchTab(infos);
		Names.tabs(tabbedPane, "tab.controls");

		mainTable = new Table();
		mainTable.add(tabbedPane.getTable()).expandX().fillX();
		mainTable.row();
		mainTable.add(container).expand().fill();
	}

	@Override
	public String getTabTitle()
	{return "Controls";}

	@Override
	public Table getContentTable()
	{return mainTable;}
}
