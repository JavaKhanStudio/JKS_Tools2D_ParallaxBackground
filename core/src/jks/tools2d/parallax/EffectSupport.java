package jks.tools2d.parallax;

import jks.tools2d.parallax.pages.Enum_LayerKind;

/**
 * Which engine draws which kind of layer, and why one does not (docs/effect-layers.md, "Saying what an engine cannot
 * draw"): what the editor marks on a layer's controls. Change a reader, change its line here, and the round that
 * proves it.
 */
public final class EffectSupport
{
	/** The readers of a page. */
	public enum Engine
	{
		/** core, in a desktop or mobile libGDX game. */
		LIBGDX,
		/** core compiled to JavaScript by GWT, in a browser libGDX game. */
		BROWSER,
		/** engines/godot's addon. */
		GODOT,
		/** engines/jme's PlaxBackground. */
		JME,
	}

	private EffectSupport()
	{}

	/** True when {@code engine} draws a layer of {@code kind}; an EMPTY layer is drawn by the game's hook everywhere. */
	public static boolean draws(Enum_LayerKind kind, Engine engine)
	{return whyNot(kind, engine) == null;}

	/** Why {@code engine} draws no layer of {@code kind}, for a person to read; null when it draws it. */
	public static String whyNot(Enum_LayerKind kind, Engine engine)
	{
		if (kind != Enum_LayerKind.PARTICLES)
			return null;
		switch (engine)
		{
			case GODOT:
				return "Not yet: Godot instances the page's particle scene in phase 2 of docs/effect-layers.md (r179)";
			case JME:
				return "jME has no 2D particle system, and libGDX's ParticleEffect draws through a Batch call JmeBatch does not"
						+ " implement: fill an EMPTY layer from a hook instead";
			default:
				return null;
		}
	}
}
