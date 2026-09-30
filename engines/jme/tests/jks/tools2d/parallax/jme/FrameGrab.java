package jks.tools2d.parallax.jme;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;

import com.jme3.post.SceneProcessor;
import com.jme3.profile.AppProfiler;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Image;
import com.jme3.util.BufferUtils;

/**
 * Saves the frame as a PNG at the end of the viewport it is added to: add it to the GUI viewport, the last one drawn.
 * {@link #grab(File)} asks for the frame being drawn. jME's Screenshots.convertScreenShot expects BGRA, so the RGBA read
 * is converted here, and its alpha left out, as the comparison leaves it out of libGDX's stills.
 */
final class FrameGrab implements SceneProcessor
{
	private RenderManager renderManager;
	private ViewPort viewPort;
	private File pending;

	/** Saves the frame being drawn to {@code file}. */
	void grab(File file)
	{pending = file;}

	boolean isPending()
	{return pending != null;}

	@Override
	public void initialize(RenderManager rm, ViewPort vp)
	{
		renderManager = rm;
		viewPort = vp;
	}

	@Override
	public void postFrame(FrameBuffer out)
	{
		if (pending == null)
			return;
		int width = viewPort.getCamera().getWidth(), height = viewPort.getCamera().getHeight();
		ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
		renderManager.getRenderer().readFrameBufferWithFormat(out, pixels, Image.Format.RGBA8);
		// Rows come from the bottom up.
		BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < height; y++)
			for (int x = 0; x < width; x++)
			{
				int i = ((height - 1 - y) * width + x) * 4;
				rgb.setRGB(x, y, (pixels.get(i) & 0xFF) << 16 | (pixels.get(i + 1) & 0xFF) << 8 | pixels.get(i + 2) & 0xFF);
			}
		try
		{ImageIO.write(rgb, "png", pending);}
		catch (IOException e)
		{throw new RuntimeException(e);}
		pending = null;
	}

	@Override
	public void reshape(ViewPort vp, int w, int h)
	{}

	@Override
	public boolean isInitialized()
	{return renderManager != null;}

	@Override
	public void preFrame(float tpf)
	{}

	@Override
	public void postQueue(RenderQueue rq)
	{}

	@Override
	public void cleanup()
	{}

	@Override
	public void setProfiler(AppProfiler profiler)
	{}
}
