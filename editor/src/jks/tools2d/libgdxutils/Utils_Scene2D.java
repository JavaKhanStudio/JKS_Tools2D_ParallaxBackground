package jks.tools2d.libgdxutils;

import java.io.File;
import java.util.List;

import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton.ImageButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton;
import com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton.ImageTextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;

/** Scene2D helpers, originally from dermetfan's libgdx-utils. */
public final class Utils_Scene2D
{
	private Utils_Scene2D()
	{}

	/** Creates the {@link Button} subclass matching the concrete type of {@code style}. */
	public static Button newButton(ButtonStyle style, String textIfAny)
	{
		if (style instanceof ImageTextButtonStyle)
			return new ImageTextButton(textIfAny, (ImageTextButtonStyle) style);
		if (style instanceof TextButtonStyle)
			return new TextButton(textIfAny, (TextButtonStyle) style);
		if (style instanceof ImageButtonStyle)
			return new ImageButton((ImageButtonStyle) style);
		return new Button(style);
	}

	/** Reads a button style that may be any of the {@link ButtonStyle} subclasses, or returns null if absent. */
	public static ButtonStyle readButtonStyle(String name, Json json, JsonValue jsonValue)
	{
		if (!jsonValue.has(name))
			return null;

		for (Class<? extends ButtonStyle> type : List.of(TextButtonStyle.class, ImageButtonStyle.class, ImageTextButtonStyle.class, ButtonStyle.class))
		{
			try
			{
				ButtonStyle style = json.readValue(name, type, jsonValue);
				if (style != null)
					return style;
			}
			catch (RuntimeException e)
			{
				// not this style type, try the next one
			}
		}
		return null;
	}

	public static String getExtension(File file)
	{return getExtension(file.getName());}

	public static String getExtension(String fileName)
	{
		int dotIndex = fileName.lastIndexOf('.');
		return dotIndex == -1 ? "" : fileName.substring(dotIndex + 1);
	}
}
