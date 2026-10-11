package jks.tools2d.parallax;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.Matrix4;

/**
 * The batch a dollied layer is drawn through ({@link ParallaxPageReader#setDolly}): every draw scaled by
 * {@code scale} about the camera's middle, from the layer's own view to the camera's. The rest goes to the game's
 * batch as is. One per reader, set for each layer; nothing allocated in a frame (the particles' vertices are moved into
 * an array kept from one draw to the next).
 */
final class DollyBatch implements Batch
{
	private Batch batch;
	private float scale, centerX, centerY;
	private float[] moved = new float[20]; // a sprite: 4 vertices of 5 floats
	private final Affine2 scaled = new Affine2();

	/** Draws through {@code batch}, {@code scale} times as large about {@code (centerX, centerY)}. */
	DollyBatch set(Batch batch, float scale, float centerX, float centerY)
	{
		this.batch = batch;
		this.scale = scale;
		this.centerX = centerX;
		this.centerY = centerY;
		return this;
	}

	private float x(float x)
	{return centerX + (x - centerX) * scale;}

	private float y(float y)
	{return centerY + (y - centerY) * scale;}

	@Override
	public void draw(TextureRegion region, float x, float y, float width, float height)
	{batch.draw(region, x(x), y(y), width * scale, height * scale);}

	@Override
	public void draw(TextureRegion region, float x, float y)
	{draw(region, x, y, region.getRegionWidth(), region.getRegionHeight());}

	@Override
	public void draw(TextureRegion region, float x, float y, float originX, float originY, float width, float height, float scaleX,
			float scaleY, float rotation)
	{batch.draw(region, x(x), y(y), originX * scale, originY * scale, width * scale, height * scale, scaleX, scaleY, rotation);}

	@Override
	public void draw(TextureRegion region, float x, float y, float originX, float originY, float width, float height, float scaleX,
			float scaleY, float rotation, boolean clockwise)
	{
		batch.draw(region, x(x), y(y), originX * scale, originY * scale, width * scale, height * scale, scaleX, scaleY, rotation,
				clockwise);
	}

	@Override
	public void draw(TextureRegion region, float width, float height, Affine2 transform)
	{
		scaled.set(transform);
		scaled.m00 *= scale;
		scaled.m01 *= scale;
		scaled.m02 = x(transform.m02);
		scaled.m10 *= scale;
		scaled.m11 *= scale;
		scaled.m12 = y(transform.m12);
		batch.draw(region, width, height, scaled);
	}

	@Override
	public void draw(Texture texture, float x, float y, float originX, float originY, float width, float height, float scaleX,
			float scaleY, float rotation, int srcX, int srcY, int srcWidth, int srcHeight, boolean flipX, boolean flipY)
	{
		batch.draw(texture, x(x), y(y), originX * scale, originY * scale, width * scale, height * scale, scaleX, scaleY, rotation,
				srcX, srcY, srcWidth, srcHeight, flipX, flipY);
	}

	@Override
	public void draw(Texture texture, float x, float y, float width, float height, int srcX, int srcY, int srcWidth, int srcHeight,
			boolean flipX, boolean flipY)
	{batch.draw(texture, x(x), y(y), width * scale, height * scale, srcX, srcY, srcWidth, srcHeight, flipX, flipY);}

	@Override
	public void draw(Texture texture, float x, float y, int srcX, int srcY, int srcWidth, int srcHeight)
	{draw(texture, x, y, srcWidth, srcHeight, srcX, srcY, srcWidth, srcHeight, false, false);}

	@Override
	public void draw(Texture texture, float x, float y, float width, float height, float u, float v, float u2, float v2)
	{batch.draw(texture, x(x), y(y), width * scale, height * scale, u, v, u2, v2);}

	@Override
	public void draw(Texture texture, float x, float y)
	{draw(texture, x, y, texture.getWidth(), texture.getHeight());}

	@Override
	public void draw(Texture texture, float x, float y, float width, float height)
	{batch.draw(texture, x(x), y(y), width * scale, height * scale);}

	/** A sprite's vertices (a particle's): x and y of each, 5 floats apart, moved; color and texture coordinates kept. */
	@Override
	public void draw(Texture texture, float[] spriteVertices, int offset, int count)
	{
		if (moved.length < count)
			moved = new float[count];
		for (int i = 0; i < count; i += 5)
		{
			moved[i] = x(spriteVertices[offset + i]);
			moved[i + 1] = y(spriteVertices[offset + i + 1]);
			moved[i + 2] = spriteVertices[offset + i + 2];
			moved[i + 3] = spriteVertices[offset + i + 3];
			moved[i + 4] = spriteVertices[offset + i + 4];
		}
		batch.draw(texture, moved, 0, count);
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
