package jks.tools2d.parallax.pages;

/**
 * The effects a SHADER layer draws its image through (docs/effect-layers.md: a closed list the library ships, Simon's
 * choice on r146), each written for libGDX, Godot and jME and checked by pixels against libGDX. Stored by name since
 * .plax format 7. Append only. Their numbers are {@link Parallax_Model#shaderAmplitude},
 * {@link Parallax_Model#shaderWavelength} and {@link Parallax_Model#shaderSpeed}, in world units and seconds, measured
 * on the image as it is drawn: they scale with the layer.
 */
public enum Enum_ShaderEffect
{
	/**
	 * A horizontal ripple, for water and heat: each row of the image is shifted sideways by
	 * {@code amplitude * sin(2 pi (y - speed * t) / wavelength)}, y from the image's bottom. The ripple runs up the image
	 * at {@code speed}; a negative speed runs it down.
	 */
	WAVE,
	/**
	 * Drifting fog: the image's opacity is thinned by up to {@code amplitude} (0 to 1) in soft patches about
	 * {@code wavelength} wide, which drift left at {@code speed}. A plain white image is a moving fog over the layers
	 * behind it. The patches come back the same only every {@link #FOG_PERIOD} wavelengths across (r216).
	 */
	FOG;

	/** How many wavelengths FOG's patches run before they repeat: its x frequencies are 7, 17 and 23 per 8. */
	public static final int FOG_PERIOD = 8;

	/** How many wavelengths this effect runs before it repeats: its phase is wrapped there. */
	public int period()
	{return this == FOG ? FOG_PERIOD : 1;}
}
