package jks.tools2d.parallax;

import java.util.List;

/**
 * How far a page's layers are, read from their speeds (r444). A parallax speed is 1 / distance (the dolly, the panorama
 * and the page fog already read it so): a layer at half the front layer's speed ratio X is twice as far from the camera.
 * The speeds give the distances only up to one number, the camera's distance to the front layer: here it is the unit,
 * so a distance in "front distances" is {@code front / speed}, and the camera's distance in world units is a choice of
 * the view ({@link #cameraDistance}), stored nowhere. Nothing a reader runs every frame calls this: it is for an
 * editor's or a lab's 3D view.
 */
public final class ParallaxDepth
{
	private ParallaxDepth()
	{}

	/**
	 * How far {@code layer} is, in front distances, its page's fastest speed ratio X being {@code front}
	 * ({@link ParallaxPageReader#frontSpeedOf}): {@code front / speed}, as magnitudes. The front layer is at 1, one at a
	 * quarter of its speed at 4; a still layer (the sky) is infinitely far. A page that does not scroll is one picture,
	 * every layer at 1.
	 */
	public static float distanceOf(float front, ParallaxLayer layer)
	{
		if (!(front > 0))
			return 1;
		float speed = Math.abs(layer.getParallaxSpeedRatioX());
		return speed > 0 ? front / speed : Float.POSITIVE_INFINITY;
	}

	/** How far the layer at {@code index} of {@code page} is, in front distances: {@link #distanceOf(float, ParallaxLayer)}. */
	public static float distanceOf(List<ParallaxLayer> page, int index)
	{return distanceOf(ParallaxPageReader.frontSpeedOf(page), page.get(index));}

	/**
	 * The speed ratio X that puts a layer {@code distance} front distances away, the front's being {@code front}: the
	 * inverse of {@link #distanceOf(float, ParallaxLayer)}, for an editor that sets a layer by its distance. 0 (still) for
	 * an infinite distance.
	 */
	public static float speedAt(float front, float distance)
	{return distance > 0 ? front / distance : front;}

	/**
	 * The camera's distance to the front layer, in world units, when the view, {@code viewWidth} world units wide, spans
	 * {@code degrees} across (its horizontal field of view): {@code (viewWidth / 2) / tan(degrees / 2)}. A narrower field
	 * puts the camera farther, and every layer with it: the page looks deeper from the side.
	 */
	public static float cameraDistance(float viewWidth, float degrees)
	{return (float) (viewWidth / 2 / Math.tan(Math.toRadians(degrees) / 2));}
}
