package jks.tools2d.parallax.side;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import jks.tools2d.parallax.TransfertStyle;

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
	private TransfertStyle style = TransfertStyle.FADE;

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
	{transfertInto(topTransfert, bottomTransfert, inXSecondes, TransfertStyle.FADE);}

	/**
	 * {@link #transfertInto(Color, Color, float)} as the layers' {@code style} goes ({@link TransfertStyle#gradient}):
	 * through its colour for {@link TransfertStyle#throughColor} and {@link TransfertStyle#fogCreep}, straight for every
	 * other. null is FADE.
	 */
	public void transfertInto(Color topTransfert, Color bottomTransfert, float inXSecondes, TransfertStyle style)
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
		this.style = style == null ? TransfertStyle.FADE : style;

		if (inXSecondes <= 0)
			act(0);
	}

	public void act(float delta)
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

	public void draw(ShapeRenderer render)
	{
		if (visible && height > 0)
			render.rect(0, posY, width, height, bottomColor, bottomColor, topColor, topColor);
	}
}
