package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.size_Bloc_Selection_Parallax_Width;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.tabbedPane;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Disposable;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneAdapter;

import jks.tools2d.libgdxutils.Utils_Interface;
import jks.tools2d.parallax.editor.driver.Names;
import jks.tools2d.parallax.editor.gvars.GVars_UI;

/** Left panel: the main tabs (controls, textures, selected layer, background). */
public class VE_Tab_AControl extends Table implements Disposable
{
	private final VE_Tab_ColorConfig colorConfig;

	public VE_Tab_AControl()
	{
		final VisTable container = new VisTable();
		container.setWidth(size_Bloc_Selection_Parallax_Width);

		tabbedPane = Utils_Interface.buildTabbedPane(GVars_UI.baseSkin);
		tabbedPane.addListener(new TabbedPaneAdapter()
		{
			@Override
			public void switchedTab(Tab tab)
			{
				container.clearChildren();
				container.add(tab.getContentTable()).expand().fill();
			}
		});

		VE_Tab_Meta parallaxConfig = new VE_Tab_Meta();
		colorConfig = new VE_Tab_ColorConfig();

		tabbedPane.add(parallaxConfig);
		tabbedPane.add(new VE_Tab_TextureList());
		tabbedPane.add(new VE_Tab_Texture());
		tabbedPane.add(colorConfig);
		tabbedPane.switchTab(parallaxConfig);
		Names.tabs(tabbedPane, "tab");

		add(tabbedPane.getTable()).expandX().fillX();
		row();
		add(container).expand().fill();
		container.setZIndex(0);
		resize();
	}

	public void resize()
	{
		setWidth(size_Bloc_Selection_Parallax_Width);
		setHeight(Gdx.graphics.getHeight());
	}

	@Override
	public void dispose()
	{colorConfig.dispose();}
}
