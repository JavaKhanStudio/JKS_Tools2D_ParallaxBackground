package jks.tools2d.parallax.browsertest;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.Matrix4;

/**
 * A {@link Batch} that draws nothing and records the x, y, width and height of every 5-argument draw, the ones the
 * reader makes. ParallaxPageReaderTest does the same with a java.lang.reflect.Proxy, which GWT does not have.
 */
public class RecordingBatch implements Batch
{
	public final List<float[]> draws = new ArrayList<>();
	private final Color color = new Color(Color.WHITE);
	private final Matrix4 projection = new Matrix4(), transform = new Matrix4();

	@Override
	public void draw(TextureRegion region, float x, float y, float width, float height)
	{draws.add(new float[] { x, y, width, height });}

	@Override
	public void draw(Texture texture, float x, float y, float width, float height)
	{draws.add(new float[] { x, y, width, height });}

	@Override public void begin() {}
	@Override public void end() {}
	@Override public void setColor(Color tint) {color.set(tint);}
	@Override public void setColor(float r, float g, float b, float a) {color.set(r, g, b, a);}
	@Override public Color getColor() {return color;}
	@Override public void setPackedColor(float packedColor) {Color.abgr8888ToColor(color, packedColor);}
	@Override public float getPackedColor() {return color.toFloatBits();}
	@Override public void draw(Texture texture, float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation, int srcX, int srcY, int srcWidth, int srcHeight, boolean flipX, boolean flipY) {}
	@Override public void draw(Texture texture, float x, float y, float width, float height, int srcX, int srcY, int srcWidth, int srcHeight, boolean flipX, boolean flipY) {}
	@Override public void draw(Texture texture, float x, float y, int srcX, int srcY, int srcWidth, int srcHeight) {}
	@Override public void draw(Texture texture, float x, float y, float width, float height, float u, float v, float u2, float v2) {}
	@Override public void draw(Texture texture, float x, float y) {}
	@Override public void draw(Texture texture, float[] spriteVertices, int offset, int count) {}
	@Override public void draw(TextureRegion region, float x, float y) {}
	@Override public void draw(TextureRegion region, float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation) {}
	@Override public void draw(TextureRegion region, float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation, boolean clockwise) {}
	@Override public void draw(TextureRegion region, float width, float height, Affine2 transform) {}
	@Override public void flush() {}
	@Override public void disableBlending() {}
	@Override public void enableBlending() {}
	@Override public void setBlendFunction(int srcFunc, int dstFunc) {}
	@Override public void setBlendFunctionSeparate(int srcFuncColor, int dstFuncColor, int srcFuncAlpha, int dstFuncAlpha) {}
	@Override public int getBlendSrcFunc() {return 0;}
	@Override public int getBlendDstFunc() {return 0;}
	@Override public int getBlendSrcFuncAlpha() {return 0;}
	@Override public int getBlendDstFuncAlpha() {return 0;}
	@Override public Matrix4 getProjectionMatrix() {return projection;}
	@Override public Matrix4 getTransformMatrix() {return transform;}
	@Override public void setProjectionMatrix(Matrix4 matrix) {projection.set(matrix);}
	@Override public void setTransformMatrix(Matrix4 matrix) {transform.set(matrix);}
	@Override public void setShader(ShaderProgram shader) {}
	@Override public ShaderProgram getShader() {return null;}
	@Override public boolean isBlendingEnabled() {return false;}
	@Override public boolean isDrawing() {return false;}
	@Override public void dispose() {}
}
