package jks.tools2d.parallax;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.Matrix4;

/**
 * The batch a layer bent round the page's planet is drawn through ({@link ParallaxPageReader#planetBendOf}, r277,
 * docs/depth-tricks.md §3): each {@code draw(region, x, y, width, height)}, the one call every engine's batch draws, is
 * cut where it crosses the {@link #STRIPS} vertical strips of equal width the layer's view is divided in, and each piece
 * is lowered by {@code bend dx²}, dx from the view's middle to its strip's middle. The strips are fixed in the view, so
 * the art scrolls through a still curve; what lies outside the view is not drawn. The other draws (none a bent layer
 * makes) and the rest go to the batch under it as is. One per reader, set for each layer; nothing allocated in a frame.
 */
final class PlanetBatch implements Batch
{
	/** How many strips the view is cut in, across. */
	static final int STRIPS = 256;

	private Batch batch;
	private float bend, viewLeft, viewWidth;
	/** The piece of a region a strip draws, set before each piece's draw. */
	private final TextureRegion piece = new TextureRegion();

	/** Draws through {@code batch}, bent by {@code bend} (lowered {@code bend dx²}) across the view from {@code viewLeft}. */
	PlanetBatch set(Batch batch, float bend, float viewLeft, float viewWidth)
	{
		this.batch = batch;
		this.bend = bend;
		this.viewLeft = viewLeft;
		this.viewWidth = viewWidth;
		return this;
	}

	/** How far strip {@code strip} of a view {@code viewWidth} wide is lowered, bent by {@code bend}. */
	static float dropOf(float bend, float viewWidth, int strip)
	{
		float dx = (strip + 0.5f - STRIPS / 2f) * (viewWidth / STRIPS);
		return bend * dx * dx;
	}

	/**
	 * The region over {@code x} to {@code x + width} (a negative width, flipped on X, runs it right to left), cut at the
	 * strips, each piece lowered by its strip's drop.
	 */
	@Override
	public void draw(TextureRegion region, float x, float y, float width, float height)
	{
		float a = Math.min(x, x + width), b = Math.max(x, x + width), step = viewWidth / STRIPS;
		if (!(b > a) || !(step > 0) || b <= viewLeft || a >= viewLeft + viewWidth)
			return;
		int first = Math.max(0, (int) Math.floor((a - viewLeft) / step));
		int last = Math.min(STRIPS - 1, (int) Math.floor((b - viewLeft) / step));
		float u = region.getU(), u2 = region.getU2();
		piece.setTexture(region.getTexture());
		for (int strip = first; strip <= last; strip++)
		{
			float p0 = Math.max(a, viewLeft + strip * step), p1 = Math.min(b, viewLeft + (strip + 1) * step);
			if (!(p1 > p0))
				continue;
			// The piece's share of the region, from x along the width: decreasing when it runs right to left.
			float t0 = (p0 - x) / width, t1 = (p1 - x) / width;
			if (piece.getTexture() != null)
				piece.setRegion(u + (u2 - u) * Math.min(t0, t1), region.getV(), u + (u2 - u) * Math.max(t0, t1), region.getV2());
			float drop = dropOf(bend, viewWidth, strip);
			if (width > 0)
				batch.draw(piece, p0, y - drop, p1 - p0, height);
			else
				batch.draw(piece, p1, y - drop, p0 - p1, height);
		}
	}

	@Override
	public void draw(TextureRegion region, float x, float y)
	{draw(region, x, y, region.getRegionWidth(), region.getRegionHeight());}

	@Override
	public void draw(TextureRegion region, float x, float y, float originX, float originY, float width, float height, float scaleX,
			float scaleY, float rotation)
	{batch.draw(region, x, y, originX, originY, width, height, scaleX, scaleY, rotation);}

	@Override
	public void draw(TextureRegion region, float x, float y, float originX, float originY, float width, float height, float scaleX,
			float scaleY, float rotation, boolean clockwise)
	{batch.draw(region, x, y, originX, originY, width, height, scaleX, scaleY, rotation, clockwise);}

