package jks.tools2d.parallax.jme;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.Matrix4;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Format;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.VertexBuffer.Usage;
import com.jme3.texture.Texture2D;
import com.jme3.util.BufferUtils;

/**
 * libGDX's {@link Batch}, drawn by jME: what lets {@link jks.tools2d.parallax.ParallaxPageReader} draw a page in a jME
 * scene unchanged. Between {@link #begin()} and {@link #end()} it collects the quads of
 * {@link #draw(TextureRegion, float, float, float, float)}, the only draw the reader calls, into one mesh per run of the
 * same texture, in draw order, as SpriteBatch flushes. The quads keep libGDX's packed vertex colour (8 bits a channel,
 * the alpha's lowest bit dropped) and its blending (source alpha, one minus source alpha).
 * <p>
 * Positions are in the reader's world units, mapped to pixels by {@link #setView}. The other draws, shaders and
 * matrices are not supported: the reader does not use them.
 */
public class JmeBatch implements Batch
{
	/** A mesh of quads sharing a texture, reused from frame to frame. */
	private static final class Run
	{
		final Geometry geometry;
		final Mesh mesh = new Mesh();
		float[] positions = new float[4 * 3 * 16], uvs = new float[4 * 2 * 16];
		byte[] colors = new byte[4 * 4 * 16];
		int quads, capacity;
		Texture2D texture;

		Run(int index)
		{
			geometry = new Geometry("parallax run " + index, mesh);
			geometry.setQueueBucket(Bucket.Gui);
		}
	}

	private final AssetManager assets;
	private final Node node;
	private final float z;
	private final List<Run> runs = new ArrayList<>();
	private final Map<Texture2D, Material> materials = new IdentityHashMap<>();
	private int used;
	private Run current;
	private boolean drawing;

	private final Color color = new Color(1, 1, 1, 1);
	private byte red = -1, green = -1, blue = -1, alpha = (byte) 0xFE;

	private float viewLeft, viewBottom, pixelsPerUnitX = 1, pixelsPerUnitY = 1;

	/** Draws under {@code node}, the runs from {@code z} up, one unit apart (a Gui bucket sorts on z). */
	public JmeBatch(AssetManager assets, Node node, float z)
	{
		this.assets = assets;
		this.node = node;
		this.z = z;
	}

	/** The world rectangle the screen shows, and the screen's size in pixels. */
	public void setView(float viewLeft, float viewBottom, float viewWidth, float viewHeight, int screenWidth, int screenHeight)
	{
		this.viewLeft = viewLeft;
		this.viewBottom = viewBottom;
		this.pixelsPerUnitX = screenWidth / viewWidth;
		this.pixelsPerUnitY = screenHeight / viewHeight;
	}

	@Override
	public void begin()
	{
		used = 0;
		current = null;
		drawing = true;
	}

	@Override
	public void end()
	{
		flush();
		for (int i = 0; i < runs.size(); i++)
		{
			Run run = runs.get(i);
			if (i < used)
			{
				upload(run);
				if (run.geometry.getParent() != node)
					node.attachChild(run.geometry);
			}
			else
				run.geometry.removeFromParent();
		}
		drawing = false;
	}

	@Override
	public void draw(TextureRegion region, float x, float y, float width, float height)
	{
		Texture2D texture = ((JmeAtlas.PageTexture) region.getTexture()).texture;
		if (current == null || current.texture != texture)
			current = nextRun(texture);

		Run run = current;
		ensureCapacity(run, run.quads + 1);
		float left = (x - viewLeft) * pixelsPerUnitX, bottom = (y - viewBottom) * pixelsPerUnitY;
		float right = (x + width - viewLeft) * pixelsPerUnitX, top = (y + height - viewBottom) * pixelsPerUnitY;
		float u = region.getU(), v = region.getV(), u2 = region.getU2(), v2 = region.getV2();
		// SpriteBatch's order: bottom left, top left, top right, bottom right.
		vertex(run, run.quads * 4, left, bottom, u, v2);
		vertex(run, run.quads * 4 + 1, left, top, u, v);
		vertex(run, run.quads * 4 + 2, right, top, u2, v);
		vertex(run, run.quads * 4 + 3, right, bottom, u2, v2);
		run.quads++;
	}

