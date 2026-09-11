package jks.tools2d.parallax.editor.vue.edition.utils;

import java.nio.file.Paths;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import jks.tools2d.filewatch.FileWatchService;

/** A loose PNG used by the project, hot-reloaded in every layer when the file is saved from an image editor. */
public class WatchedImage
{
	private final String path;
	private TextureRegion region;
	private final FileWatchService.Watch watch;
	/** A reload may already be queued on the render thread when the project closes. */
	private boolean cancelled;

	public WatchedImage(String path, TextureRegion region)
	{
		this.path = path;
		this.region = region;
		this.watch = FileWatchService.watch(Paths.get(path), this::reload);
	}

	private void reload()
	{
		if (cancelled)
			return;

		TextureRegion reloaded = Utils_Texture.getTextureRegionFromPath(path);
		if (reloaded == null)
		{
			Gdx.app.error("WatchedImage", "Could not reload " + path);
			return;
		}

		Utils_Texture.replaceTexture(region, reloaded, true);
		region = reloaded;
	}

	public TextureRegion getRegion()
	{return region;}

	public void cancel()
	{
		cancelled = true;
		watch.cancel();
	}
}
