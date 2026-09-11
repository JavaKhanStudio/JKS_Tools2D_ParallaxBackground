package jks.tools2d.libgdxutils;

/** A widget reacting to the mouse wheel while hovered. */
public interface SelectableItem
{
	void scrolled(float amount);

	void quit();
}
