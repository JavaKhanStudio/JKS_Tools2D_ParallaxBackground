package jks.tools2d.parallax.editor.vue.model;

/** A screen of the editor: the file selection or the edition of a parallax. */
public abstract class AVue_Model implements ImportAction
{
	public abstract void init();

	/** Releases what {@link #init()} built; the view is not used afterwards. */
	public abstract void destroy();

	public abstract void update(float delta);

	public abstract void render();

	public abstract void resize(int width, int height);
}
