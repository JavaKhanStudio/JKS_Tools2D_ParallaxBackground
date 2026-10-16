package jks.tools2d.parallax.browsertest;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * The shadow lab's batch (r425): each {@code draw(region, x, y, width, height)}, the reader's tile draw, cut into
 * {@link #STRIPS} upright strips. ShadowLab's warp moves a corner sideways by its height times its distance from the
 * view's middle, which no straight quad follows: across a strip it nearly does.
 */
final class StripBatch extends SpriteBatch
{
	static final int STRIPS = 32;

	@Override
	public void draw(TextureRegion region, float x, float y, float width, float height)
	{
		Texture texture = region.getTexture();
		float u = region.getU(), across = (region.getU2() - u) / STRIPS, step = width / STRIPS;
		for (int i = 0; i < STRIPS; i++)
			draw(texture, x + i * step, y, step, height, u + i * across, region.getV2(), u + (i + 1) * across, region.getV());
	}
}
