package jks.tools2d.libgdxutils.color;


import com.badlogic.gdx.graphics.Color;

/**
 * Listener for {@link ColorPicker}.
 * @author Kotcrab
 */
public interface ColorPickerListener {
	/**
	 * Called when color selection was canceled by user (either by clicking cancel or closing the window). Note that this
	 * event can only occur when using {@link ColorPicker} dialog.
	 */
	void canceled (Color oldColor);

	/**
	 * Called when currently selected color in picker has changed. This does not mean that user finished selecting color, if
	 * you are only interested in that event use {@link #finished(Color)} or {@link #canceled(Color)}.
	 */
	void changed (Color newColor);

	/**
	 * Called when selected color in picker were reset to previously select one.
	 * @param previousColor color that was set before reset.
	 * @param newColor new picker color.
	 */
	void reset (Color previousColor, Color newColor);

	/**
	 * Called when user has finished selecting new color. Note that this
	 * event can only occur when using {@link ColorPicker} dialog.
	 */
	void finished (Color newColor);
}
