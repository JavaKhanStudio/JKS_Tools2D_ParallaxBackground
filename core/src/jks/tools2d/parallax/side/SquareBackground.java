package jks.tools2d.parallax.side;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * Vertical gradient drawn behind the layers, covering the top or the bottom part of the screen (in screen pixels).
 * <p>
 * {@code screenPercentage} is the part of the screen left <em>uncovered</em>: 0 fills the whole screen, 0.5 half of
 * it, 1 nothing. This is the value saved as {@code topHalfSize}/{@code bottomHalfSize} in page files.
 */
public class SquareBackground
{
	public Color topColor;
	public Color bottomColor;
	public boolean visible = true;

	private final boolean isTop;
	private float screenPercentage;
	private float posY, width, height;

	private final Color topFrom = new Color(), bottomFrom = new Color();
	private final Color topTarget = new Color(), bottomTarget = new Color();
	private float transfertDuration, transfertElapsed;
	private boolean inTransfert;

	public SquareBackground(Color top, Color bottom, float screenPercentage, boolean isTop)
	{
		this.topColor = top;
		this.bottomColor = bottom;
		this.isTop = isTop;
		this.screenPercentage = screenPercentage;
		resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
	}

	public void resize(float screenWidth, float screenHeight)
	{
		width = screenWidth;
		float uncovered = screenHeight * screenPercentage;
		height = screenHeight - uncovered;
		posY = isTop ? uncovered : 0;
	}

	public float getScreenPercentage()
	{return screenPercentage;}

	public void setScreenPercentage(float screenPercentage)
	{
		this.screenPercentage = screenPercentage;
		resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
	}

	public boolean isTop()
	{return isTop;}

	public float getHeight()
	{return height;}

	/** Fades both gradient colors (alpha included) toward the given ones over {@code inXSecondes}. */
	public void transfertInto(Color topTransfert, Color bottomTransfert, float inXSecondes)
	{
		if (topTransfert == null || bottomTransfert == null)
			return;

		topFrom.set(topColor);
		bottomFrom.set(bottomColor);
		topTarget.set(topTransfert);
		bottomTarget.set(bottomTransfert);
		transfertElapsed = 0;
		transfertDuration = inXSecondes;
		inTransfert = true;

		if (inXSecondes <= 0)
			act(0);
	}

	public void act(float delta)
	{
		if (!inTransfert)
			return;

		transfertElapsed += delta;
		float progress = transfertDuration > 0 ? Math.min(1, transfertElapsed / transfertDuration) : 1;
		topColor.set(topFrom).lerp(topTarget, progress);
		bottomColor.set(bottomFrom).lerp(bottomTarget, progress);

		if (progress >= 1)
			inTransfert = false;
	}

	public void draw(ShapeRenderer render)
	{
		if (visible && height > 0)
			render.rect(0, posY, width, height, bottomColor, bottomColor, topColor, topColor);
	}
}
