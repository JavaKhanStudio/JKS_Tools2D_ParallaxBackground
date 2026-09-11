package jks.tools2d.libgdxutils;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

public final class Utils_Interface
{
	/** Icons are loaded once: the texture tab alone builds 16 copy buttons from the same two images. */
	private static final Map<String, Texture> icons = new HashMap<>();

	private Utils_Interface()
	{}

	public static TextureRegionDrawable buildDrawingRegionTexture(String texturePath)
	{return new TextureRegionDrawable(new TextureRegion(icon(texturePath)));}

	private static Texture icon(String texturePath)
	{
		return icons.computeIfAbsent(texturePath, path ->
		{
			Texture texture = new Texture(Gdx.files.internal(path), true);
			texture.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear);
			return texture;
		});
	}

	/** An image button with a fixed square preferred size. */
	public static ImageButton buildSquareButton(String imagePath, float size)
	{
		return new ImageButton(buildDrawingRegionTexture(imagePath))
		{
			@Override
			public float getPrefWidth()
			{return size;}

			@Override
			public float getPrefHeight()
			{return size;}
		};
	}

	public static void disposeTextures()
	{
		for (Texture texture : icons.values())
			texture.dispose();
		icons.clear();
	}
}
