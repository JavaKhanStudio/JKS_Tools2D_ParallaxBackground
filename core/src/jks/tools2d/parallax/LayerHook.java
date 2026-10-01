package jks.tools2d.parallax;

import com.badlogic.gdx.graphics.g2d.Batch;

/**
 * What a game draws in an EMPTY layer ({@link jks.tools2d.parallax.pages.Enum_LayerKind#EMPTY}), registered under the
 * layer's name with {@link ParallaxPageReader#setLayerHook}. Called every frame, in the layer's place in the draw
 * order, once for each tile of the layer the view shows: on the axes the page repeats on, a box the layer's size,
 * scrolled and placed as an image layer would be. Runs every frame: allocate nothing.
 */
public interface LayerHook
{
	/**
	 * @param batch already begun, its color the page's tint at the layer's opacity (a cross-fade fades it): draw with
	 *            it, or multiply it in
	 * @param layer the EMPTY layer, its settings and scrolled distances
	 * @param x left of this tile, in world units
	 * @param y bottom of this tile, in world units
	 * @param width the layer's width: the world's width times its sizeRatio
	 * @param height the layer's height: the world's height times its sizeRatio
	 */
	void draw(Batch batch, ParallaxLayer layer, float x, float y, float width, float height);
}
