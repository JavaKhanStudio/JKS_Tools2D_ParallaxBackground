package jks.tools2d.parallax.editor.driver;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;

/**
 * The names {@link EditorDriver} finds controls by. They are part of what a presentation script refers to: rename one
 * and the scripts using it break.
 */
public final class Names
{
	private Names()
	{}

	/** "-- Speed ratio X --" becomes "speedRatioX". */
	public static String slug(String title)
	{
		StringBuilder slug = new StringBuilder();
		for (String word : title.split("[^A-Za-z0-9]+"))
		{
			if (word.isEmpty())
				continue;
			String lower = word.toLowerCase();
			slug.append(slug.length() == 0 ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1));
		}
		return slug.toString();
	}

	/** Names each tab's button {@code prefix.<its title>}, once every tab is added: VisUI keeps those buttons private. */
	public static void tabs(TabbedPane pane, String prefix)
	{
		Array<Tab> tabs = pane.getUIOrderedTabs();
		Array<Actor> buttons = pane.getTabsPane().getChildren();
		for (int i = 0; i < tabs.size && i < buttons.size; i++)
			buttons.get(i).setName(prefix + "." + slug(tabs.get(i).getTabTitle()));
	}
}
