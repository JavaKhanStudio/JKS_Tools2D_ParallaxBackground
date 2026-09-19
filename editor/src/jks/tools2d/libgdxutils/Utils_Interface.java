package jks.tools2d.libgdxutils;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

import jks.tools2d.parallax.editor.inputs.GVars_Inputs;

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

	/**
	 * A vertical scroll for a panel taller than a small window, without a background. No flick scrolling, so dragging
	 * a slider or a color picker in it moves that control rather than the scroll.
	 */
	public static ScrollPane buildVerticalScroll(Actor content, Skin skin)
	{
		ScrollPaneStyle style = new ScrollPaneStyle(skin.get(ScrollPaneStyle.class)); // a copy: skin styles are shared
		style.background = null;
		ScrollPane scroll = new ScrollPane(content, style);
		scroll.setScrollingDisabled(true, false);
		scroll.setFlickScroll(false);
		scroll.setFadeScrollBars(false);
		scroll.setOverscroll(false, false);
		// A scroll pane takes the wheel only once clicked: take it on hover, and give it back on leaving. The wheel
		// over a slider changes its value (EditorInputProcessus, after the stage): stopped here and left unhandled,
		// it reaches it instead of scrolling the pane.
		scroll.addCaptureListener(new InputListener()
		{
			@Override
			public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor)
			{
				if (pointer == -1 && event.getStage() != null)
					event.getStage().setScrollFocus(scroll);
			}

			@Override
			public void exit(InputEvent event, float x, float y, int pointer, Actor toActor)
			{
				if (pointer == -1 && event.getStage() != null && (toActor == null || !toActor.isDescendantOf(scroll))
						&& event.getStage().getScrollFocus() == scroll)
					event.getStage().setScrollFocus(null);
			}

			@Override
			public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY)
			{
				if (GVars_Inputs.selectedItem != null)
					event.stop();
				return false;
			}
		});
		return scroll;
	}

	public static void disposeTextures()
	{
		for (Texture texture : icons.values())
			texture.dispose();
		icons.clear();
	}
}
