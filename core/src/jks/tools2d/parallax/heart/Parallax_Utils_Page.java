package jks.tools2d.parallax.heart;

import jks.tools2d.parallax.pages.WholePage_Model;

public final class Parallax_Utils_Page
{
	private Parallax_Utils_Page()
	{}

	public static void setPage(Parallax_Heart ref, WholePage_Model pageModel)
	{
		release(ref, ref.currentTransfertPage, pageModel);
		release(ref, ref.currentPage, pageModel);
		ref.currentTransfertPage = null;
		ref.currentPage = pageModel;
		ref.parallaxReader.layers.clear();
		ref.parallaxReader.resetTransfert();
		ref.parallaxReader.addLayers(pageModel.getDrawing(ref.relativePath, ref.getWorldWidth(), ref.getWorldHeight()));
		ref.parallaxReader.setRepeatOnX(pageModel.repeatOnX);
		ref.parallaxReader.setRepeatOnY(pageModel.repeatOnY);

		ref.topSquare = pageModel.buildTopSquareBackground(pageModel.topHalfSize);
		ref.bottomSquare = pageModel.buildBottomSquareBackground(pageModel.bottomHalfSize);
	}

	public static void transfertIntoPage(Parallax_Heart ref, WholePage_Model pageModel, float inXSecondes)
	{
		// A transfer interrupted by a new one never becomes the current page.
		if (ref.currentTransfertPage != ref.currentPage)
			release(ref, ref.currentTransfertPage, pageModel);
		ref.currentTransfertPage = pageModel;

		ref.parallaxReader.addLayersTransfert(pageModel, inXSecondes);
		if (ref.topSquare != null)
			ref.topSquare.transfertInto(pageModel.topHalf_top, pageModel.topHalf_bottom, inXSecondes);
		if (ref.bottomSquare != null)
			ref.bottomSquare.transfertInto(pageModel.bottomHalf_top, pageModel.bottomHalf_bottom, inXSecondes);
		if (!ref.parallaxReader.isInTransfer())
			transferFinished(ref);
	}

	/** Called once the reader has swapped in the transferred layers: that page becomes the current one. */
	static void transferFinished(Parallax_Heart ref)
	{
		WholePage_Model previous = ref.currentPage;
		ref.currentPage = ref.currentTransfertPage;
		ref.currentTransfertPage = null;
		release(ref, previous, ref.currentPage);
	}

	/** Frees the atlas a page loaded itself, unless that page is still in use. */
	private static void release(Parallax_Heart ref, WholePage_Model page, WholePage_Model keep)
	{
		if (page != null && page != keep)
			page.disposeOwnedAtlas();
	}
}
