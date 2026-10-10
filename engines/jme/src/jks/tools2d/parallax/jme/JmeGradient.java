package jks.tools2d.parallax.jme;

import com.badlogic.gdx.graphics.Color;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Format;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.VertexBuffer.Usage;
import com.jme3.util.BufferUtils;

import jks.tools2d.parallax.TransfertStyle;

/**
 * core's {@code SquareBackground}, drawn by jME: a vertical gradient over the top or the bottom part of the screen, in
 * pixels. {@code screenPercentage} is the part left uncovered. Drawn opaque, as libGDX's ShapeRenderer draws it (no
 * blending, so the colours' alpha is ignored), with the colour packed to 8 bits a channel as it does. SquareBackground
 * itself cannot be used: it reads the screen size from libGDX's {@code Gdx.graphics}.
 */
final class JmeGradient
{
	final Color topColor = new Color(), bottomColor = new Color();
	private final boolean isTop;
	private final float screenPercentage;
	private final Geometry geometry;
	private final Mesh mesh = new Mesh();

	private final Color topFrom = new Color(), bottomFrom = new Color();
	private final Color topTarget = new Color(), bottomTarget = new Color();
	private float transfertDuration, transfertElapsed;
	private boolean inTransfert;
	private TransfertStyle style = TransfertStyle.FADE;

	JmeGradient(AssetManager assets, Color top, Color bottom, float screenPercentage, boolean isTop, float z)
	{
		topColor.set(top);
		bottomColor.set(bottom);
		this.screenPercentage = screenPercentage;
		this.isTop = isTop;

		mesh.setBuffer(Type.Position, 3, BufferUtils.createFloatBuffer(4 * 3));
		VertexBuffer colors = new VertexBuffer(Type.Color);
		colors.setupData(Usage.Dynamic, 4, Format.UnsignedByte, BufferUtils.createByteBuffer(4 * 4));
		colors.setNormalized(true);
		mesh.setBuffer(colors);
		// ShapeRenderer's two triangles: bottom left, bottom right, top right, then top right, top left, bottom left.
		mesh.setBuffer(Type.Index, 3, new short[] { 0, 1, 2, 2, 3, 0 });

		Material material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
		material.setBoolean("VertexColor", true);
		material.getAdditionalRenderState().setBlendMode(BlendMode.Off);
		material.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
		material.getAdditionalRenderState().setDepthTest(false);
		material.getAdditionalRenderState().setDepthWrite(false);
		geometry = new Geometry(isTop ? "parallax top gradient" : "parallax bottom gradient", mesh);
		geometry.setMaterial(material);
		geometry.setQueueBucket(Bucket.Gui);
		geometry.setLocalTranslation(0, 0, z);
	}

	Geometry geometry()
	{return geometry;}

	/** Fades both colours toward these over {@code seconds} as {@code style} goes, as SquareBackground.transfertInto. */
	void transfertInto(Color top, Color bottom, float seconds, TransfertStyle style)
	{
		this.style = style == null ? TransfertStyle.FADE : style;
		topFrom.set(topColor);
		bottomFrom.set(bottomColor);
		topTarget.set(top);
		bottomTarget.set(bottom);
		transfertElapsed = 0;
		transfertDuration = seconds;
		inTransfert = true;
		if (seconds <= 0)
			act(0);
	}

	void act(float delta)
	{
		if (!inTransfert)
			return;
		transfertElapsed += delta;
		float progress = transfertDuration > 0 ? Math.min(1, transfertElapsed / transfertDuration) : 1;
		style.gradient(topFrom, topTarget, progress, topColor);
		style.gradient(bottomFrom, bottomTarget, progress, bottomColor);
		if (progress >= 1)
			inTransfert = false;
	}

	/** Places the quad on a screen of that size, with the current colours. */
	void update(int screenWidth, int screenHeight)
	{
		float uncovered = screenHeight * screenPercentage;
		float height = screenHeight - uncovered;
		float bottom = isTop ? uncovered : 0;
		geometry.setCullHint(height > 0 ? Geometry.CullHint.Inherit : Geometry.CullHint.Always);

		java.nio.FloatBuffer positions = (java.nio.FloatBuffer) mesh.getBuffer(Type.Position).getData();
		positions.clear();
		positions.put(0).put(bottom).put(0);
		positions.put(screenWidth).put(bottom).put(0);
		positions.put(screenWidth).put(bottom + height).put(0);
		positions.put(0).put(bottom + height).put(0);
		positions.flip();
		mesh.getBuffer(Type.Position).updateData(positions);

		java.nio.ByteBuffer colors = (java.nio.ByteBuffer) mesh.getBuffer(Type.Color).getData();
		colors.clear();
		put(colors, bottomColor);
		put(colors, bottomColor);
		put(colors, topColor);
		put(colors, topColor);
		colors.flip();
		mesh.getBuffer(Type.Color).updateData(colors);
		mesh.updateBound();
		geometry.updateModelBound();
	}

	/** As Color.toFloatBits packs it: 8 bits a channel, truncated. */
	private static void put(java.nio.ByteBuffer colors, Color color)
	{
		colors.put((byte) (int) (255 * color.r)).put((byte) (int) (255 * color.g)).put((byte) (int) (255 * color.b))
				.put((byte) ((int) (255 * color.a) & 0xFE));
	}
}