	private void vertex(Run run, int index, float x, float y, float u, float v)
	{
		run.positions[index * 3] = x;
		run.positions[index * 3 + 1] = y;
		run.positions[index * 3 + 2] = 0;
		run.uvs[index * 2] = u;
		run.uvs[index * 2 + 1] = v;
		run.colors[index * 4] = red;
		run.colors[index * 4 + 1] = green;
		run.colors[index * 4 + 2] = blue;
		run.colors[index * 4 + 3] = alpha;
	}

	private Run nextRun(Texture2D texture)
	{
		if (used == runs.size())
			runs.add(new Run(runs.size()));
		Run run = runs.get(used);
		run.geometry.setLocalTranslation(0, 0, z + used);
		used++;
		run.quads = 0;
		if (run.texture != texture)
		{
			run.texture = texture;
			run.geometry.setMaterial(materials.computeIfAbsent(texture, this::material));
		}
		return run;
	}

	private Material material(Texture2D texture)
	{
		Material material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
		material.setTexture("ColorMap", texture);
		material.setBoolean("VertexColor", true);
		material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
		// A flipped layer is drawn with a negative width or height: its quad faces away.
		material.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
		material.getAdditionalRenderState().setDepthTest(false);
		material.getAdditionalRenderState().setDepthWrite(false);
		return material;
	}

	private static void ensureCapacity(Run run, int quads)
	{
		if (quads * 4 * 3 <= run.positions.length)
			return;
		int size = Math.max(quads, run.positions.length / 6);
		run.positions = java.util.Arrays.copyOf(run.positions, size * 4 * 3);
		run.uvs = java.util.Arrays.copyOf(run.uvs, size * 4 * 2);
		run.colors = java.util.Arrays.copyOf(run.colors, size * 4 * 4);
	}

	/** Copies a run's quads into its mesh, growing its buffers only when it holds more quads than ever before. */
	private static void upload(Run run)
	{
		Mesh mesh = run.mesh;
		if (run.quads > run.capacity)
		{
			run.capacity = Math.max(run.quads, run.capacity * 2);
			int vertices = run.capacity * 4;
			mesh.setBuffer(Type.Position, 3, BufferUtils.createFloatBuffer(vertices * 3));
			mesh.setBuffer(Type.TexCoord, 2, BufferUtils.createFloatBuffer(vertices * 2));
			VertexBuffer colors = new VertexBuffer(Type.Color);
			colors.setupData(Usage.Stream, 4, Format.UnsignedByte, BufferUtils.createByteBuffer(vertices * 4));
			colors.setNormalized(true);
			mesh.clearBuffer(Type.Color);
			mesh.setBuffer(colors);
			IntBuffer indices = BufferUtils.createIntBuffer(run.capacity * 6);
			for (int q = 0; q < run.capacity; q++)
				indices.put(q * 4).put(q * 4 + 1).put(q * 4 + 2).put(q * 4 + 2).put(q * 4 + 3).put(q * 4);
			indices.flip();
			mesh.setBuffer(Type.Index, 3, indices);
			for (Type type : new Type[] { Type.Position, Type.TexCoord })
				mesh.getBuffer(type).setUsage(Usage.Stream);
		}

		FloatBuffer positions = (FloatBuffer) mesh.getBuffer(Type.Position).getData();
		positions.clear();
		positions.put(run.positions, 0, run.quads * 4 * 3).flip();
		mesh.getBuffer(Type.Position).updateData(positions);
		FloatBuffer uvs = (FloatBuffer) mesh.getBuffer(Type.TexCoord).getData();
		uvs.clear();
		uvs.put(run.uvs, 0, run.quads * 4 * 2).flip();
		mesh.getBuffer(Type.TexCoord).updateData(uvs);
		ByteBuffer colors = (ByteBuffer) mesh.getBuffer(Type.Color).getData();
		colors.clear();
		colors.put(run.colors, 0, run.quads * 4 * 4).flip();
		mesh.getBuffer(Type.Color).updateData(colors);
		IntBuffer indices = (IntBuffer) mesh.getBuffer(Type.Index).getData();
		indices.limit(run.quads * 6);
		mesh.getBuffer(Type.Index).updateData(indices);
		mesh.updateCounts();
		mesh.updateBound();
		run.geometry.updateModelBound();
	}

