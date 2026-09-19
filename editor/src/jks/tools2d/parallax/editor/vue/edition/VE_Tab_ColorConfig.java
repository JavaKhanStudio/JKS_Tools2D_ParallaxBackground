package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.gvars.GVars_UI.baseSkin;
import static jks.tools2d.parallax.editor.vue.Vue_Edition.parallax_Heart;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Disposable;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextField;
import com.kotcrab.vis.ui.widget.VisTextField.VisTextFieldStyle;
import com.kotcrab.vis.ui.widget.color.ColorPickerAdapter;
import com.kotcrab.vis.ui.widget.color.ExtendedColorPicker;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneAdapter;

import jks.tools2d.libgdxutils.Utils_Interface;
import jks.tools2d.parallax.editor.driver.Names;
import jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition;
import jks.tools2d.parallax.side.SquareBackground;

/** Background tab: size and gradient colors of the top and bottom squares drawn behind the layers. */
public class VE_Tab_ColorConfig extends Tab implements Disposable
{
	private final Table mainTable;
	private final List<ExtendedColorPicker> pickers = new ArrayList<>();

	VE_Tab_ColorConfig()
	{
		super(false, false);

		final VisTable container = new VisTable();
		TabbedPane tabbedPane = Utils_Interface.buildTabbedPane(baseSkin);
		tabbedPane.addListener(new TabbedPaneAdapter()
		{
			@Override
			public void switchedTab(Tab tab)
			{
				container.clearChildren();
				container.add(tab.getContentTable()).expand().fill();
			}
		});

		Tab topPalette = buildColorPalette("Top Square", parallax_Heart.topSquare);
		tabbedPane.add(topPalette);
		tabbedPane.add(buildColorPalette("Bottom Square", parallax_Heart.bottomSquare));
		tabbedPane.switchTab(topPalette);
		Names.tabs(tabbedPane, "tab.background");

		mainTable = new Table();
		mainTable.add(tabbedPane.getTable()).expandX().fillX();
		mainTable.row();
		mainTable.add(container).expand().fill();
	}

	private Tab buildColorPalette(String title, SquareBackground square)
	{
		String name = "background." + Names.slug(title) + ".";
		ExtendedColorPicker topPicker = buildPicker(square.topColor);
		ExtendedColorPicker bottomPicker = buildPicker(square.bottomColor);
		topPicker.setName(name + "topColor");
		bottomPicker.setName(name + "bottomColor");

		VisCheckBox activeBox = new VisCheckBox("Is active");
		activeBox.setName(name + "active");
		activeBox.setChecked(square.visible);
		topPicker.setVisible(square.visible);
		bottomPicker.setVisible(square.visible);
		activeBox.addListener(new ChangeListener()
		{
			@Override
			public void changed(ChangeEvent event, Actor actor)
			{
				square.visible = activeBox.isChecked();
				topPicker.setVisible(square.visible);
				bottomPicker.setVisible(square.visible);
			}
		});

		// The box size is the covered part of the screen, in percent.
		Slider boxSize = new Slider(0, 100, 1, false, baseSkin);
		TextField boxSizeText = new TextField("", baseSkin);
		boxSizeText.setDisabled(true);
		boxSize.setName(name + "size");
		boxSize.setValue(Math.round((1 - square.getScreenPercentage()) * 100));
		boxSizeText.setText(String.valueOf((int) boxSize.getValue()));
		boxSize.addListener(new ChangeListener()
		{
			@Override
			public void changed(ChangeEvent event, Actor actor)
			{
				square.setScreenPercentage(1 - boxSize.getValue() / 100);
				boxSizeText.setText(String.valueOf((int) boxSize.getValue()));
			}
		});

		Table content = new Table();
		content.add(activeBox).row();
		content.add(boxSize);
		content.add(boxSizeText).width(40).row();

		content.add(new VisLabel("Top Color")).row();
		content.add(new VisLabel("Picker"));
		content.add(buildEyedropper(topPicker, name + "topEyedropper")).row();
		content.add(topPicker).colspan(2).row();

		content.add(new VisLabel("Bottom Color")).row();
		content.add(new VisLabel("Picker"));
		content.add(buildEyedropper(bottomPicker, name + "bottomEyedropper")).row();
		content.add(bottomPicker).colspan(2);

		// Two pickers are taller than a small window: scroll instead of overflowing over the tab bars.
		Table scrolled = new Table();
		scrolled.add(Utils_Interface.buildVerticalScroll(content, baseSkin)).expand().fill();

		return new Tab(false, false)
		{
			@Override
			public String getTabTitle()
			{return title;}

			@Override
			public Table getContentTable()
			{return scrolled;}
		};
	}

	/** A picker editing {@code target} in place. */
	private ExtendedColorPicker buildPicker(Color target)
	{
		ExtendedColorPicker picker = new ExtendedColorPicker();
		picker.setColor(target);
		picker.setListener(new ColorPickerAdapter()
		{
			@Override
			public void changed(Color newColor)
			{target.set(newColor);}
		});
		pickers.add(picker);
		fitFields(picker);
		picker.invalidateHierarchy();
		return picker;
	}

	/**
	 * VisUI sizes the picker's text fields by its scaleFactor (0.6 here, GVars_UI.init) but not their font, so they
	 * showed the end of their value: "800FF" for FF8800FF, "55" for 255. Widens each field to its longest value.
	 */
	private static void fitFields(Group group)
	{
		GlyphLayout layout = new GlyphLayout();
		for (Actor child : group.getChildren())
		{
			if (child instanceof VisTextField && child.getParent() instanceof Table)
			{
				VisTextField field = (VisTextField) child;
				VisTextFieldStyle style = field.getStyle();
				BitmapFont font = style.font;
				// The hex field holds 8 digits (its text) though it accepts 6; the channel fields accept 3.
				int length = Math.max(field.getMaxLength(), field.getText().length());
				float digit = 0;
				for (char c : "0123456789ABCDEF".toCharArray())
				{
					layout.setText(font, String.valueOf(c));
					digit = Math.max(digit, layout.width);
				}
				float padding = style.background == null ? 0 : style.background.getLeftWidth() + style.background.getRightWidth();
				float width = digit * length + padding + 2; // + the cursor
				((Table) child.getParent()).getCell(field).width(width);
			}
			else if (child instanceof Group)
				fitFields((Group) child);
		}
	}

	/** Button arming the eyedropper: the next click in the preview sets this picker's color. */
	private ImageButton buildEyedropper(ExtendedColorPicker picker, String name)
	{
		ImageButton eyedropper = Utils_Interface.buildSquareButton("editor/interfaces/colorSelection.png", 50);
		eyedropper.setName(name);
		eyedropper.addListener(new ChangeListener()
		{
			@Override
			public void changed(ChangeEvent event, Actor actor)
			{GVars_Vue_Edition.colorPicked = picker;}
		});
		return eyedropper;
	}

	@Override
	public String getTabTitle()
	{return "Background";}

	@Override
	public Table getContentTable()
	{return mainTable;}

	@Override
	public void dispose()
	{
		super.dispose();
		for (ExtendedColorPicker picker : pickers)
			picker.dispose();
		pickers.clear();
	}
}
