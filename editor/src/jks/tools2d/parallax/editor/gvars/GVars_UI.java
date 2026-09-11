package jks.tools2d.parallax.editor.gvars;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisTextField.VisTextFieldStyle;

public final class GVars_UI
{
	public static Skin baseSkin;
	public static Stage mainUi;

	/** Label style of the headings in the option tabs, uses {@link #areaTextFont}. */
	public static LabelStyle labelStyle_OptionsTitle;
	/** Plain copy of the default label style, for labels whose style gets tweaked. */
	public static LabelStyle labelStyle_Second;

	/** Font of every VisUI text field (spinners included), sized after the window width. */
	public static BitmapFont areaTextFont;

	private static FreeTypeFontGenerator generator;

	private GVars_UI()
	{}

	public static void init()
	{
		mainUi = new Stage(new ScreenViewport());
		baseSkin = new Skin(Gdx.files.internal("skins/uis/uiskin.json"));
		if (!VisUI.isLoaded())
		{
			// VisUI 1.5.9 targets libGDX 1.14.1; 1.14.2 only reverted return types VisUI never calls (checked in its bytecode).
			VisUI.setSkipGdxVersionCheck(true);
			VisUI.load(baseSkin);
		}
		// The editor was laid out around compact VisUI widgets (the 2019 version did this from inside a copied color picker).
		VisUI.getSizes().scaleFactor = 0.6f;

		labelStyle_Second = new LabelStyle(baseSkin.get("default", LabelStyle.class));
		labelStyle_OptionsTitle = new LabelStyle(baseSkin.get("default", LabelStyle.class));
		generator = new FreeTypeFontGenerator(Gdx.files.internal("ui/fonts/OpenSansRegular.ttf"));
		resize();

		Gdx.input.setInputProcessor(mainUi);
	}

	/** Regenerates the size-dependent font after the window size changed. */
	public static void resize()
	{
		BitmapFont previous = areaTextFont;

		FreeTypeFontParameter parameter = new FreeTypeFontParameter();
		parameter.size = Math.max(10, Gdx.graphics.getWidth() / 70);
		parameter.color = Color.WHITE;
		parameter.minFilter = TextureFilter.Linear;
		parameter.magFilter = TextureFilter.Linear;
		areaTextFont = generator.generateFont(parameter);

		VisTextFieldStyle textFieldStyle = baseSkin.get("default", VisTextFieldStyle.class);
		textFieldStyle.font = areaTextFont;
		textFieldStyle.messageFont = areaTextFont;
		labelStyle_OptionsTitle.font = areaTextFont;

		if (previous != null)
			previous.dispose();
	}

	/** Starts a new, empty stage for the next view. */
	public static void reset()
	{
		mainUi.dispose();
		mainUi = new Stage(new ScreenViewport());
		Gdx.input.setInputProcessor(mainUi);
	}

	public static void dispose()
	{
		mainUi.dispose();
		if (areaTextFont != null)
			areaTextFont.dispose();
		generator.dispose();
		VisUI.dispose(); // also disposes baseSkin
	}
}
