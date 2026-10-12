package jks.tools2d.parallax;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.Matrix4;

/**
 * The batch a layer round the page's planet is drawn through (r277, r347: docs/depth-tricks.md §3): the layer's view is
 * the side of a world turning round one centre {@code radius} under the middle of the view's bottom. A point
 * {@code (x, y)} of the view goes to the angle {@code (x - middle) / radius} round it, at {@code radius} plus its height
 * over the view's bottom from it ({@link #placeX}, {@link #placeY}): what scrolls across turns round the centre, and
 * what stands up leans out along the radius. Each {@code draw(region, x, y, width, height)}, the one call every
 * engine's batch draws, is cut where it crosses the {@link #STRIPS} vertical strips of equal width the view is divided
 * in, and each piece drawn as the quad its four corners go to, through {@code draw(Texture, float[], ...)}; what lies
 * outside the view is not drawn. {@link #turn} instead turns a particle effect as one block, every vertex of its
 * sprites. The rest goes to the batch under it as is. One per reader, set for each layer; nothing allocated in a frame.
 */
final class PlanetBatch implements Batch
{
	/** How many strips the view is cut in, across. */
	static final int STRIPS = 256;

	private Batch batch;
	private float radius, middle, ground, viewLeft, viewWidth;
	/** Set by {@link #turn}: the sprites' vertices turned as one block, from (pivotX, pivotY) to (toX, toY). */
	private boolean turning;
	private float pivotX, pivotY, toX, toY, cos, sin;
	/** A strip's quad, SpriteBatch's 20 floats; a turned sprite's vertices. */
	private final float[] quad = new float[20];
	private float[] turned = new float[20];

	/**
	 * Draws through {@code batch}, round a centre {@code radius} under the middle of the view {@code viewWidth} wide from
	 * {@code (viewLeft, viewBottom)}.
	 */
	PlanetBatch set(Batch batch, float radius, float viewLeft, float viewBottom, float viewWidth)
	{
		this.batch = batch;
		this.radius = radius;
		this.viewLeft = viewLeft;
		this.viewWidth = viewWidth;
		middle = viewLeft + viewWidth / 2;
		ground = viewBottom;
		turning = false;
		return this;
	}

	/**
	 * Turns the sprites drawn next ({@code draw(Texture, float[], ...)}: a particle effect's) as one block: moved and
	 * turned as the point {@code (x, y)} is, their middle.
	 */
	PlanetBatch turn(float x, float y)
	{
		pivotX = x;
		pivotY = y;
		toX = placeX(radius, middle, ground, x, y);
		toY = placeY(radius, middle, ground, x, y);
		double angle = -(x - middle) / radius;
		cos = (float) Math.cos(angle);
		sin = (float) Math.sin(angle);
		turning = true;
		return this;
	}

	/** Where {@code (x, y)} goes across, round a centre {@code radius} under {@code (middle, ground)}. */
	static float placeX(float radius, float middle, float ground, float x, float y)
	{return middle + (radius + y - ground) * (float) Math.sin((x - middle) / radius);}

	/** Where {@code (x, y)} goes up, round a centre {@code radius} under {@code (middle, ground)}. */
	static float placeY(float radius, float middle, float ground, float x, float y)
	{return ground - radius + (radius + y - ground) * (float) Math.cos((x - middle) / radius);}

	/**
	 * The region over {@code x} to {@code x + width} (a negative width, flipped on X, runs it right to left), cut at the
	 * strips, each piece the quad its corners go to.
	 */
	@Override
	public void draw(TextureRegion region, float x, float y, float width, float height)
	{
		float a = Math.min(x, x + width), b = Math.max(x, x + width), step = viewWidth / STRIPS;
		if (!(b > a) || !(step > 0) || b <= viewLeft || a >= viewLeft + viewWidth)
			return;
		int first = Math.max(0, (int) Math.floor((a - viewLeft) / step));
		int last = Math.min(STRIPS - 1, (int) Math.floor((b - viewLeft) / step));
		float u = region.getU(), u2 = region.getU2(), v = region.getV(), v2 = region.getV2(), color = batch.getPackedColor();
		for (int strip = first; strip <= last; strip++)
		{
			float p0 = Math.max(a, viewLeft + strip * step), p1 = Math.min(b, viewLeft + (strip + 1) * step);
			if (!(p1 > p0))
				continue;
			// The piece's share of the region, from x along the width: decreasing when it runs right to left.
			float u0 = u + (u2 - u) * ((p0 - x) / width), u1 = u + (u2 - u) * ((p1 - x) / width);
			// SpriteBatch's order: bottom left, top left, top right, bottom right.
			corner(0, p0, y, color, u0, v2);
			corner(5, p0, y + height, color, u0, v);
			corner(10, p1, y + height, color, u1, v);
			corner(15, p1, y, color, u1, v2);
			batch.draw(region.getTexture(), quad, 0, 20);
		}
	}

	private void corner(int at, float x, float y, float color, float u, float v)
	{
		quad[at] = placeX(radius, middle, ground, x, y);
		quad[at + 1] = placeY(radius, middle, ground, x, y);
		quad[at + 2] = color;
		quad[at + 3] = u;
		quad[at + 4] = v;
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

	/** A sprite's vertices (a particle's): turned as one block after {@link #turn}, as is otherwise. */
	@Override
	public void draw(Texture texture, float[] spriteVertices, int offset, int count)
	{
		if (!turning)
		{
			batch.draw(texture, spriteVertices, offset, count);
			return;
		}
		if (turned.length < count)
			turned = new float[count];
		for (int i = 0; i < count; i += 5)
		{
			float dx = spriteVertices[offset + i] - pivotX, dy = spriteVertices[offset + i + 1] - pivotY;
			turned[i] = toX + dx * cos - dy * sin;
			turned[i + 1] = toY + dx * sin + dy * cos;
			turned[i + 2] = spriteVertices[offset + i + 2];
			turned[i + 3] = spriteVertices[offset + i + 3];
			turned[i + 4] = spriteVertices[offset + i + 4];
		}
		batch.draw(texture, turned, 0, count);
	}

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
