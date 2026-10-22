package jks.tools2d.parallax.jme;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.NumberUtils;
import com.jme3.asset.AssetManager;
import com.jme3.material.MatParamTexture;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendFunc;
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
 * {@link #draw(TextureRegion, float, float, float, float)}, the reader's draw, of
 * {@link #draw(Texture, float[], int, int)}, a page round a planet's (r347), and of the turned region draw a game's hook
 * stands a thing on a planet with, into one mesh per run of the same texture, in draw order, as SpriteBatch flushes.
 * The quads keep libGDX's packed vertex colour (8 bits a channel, the alpha's lowest bit dropped) and its blending
 * (source alpha, one minus source alpha), or the one {@link #setBlendFunction} sets, as a PARTICLES layer's emitters
 * do (additive, premultiplied), in runs of its own.
 * <p>
 * Positions are in the reader's world units, mapped to pixels by {@link #setView}. A SHADER layer's run is drawn with
 * the material {@link #setEffect} names, which {@link JmeLayerEffects} sets around its tiles. The other draws, libGDX
 * shaders and matrices are not supported: the reader does not use them.
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
		/** The blend function its quads were drawn with, {@link #blendKey()}. */
		long blend;
		Material material;

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
	/** Each texture's materials, one per blend function ({@link #blendKey()}). */
	private final Map<Texture2D, Map<Long, Material>> materials = new IdentityHashMap<>();
	private int used;
	private Run current;
	private boolean drawing;
	/** The material the next draws are drawn with, null: their texture's own (Unshaded). */
	private Material effect;

	private final Color color = new Color(1, 1, 1, 1);
	private byte red = -1, green = -1, blue = -1, alpha = (byte) 0xFE;
	/** The blend function the next quads are drawn with, as GL20 factors, colour then alpha: SpriteBatch's until set. */
	private int blendSrc = GL20.GL_SRC_ALPHA, blendDst = GL20.GL_ONE_MINUS_SRC_ALPHA;
	private int blendSrcAlpha = GL20.GL_SRC_ALPHA, blendDstAlpha = GL20.GL_ONE_MINUS_SRC_ALPHA;

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

	/**
	 * Draws what follows with {@code material}, its ColorMap set to each run's texture, in runs of their own; null goes
	 * back to each texture's own material. A SHADER layer's run: the reader's flush around it.
	 */
	public void setEffect(Material material)
	{
		effect = material;
		current = null;
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
		Run run = quad(region.getTexture());
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

	/**
	 * Quads, SpriteBatch's 20 floats each (x, y, packed colour, u, v; bottom left, top left, top right, bottom right): a
	 * strip of a page round a planet, its corners where the planet puts them.
	 */
	@Override
	public void draw(Texture texture, float[] spriteVertices, int offset, int count)
	{
		for (int at = offset; at + 20 <= offset + count; at += 20)
		{
			Run run = quad(texture);
			for (int corner = 0; corner < 4; corner++)
			{
				int i = at + corner * 5, index = run.quads * 4 + corner;
				vertex(run, index, (spriteVertices[i] - viewLeft) * pixelsPerUnitX, (spriteVertices[i + 1] - viewBottom) * pixelsPerUnitY,
						spriteVertices[i + 3], spriteVertices[i + 4]);
				int abgr = NumberUtils.floatToIntColor(spriteVertices[i + 2]);
				run.colors[index * 4] = (byte) abgr;
				run.colors[index * 4 + 1] = (byte) (abgr >>> 8);
				run.colors[index * 4 + 2] = (byte) (abgr >>> 16);
				run.colors[index * 4 + 3] = (byte) (abgr >>> 24);
			}
			run.quads++;
		}
	}

	/**
	 * The region turned by {@code rotation} degrees counterclockwise about {@code (x + originX, y + originY)} and scaled
	 * about it, as SpriteBatch draws it: what a game's hook stands a thing with on a page round a planet.
	 */
	@Override
	public void draw(TextureRegion region, float x, float y, float originX, float originY, float width, float height,
			float scaleX, float scaleY, float rotation)
	{
		Run run = quad(region.getTexture());
		float ox = x + originX, oy = y + originY;
		float left = -originX * scaleX, bottom = -originY * scaleY;
		float right = (width - originX) * scaleX, top = (height - originY) * scaleY;
		float cos = MathUtils.cosDeg(rotation), sin = MathUtils.sinDeg(rotation);
		float u = region.getU(), v = region.getV(), u2 = region.getU2(), v2 = region.getV2();
		turned(run, run.quads * 4, ox, oy, left, bottom, cos, sin, u, v2);
		turned(run, run.quads * 4 + 1, ox, oy, left, top, cos, sin, u, v);
		turned(run, run.quads * 4 + 2, ox, oy, right, top, cos, sin, u2, v);
		turned(run, run.quads * 4 + 3, ox, oy, right, bottom, cos, sin, u2, v2);
		run.quads++;
	}

	private void turned(Run run, int index, float ox, float oy, float dx, float dy, float cos, float sin, float u, float v)
	{
		vertex(run, index, (ox + dx * cos - dy * sin - viewLeft) * pixelsPerUnitX, (oy + dx * sin + dy * cos - viewBottom) * pixelsPerUnitY,
				u, v);
	}

	/** The run the next quad of {@code texture} goes in, room made for it. */
	private Run quad(Texture texture)
	{
		Texture2D page = ((JmeAtlas.PageTexture) texture).texture;
		if (current == null || current.texture != page || current.blend != blendKey())
			current = nextRun(page);
		ensureCapacity(current, current.quads + 1);
		return current;
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
		run.texture = texture;
		run.blend = blendKey();
		Material material = effect != null ? effect
				: materials.computeIfAbsent(texture, t -> new HashMap<>()).computeIfAbsent(run.blend, b -> material(texture));
		if (effect != null)
		{
			MatParamTexture map = effect.getTextureParam("ColorMap");
			if (map == null || map.getTextureValue() != texture)
				effect.setTexture("ColorMap", texture);
		}
		if (run.material != material)
		{
			run.material = material;
			run.geometry.setMaterial(material);
		}
		return run;
	}

	private Material material(Texture2D texture)
	{
		Material material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
		material.setTexture("ColorMap", texture);
		material.setBoolean("VertexColor", true);
		renderAsRuns(material);
		if (blendKey() != DEFAULT_BLEND)
		{
			material.getAdditionalRenderState().setBlendMode(BlendMode.Custom);
			material.getAdditionalRenderState().setCustomBlendFactors(blendFunc(blendSrc), blendFunc(blendDst),
					blendFunc(blendSrcAlpha), blendFunc(blendDstAlpha));
		}
		return material;
	}

	/** SpriteBatch's blend function, {@link BlendMode#Alpha}'s, as {@link #blendKey()} packs it. */
	private static final long DEFAULT_BLEND = blendKey(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_SRC_ALPHA,
			GL20.GL_ONE_MINUS_SRC_ALPHA);

	/** The blend function the next quads are drawn with, its four factors in one number. */
	private long blendKey()
	{return blendKey(blendSrc, blendDst, blendSrcAlpha, blendDstAlpha);}

	private static long blendKey(int src, int dst, int srcAlpha, int dstAlpha)
	{return (long) src << 48 | (long) dst << 32 | (long) srcAlpha << 16 | dstAlpha;}

	/** jME's name for a GL20 blend factor. */
	private static BlendFunc blendFunc(int factor)
	{
		switch (factor)
		{
			case GL20.GL_ZERO:
				return BlendFunc.Zero;
			case GL20.GL_ONE:
				return BlendFunc.One;
			case GL20.GL_SRC_COLOR:
				return BlendFunc.Src_Color;
			case GL20.GL_ONE_MINUS_SRC_COLOR:
				return BlendFunc.One_Minus_Src_Color;
			case GL20.GL_DST_COLOR:
				return BlendFunc.Dst_Color;
			case GL20.GL_ONE_MINUS_DST_COLOR:
				return BlendFunc.One_Minus_Dst_Color;
			case GL20.GL_SRC_ALPHA:
				return BlendFunc.Src_Alpha;
			case GL20.GL_ONE_MINUS_SRC_ALPHA:
				return BlendFunc.One_Minus_Src_Alpha;
			case GL20.GL_DST_ALPHA:
				return BlendFunc.Dst_Alpha;
			case GL20.GL_ONE_MINUS_DST_ALPHA:
				return BlendFunc.One_Minus_Dst_Alpha;
			case GL20.GL_SRC_ALPHA_SATURATE:
				return BlendFunc.Src_Alpha_Saturate;
			default:
				throw new UnsupportedOperationException("JmeBatch blends with no GL factor 0x" + Integer.toHexString(factor));
		}
	}

	/** Blends as SpriteBatch, draws both faces, ignores depth: what every run's material is set to. */
	static void renderAsRuns(Material material)
	{
		material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
		// A flipped layer is drawn with a negative width or height: its quad faces away.
		material.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
		material.getAdditionalRenderState().setDepthTest(false);
		material.getAdditionalRenderState().setDepthWrite(false);
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

	/**
	 * Draws what follows blended so, colour and alpha alike, as glBlendFunc: a PARTICLES layer's emitters set it (normal,
	 * additive or premultiplied, as their .p asks), and the reader sets the one before back after them.
	 */
	@Override
	public void setBlendFunction(int srcFunc, int dstFunc)
	{setBlendFunctionSeparate(srcFunc, dstFunc, srcFunc, dstFunc);}

	@Override
	public void setBlendFunctionSeparate(int srcFuncColor, int dstFuncColor, int srcFuncAlpha, int dstFuncAlpha)
	{
		blendSrc = srcFuncColor;
		blendDst = dstFuncColor;
		blendSrcAlpha = srcFuncAlpha;
		blendDstAlpha = dstFuncAlpha;
	}

	@Override
	public int getBlendSrcFunc()
	{return blendSrc;}

	@Override
	public int getBlendDstFunc()
	{return blendDst;}

	@Override
	public int getBlendSrcFuncAlpha()
	{return blendSrcAlpha;}

	@Override
	public int getBlendDstFuncAlpha()
	{return blendDstAlpha;}

	// What the reader never calls.

	private static UnsupportedOperationException unsupported()
	{return new UnsupportedOperationException("JmeBatch only draws what ParallaxPageReader draws");}

	@Override
	public void setPackedColor(float packedColor)
	{throw unsupported();}

	/** The colour as {@link Color#toFloatBits()} packs it: what a planet's quads carry ({@link #setColor}'s bytes). */
	@Override
	public float getPackedColor()
	{return NumberUtils.intToFloatColor((alpha & 0xFF) << 24 | (blue & 0xFF) << 16 | (green & 0xFF) << 8 | red & 0xFF);}

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
	public void draw(TextureRegion region, float x, float y)
	{draw(region, x, y, region.getRegionWidth(), region.getRegionHeight());}

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