	@Override
	public void draw(TextureRegion region, float width, float height, Affine2 transform)
	{batch.draw(region, width, height, transform);}

	@Override
	public void draw(Texture texture, float x, float y, float originX, float originY, float width, float height, float scaleX,
			float scaleY, float rotation, int srcX, int srcY, int srcWidth, int srcHeight, boolean flipX, boolean flipY)
	{batch.draw(texture, x, y, originX, originY, width, height, scaleX, scaleY, rotation, srcX, srcY, srcWidth, srcHeight, flipX, flipY);}

	@Override
	public void draw(Texture texture, float x, float y, float width, float height, int srcX, int srcY, int srcWidth, int srcHeight,
			boolean flipX, boolean flipY)
	{batch.draw(texture, x, y, width, height, srcX, srcY, srcWidth, srcHeight, flipX, flipY);}

	@Override
	public void draw(Texture texture, float x, float y, int srcX, int srcY, int srcWidth, int srcHeight)
	{batch.draw(texture, x, y, srcX, srcY, srcWidth, srcHeight);}

	@Override
	public void draw(Texture texture, float x, float y, float width, float height, float u, float v, float u2, float v2)
	{batch.draw(texture, x, y, width, height, u, v, u2, v2);}

	@Override
	public void draw(Texture texture, float x, float y)
	{batch.draw(texture, x, y);}

	@Override
	public void draw(Texture texture, float x, float y, float width, float height)
	{batch.draw(texture, x, y, width, height);}

	@Override
	public void draw(Texture texture, float[] spriteVertices, int offset, int count)
	{batch.draw(texture, spriteVertices, offset, count);}

	@Override
	public void begin()
	{batch.begin();}

	@Override
	public void end()
	{batch.end();}

	@Override
	public void setColor(Color tint)
	{batch.setColor(tint);}

	@Override
	public void setColor(float r, float g, float b, float a)
	{batch.setColor(r, g, b, a);}

	@Override
	public Color getColor()
	{return batch.getColor();}

	@Override
	public void setPackedColor(float packedColor)
	{batch.setPackedColor(packedColor);}

	@Override
	public float getPackedColor()
	{return batch.getPackedColor();}

	@Override
	public void flush()
	{batch.flush();}

	@Override
	public void disableBlending()
	{batch.disableBlending();}

	@Override
	public void enableBlending()
	{batch.enableBlending();}

	@Override
	public void setBlendFunction(int srcFunc, int dstFunc)
	{batch.setBlendFunction(srcFunc, dstFunc);}

	@Override
	public void setBlendFunctionSeparate(int srcFuncColor, int dstFuncColor, int srcFuncAlpha, int dstFuncAlpha)
	{batch.setBlendFunctionSeparate(srcFuncColor, dstFuncColor, srcFuncAlpha, dstFuncAlpha);}

	@Override
	public int getBlendSrcFunc()
	{return batch.getBlendSrcFunc();}

	@Override
	public int getBlendDstFunc()
	{return batch.getBlendDstFunc();}

	@Override
	public int getBlendSrcFuncAlpha()
	{return batch.getBlendSrcFuncAlpha();}

	@Override
	public int getBlendDstFuncAlpha()
	{return batch.getBlendDstFuncAlpha();}

	@Override
	public Matrix4 getProjectionMatrix()
	{return batch.getProjectionMatrix();}

	@Override
	public Matrix4 getTransformMatrix()
	{return batch.getTransformMatrix();}

	@Override
	public void setProjectionMatrix(Matrix4 projection)
	{batch.setProjectionMatrix(projection);}

	@Override
	public void setTransformMatrix(Matrix4 transform)
	{batch.setTransformMatrix(transform);}

	@Override
	public void setShader(ShaderProgram shader)
	{batch.setShader(shader);}

	@Override
	public ShaderProgram getShader()
	{return batch.getShader();}

	@Override
	public boolean isBlendingEnabled()
	{return batch.isBlendingEnabled();}

	@Override
	public boolean isDrawing()
	{return batch.isDrawing();}

	/** The game's batch is the game's to dispose of. */
	@Override
	public void dispose()
	{}
}
