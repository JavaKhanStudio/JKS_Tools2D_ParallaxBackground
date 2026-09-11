package jks.tools2d.parallax.editor.vue.edition;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane.TabbedPaneStyle;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneAdapter;

import jks.tools2d.parallax.editor.gvars.GVars_UI;

/** "Controls" tab: help, parallax settings and application settings. */
public class VE_Tab_Meta extends Tab
{
	private final Table mainTable;

	VE_Tab_Meta()
	{
		super(false, false);

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

		VE_Tab_Meta_Informations infos = new VE_Tab_Meta_Informations();
		tabbedPane.add(infos);
		tabbedPane.add(new VE_Tab_Meta_ConfigParallax());
		tabbedPane.add(new VE_Tab_Meta_ConfigApplication());
		tabbedPane.switchTab(infos);

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
