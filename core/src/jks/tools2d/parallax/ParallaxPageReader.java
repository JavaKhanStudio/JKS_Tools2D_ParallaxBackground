package jks.tools2d.parallax;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;

import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * Scrolls and draws the layers of a page, tiling them across the camera view, and cross-fades into another page
 * ({@link #addLayersTransfert}) or tint ({@link #addColorTransfert}).
 * <p>
 * Layers are screen-anchored: they are laid out from the left/bottom edge of the camera view, so moving the game
 * camera does not drag the background away.
 */
public class ParallaxPageReader
{
	public ArrayList<ParallaxLayer> layers = new ArrayList<>();
	public ArrayList<ParallaxLayer> transferLayers = new ArrayList<>();

	public Enum_TransfertType transfertType = Enum_TransfertType.NONE;

	private boolean repeatOnX, repeatOnY;
	private float drawingHeight;

	/** Opacity of the incoming page (0 → 1) and of the outgoing one (1 → 0) during a page transfer. */
	private float newLayerAlpha = 0, oldLayerAlpha = 1;
	private float newLayerFadeSpeed, oldLayerFadeSpeed;

	private final Color tint = new Color(Color.WHITE);
	private final Color tintFrom = new Color(Color.WHITE);
	private final Color tintTo = new Color(Color.WHITE);
	private float tintDuration, tintElapsed;

	// Visible area of the camera, refreshed each draw.
	private float viewLeft, viewBottom, viewWidth, viewHeight;

	public void addLayers(List<ParallaxLayer> newLayers)
	{layers.addAll(newLayers);}

	/** Cross-fades from the current layers into the layers of {@code pageModel} over {@code inXSecondes}. */
	public void addLayersTransfert(WholePage_Model pageModel, float inXSecondes)
	{
		resetTransfert();

		List<ParallaxLayer> newLayers = pageModel.getDrawing();
		if (newLayers == null || newLayers.isEmpty())
			return;

		// Transferring into the page on screen: its layers must not be moved and drawn twice per frame.
		for (ParallaxLayer layer : newLayers)
			transferLayers.add(layers.contains(layer) ? layer.clone() : layer);
		syncTransferPositions();

		if (inXSecondes <= 0)
		{
			layers = transferLayers;
			resetTransfert();
			return;
		}

		transfertType = Enum_TransfertType.EACH_FRAME;
		newLayerFadeSpeed = oldLayerFadeSpeed = 1 / inXSecondes;
	}

	/** Tints every layer toward {@code color} over {@code inXSecondes}. */
	public void addColorTransfert(Color color, float inXSecondes)
	{
		if (color == null)
			return;

		tintFrom.set(tint);
		tintTo.set(color);
		tintElapsed = 0;
		tintDuration = inXSecondes;
		if (inXSecondes <= 0)
			tint.set(color);
	}

	/**
	 * Pages are stacked back to front and matched from the front: when the incoming page has more layers, its extra
	 * back layers have no outgoing counterpart. Matched layers keep their own placement but take the distance their
	 * counterpart has scrolled, so the fade is seamless.
	 */
	private void syncTransferPositions()
	{
		int total = Math.max(layers.size(), transferLayers.size());
		int oldOffset = total - layers.size();
		int newOffset = total - transferLayers.size();

		for (int slot = Math.max(oldOffset, newOffset); slot < total; slot++)
		{
			ParallaxLayer from = layers.get(slot - oldOffset);
			ParallaxLayer to = transferLayers.get(slot - newOffset);
			to.setScrollX(from.getScrollX());
			to.setScrollY(from.getScrollY());
		}
	}

	public void draw(OrthographicCamera worldCamera, Batch batch)
	{
		viewWidth = worldCamera.viewportWidth * worldCamera.zoom;
		viewHeight = worldCamera.viewportHeight * worldCamera.zoom;
		viewLeft = worldCamera.position.x - viewWidth / 2;
		viewBottom = worldCamera.position.y - viewHeight / 2;

		if (transferLayers.isEmpty())
		{
			setBatchColor(batch, 1);
			for (int i = 0, n = layers.size(); i < n; i++)
				drawLayer(layers.get(i), batch);
		}
		else
		{
			int total = Math.max(layers.size(), transferLayers.size());
			int oldOffset = total - layers.size();
			int newOffset = total - transferLayers.size();

			for (int slot = 0; slot < total; slot++)
			{
				if (slot >= oldOffset)
				{
					setBatchColor(batch, oldLayerAlpha);
					drawLayer(layers.get(slot - oldOffset), batch);
				}
				if (slot >= newOffset)
				{
					setBatchColor(batch, newLayerAlpha);
					drawLayer(transferLayers.get(slot - newOffset), batch);
				}
			}
		}

		batch.setColor(Color.WHITE);
	}

	private void setBatchColor(Batch batch, float alpha)
	{batch.setColor(tint.r, tint.g, tint.b, tint.a * alpha);}

	private void drawLayer(ParallaxLayer layer, Batch batch)
	{
		float originX = viewLeft + layer.currentDistanceX;
		float originY = viewBottom + drawingHeight + layer.currentDistanceY;

		tile(layer, batch, originX, originY, repeatOnX, repeatOnY, false);

		if (layer.isMirror && repeatOnX != repeatOnY)
		{
			// A mirrored copy is stacked next to the tiled strip: above it when tiling on X, to its right on Y.
			if (repeatOnX)
				tile(layer, batch, originX, originY + layer.padY + layer.getHeight(), true, false, true);
			else
				tile(layer, batch, originX + layer.padX + layer.getWidth(), originY, false, true, true);
		}
	}

	/** Draws the layer at {@code (x, y)} plus every repetition, on the requested axes, that intersects the view. */
	private void tile(ParallaxLayer layer, Batch batch, float x, float y, boolean onX, boolean onY, boolean mirror)
	{
		float width = layer.getWidth(), height = layer.getHeight();
		if (width <= 0 || height <= 0)
			return;

		// A step <= 0 (e.g. a negative padding larger than the image) can't tile: draw the layer once.
		float stepX = layer.getTotalWidth(), stepY = layer.getTotalHeight();
		boolean tileX = onX && stepX > 0, tileY = onY && stepY > 0;

		float startX = tileX ? x - (float) Math.ceil((x + width - viewLeft) / stepX) * stepX : x;
		float startY = tileY ? y - (float) Math.ceil((y + height - viewBottom) / stepY) * stepY : y;
		int countX = tileX ? (int) Math.ceil((viewLeft + viewWidth - startX) / stepX) + 1 : 1;
		int countY = tileY ? (int) Math.ceil((viewBottom + viewHeight - startY) / stepY) + 1 : 1;

		for (int row = 0; row < countY; row++)
		{
			float drawY = startY + row * stepY;
			if (drawY + height <= viewBottom || drawY >= viewBottom + viewHeight)
				continue;

			for (int column = 0; column < countX; column++)
			{
				float drawX = startX + column * stepX;
				if (drawX + width <= viewLeft || drawX >= viewLeft + viewWidth)
					continue;

				if (mirror)
					layer.drawMirror(batch, drawX, drawY, onX);
				else
					layer.draw(batch, drawX, drawY);
			}
		}
	}

	public void resetTransfert()
	{
		transferLayers = new ArrayList<>();
		transfertType = Enum_TransfertType.NONE;
		newLayerAlpha = 0;
		oldLayerAlpha = 1;
	}

	public void resetPositions()
	{
		for (int i = 0, n = layers.size(); i < n; i++)
			layers.get(i).resetPosition();
	}

	public void act(float delta, float speedX, float speedY)
	{
		for (int i = 0, n = layers.size(); i < n; i++)
			layers.get(i).act(delta, speedX, speedY, repeatOnX, repeatOnY);

		if (transfertType != Enum_TransfertType.NONE)
		{
			for (int i = 0, n = transferLayers.size(); i < n; i++)
				transferLayers.get(i).act(delta, speedX, speedY, repeatOnX, repeatOnY);

			newLayerAlpha = Math.min(1, newLayerAlpha + delta * newLayerFadeSpeed);
			oldLayerAlpha = Math.max(0, oldLayerAlpha - delta * oldLayerFadeSpeed);

			if (newLayerAlpha >= 1)
			{
				layers = transferLayers;
				resetTransfert();
			}
		}

		if (tintElapsed < tintDuration)
		{
			tintElapsed = Math.min(tintDuration, tintElapsed + delta);
			tint.set(tintFrom).lerp(tintTo, tintElapsed / tintDuration);
		}
	}

	public boolean isInTransfer()
	{return transfertType != Enum_TransfertType.NONE;}

	public float getDrawingHeight()
	{return drawingHeight;}

	public void setDrawingHeight(float drawingHeight)
	{this.drawingHeight = drawingHeight;}

	public Color getTint()
	{return tint;}

	public boolean isRepeatOnX()
	{return repeatOnX;}

	public void setRepeatOnX(boolean repeatOnX)
	{this.repeatOnX = repeatOnX;}

	public boolean isRepeatOnY()
	{return repeatOnY;}

	public void setRepeatOnY(boolean repeatOnY)
	{this.repeatOnY = repeatOnY;}
}
