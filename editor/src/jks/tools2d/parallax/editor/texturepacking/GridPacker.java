package jks.tools2d.parallax.editor.texturepacking;

import com.badlogic.gdx.tools.texturepacker.TexturePacker.Settings;
import com.badlogic.gdx.utils.Array;

import java.util.Collections;

/** @author Nathan Sweet */
public class GridPacker implements Packer {
	private Settings settings;

	public GridPacker (Settings settings) {
		this.settings = settings;
	}

	public Array<Page> pack (Array<Rect> inputRects) {
		if (!settings.silent) System.out.print("Packing");

		int cellWidth = 0, cellHeight = 0;
		for (int i = 0, nn = inputRects.size; i < nn; i++) {
			Rect rect = inputRects.get(i);
			cellWidth = Math.max(cellWidth, rect.width);
			cellHeight = Math.max(cellHeight, rect.height);
		}
		cellWidth += settings.paddingX;
		cellHeight += settings.paddingY;

		inputRects.reverse();

		Array<Page> pages = new Array();
		while (inputRects.size > 0) {
			Page result = packPage(inputRects, cellWidth, cellHeight);
			pages.add(result);
		}
		return pages;
	}

	private Page packPage (Array<Rect> inputRects, int cellWidth, int cellHeight) {
		Page page = new Page();
		page.outputRects = new Array();

		int maxWidth = settings.maxWidth, maxHeight = settings.maxHeight;
		if (settings.edgePadding) {
			maxWidth -= settings.paddingX;
			maxHeight -= settings.paddingY;
		}
		int x = 0, y = 0;
		for (int i = inputRects.size - 1; i >= 0; i--) {
			if (x + cellWidth > maxWidth) {
				y += cellHeight;
				if (y > maxHeight - cellHeight) break;
				x = 0;
			}
			Rect rect = inputRects.removeIndex(i);
			rect.x = x;
			rect.y = y;
			rect.width += settings.paddingX;
			rect.height += settings.paddingY;
			page.outputRects.add(rect);
			x += cellWidth;
			page.width = Math.max(page.width, x);
			page.height = Math.max(page.height, y + cellHeight);
		}

		// Flip so rows start at top.
		for (int i = page.outputRects.size - 1; i >= 0; i--) {
			Rect rect = page.outputRects.get(i);
			rect.y = page.height - rect.y - rect.height;
		}
		return page;
	}
}