	@Override
	public void setColor(Color tint)
	{setColor(tint.r, tint.g, tint.b, tint.a);}

	/** Quantised as {@link Color#toFloatBits()} does, for SpriteBatch's vertex colour. */
	@Override
	public void setColor(float r, float g, float b, float a)
	{
		color.set(r, g, b, a);
		red = (byte) (int) (255 * r);
		green = (byte) (int) (255 * g);
		blue = (byte) (int) (255 * b);
		alpha = (byte) ((int) (255 * a) & 0xFE);
	}

	@Override
	public Color getColor()
	{return color;}

	@Override
	public void flush()
	{}

	@Override
	public boolean isDrawing()
	{return drawing;}

	@Override
	public boolean isBlendingEnabled()
	{return true;}

	@Override
	public void enableBlending()
	{}

	@Override
	public void dispose()
	{
		for (Run run : runs)
			run.geometry.removeFromParent();
		runs.clear();
		materials.clear();
	}

	// What the reader never calls.

	private static UnsupportedOperationException unsupported()
	{return new UnsupportedOperationException("JmeBatch only draws what ParallaxPageReader draws");}

	@Override
	public void setPackedColor(float packedColor)
	{throw unsupported();}

	@Override
	public float getPackedColor()
	{throw unsupported();}

	@Override
	public void draw(Texture texture, float x, float y, float originX, float originY, float width, float height, float scaleX,
			float scaleY, float rotation, int srcX, int srcY, int srcWidth, int srcHeight, boolean flipX, boolean flipY)
	{throw unsupported();}

	@Override
	public void draw(Texture texture, float x, float y, float width, float height, int srcX, int srcY, int srcWidth,
			int srcHeight, boolean flipX, boolean flipY)
	{throw unsupported();}

	@Override
	public void draw(Texture texture, float x, float y, int srcX, int srcY, int srcWidth, int srcHeight)
	{throw unsupported();}

	@Override
	public void draw(Texture texture, float x, float y, float width, float height, float u, float v, float u2, float v2)
	{throw unsupported();}

	@Override
	public void draw(Texture texture, float x, float y)
	{throw unsupported();}

	@Override
	public void draw(Texture texture, float x, float y, float width, float height)
	{throw unsupported();}

	@Override
	public void draw(Texture texture, float[] spriteVertices, int offset, int count)
	{throw unsupported();}

	@Override
	public void draw(TextureRegion region, float x, float y)
	{draw(region, x, y, region.getRegionWidth(), region.getRegionHeight());}

	@Override
	public void draw(TextureRegion region, float x, float y, float originX, float originY, float width, float height,
			float scaleX, float scaleY, float rotation)
	{throw unsupported();}

	@Override
	public void draw(TextureRegion region, float x, float y, float originX, float originY, float width, float height,
			float scaleX, float scaleY, float rotation, boolean clockwise)
	{throw unsupported();}

	@Override
	public void draw(TextureRegion region, float width, float height, Affine2 transform)
	{throw unsupported();}

	@Override
	public void disableBlending()
	{throw unsupported();}

	@Override
	public void setBlendFunction(int srcFunc, int dstFunc)
	{throw unsupported();}

	@Override
	public void setBlendFunctionSeparate(int srcFuncColor, int dstFuncColor, int srcFuncAlpha, int dstFuncAlpha)
	{throw unsupported();}

	@Override
	public int getBlendSrcFunc()
	{throw unsupported();}

	@Override
	public int getBlendDstFunc()
	{throw unsupported();}

	@Override
	public int getBlendSrcFuncAlpha()
	{throw unsupported();}

	@Override
	public int getBlendDstFuncAlpha()
	{throw unsupported();}

	@Override
	public Matrix4 getProjectionMatrix()
	{throw unsupported();}

	@Override
	public Matrix4 getTransformMatrix()
	{throw unsupported();}

	@Override
	public void setProjectionMatrix(Matrix4 projection)
	{throw unsupported();}

	@Override
	public void setTransformMatrix(Matrix4 transform)
	{throw unsupported();}

	@Override
	public void setShader(ShaderProgram shader)
	{throw unsupported();}

	@Override
	public ShaderProgram getShader()
	{throw unsupported();}
}
