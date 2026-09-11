package jks.tools2d.parallax.demo;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;

import jks.tools2d.parallax.heart.Gvars_Parallax;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.pages.Utils_Page;
import jks.tools2d.parallax.pages.WholePage_Model;

/** Minimal game-side usage of the library: two .plax pages made with the editor, cross-faded on demand. */
public class ParallaxDemo extends ApplicationAdapter
{
	private static final float TRANSFER_SECONDS = 3;
	private static final float MANUAL_SPEED = 400;
	private static final Color NIGHT_TINT = new Color(0.45f, 0.5f, 0.85f, 1);

	private Parallax_Heart heart;
	private WholePage_Model winter, spring;
	private boolean showingWinter = true, night;

	private SpriteBatch hudBatch;
	private BitmapFont font;

	public static void main(String[] args)
	{
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setTitle("Parallax demo");
		config.setWindowedMode(1280, 720);
		config.useVsync(true);
		new Lwjgl3Application(new ParallaxDemo(), config);
	}

	@Override
	public void create()
	{
		winter = Utils_Page.loadPage("hiver/Hiver.plax");
		spring = Utils_Page.loadPage("printemps/Printemps.plax");

		heart = new Parallax_Heart();
		heart.setPage(winter);
		heart.screenSpeedConstantX = 60;

		hudBatch = new SpriteBatch();
		font = new BitmapFont();

		Gdx.input.setInputProcessor(new InputAdapter()
		{
			@Override
			public boolean keyDown(int keycode)
			{
				switch (keycode)
				{
					case Keys.SPACE:
						showingWinter = !showingWinter;
						heart.transfertIntoPage(showingWinter ? winter : spring, TRANSFER_SECONDS);
						return true;
					case Keys.N:
						night = !night;
						heart.parallaxReader.addColorTransfert(night ? NIGHT_TINT : Color.WHITE, TRANSFER_SECONDS);
						return true;
					case Keys.R:
						heart.parallaxReader.resetPositions();
						return true;
					default:
						return false;
				}
			}
		});
	}

	@Override
	public void render()
	{
		float delta = Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f);

		if (Gdx.input.isKeyPressed(Keys.LEFT))
			heart.screenSpeedConsumableX = -MANUAL_SPEED;
		if (Gdx.input.isKeyPressed(Keys.RIGHT))
			heart.screenSpeedConsumableX = MANUAL_SPEED;

		ScreenUtils.clear(Color.BLACK);
		heart.act(delta);
		heart.render();

		hudBatch.begin();
		font.draw(hudBatch, "SPACE: winter/spring   N: night tint   LEFT/RIGHT: scroll   R: reset   " + Gdx.graphics.getFramesPerSecond() + " fps",
				10, Gdx.graphics.getHeight() - 10);
		hudBatch.end();
	}

	@Override
	public void resize(int width, int height)
	{
		heart.resize(width, height);
		hudBatch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
	}

	@Override
	public void dispose()
	{
		heart.dispose();
		hudBatch.dispose();
		font.dispose();
		// Both atlases were loaded as internal files, so the shared AssetManager owns them.
		Gvars_Parallax.getManager().dispose();
	}
}
