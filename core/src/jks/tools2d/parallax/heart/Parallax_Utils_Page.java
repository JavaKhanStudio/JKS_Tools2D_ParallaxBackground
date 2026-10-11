package jks.tools2d.parallax.heart;

import jks.tools2d.parallax.TransfertStyle;
import jks.tools2d.parallax.pages.WholePage_Model;

public final class Parallax_Utils_Page
{
	private Parallax_Utils_Page()
	{}

	public static void setPage(Parallax_Heart ref, WholePage_Model pageModel)
	{
		WholePage_Model was = ref.currentPage, wasIncoming = ref.currentTransfertPage;
		ref.currentTransfertPage = null;
		ref.currentPage = pageModel;
		handOver(ref, was, wasIncoming);
		ref.parallaxReader.clearLayers();
		ref.parallaxReader.resetTransfert();
		ref.parallaxReader.addLayers(ref.parallaxReader.layersOf(pageModel, ref.relativePath));
		ref.parallaxReader.setRepeatOnX(pageModel.repeatOnX);
		ref.parallaxReader.setRepeatOnY(pageModel.repeatOnY);
		ref.parallaxReader.setFog(pageModel.getFogStrength(), pageModel.fogColor);
		ref.parallaxReader.setPlanetRadius(pageModel.planetRadius);

		ref.topSquare = pageModel.buildTopSquareBackground(pageModel.topHalfSize);
		ref.bottomSquare = pageModel.buildBottomSquareBackground(pageModel.bottomHalfSize);
	}

	public static void transfertIntoPage(Parallax_Heart ref, WholePage_Model pageModel, float inXSecondes)
	{transfertIntoPage(ref, pageModel, inXSecondes, TransfertStyle.FADE);}

	/**
	 * Cross-fades into {@code pageModel}, its layers drawn as {@code style} says; the gradients fade straight, or through
	 * a {@link TransfertStyle#throughColor}'s colour or a {@link TransfertStyle#fogCreep}'s mist.
	 */
	public static void transfertIntoPage(Parallax_Heart ref, WholePage_Model pageModel, float inXSecondes, TransfertStyle style)
	{
		// A transfer interrupted by a new one never becomes the current page.
		WholePage_Model wasIncoming = ref.currentTransfertPage;
		ref.currentTransfertPage = pageModel;
		handOver(ref, ref.currentPage, wasIncoming);

		ref.parallaxReader.addLayersTransfert(pageModel, ref.relativePath, inXSecondes, style);
		if (ref.topSquare != null)
			ref.topSquare.transfertInto(pageModel.topHalf_top, pageModel.topHalf_bottom, inXSecondes, style);
		if (ref.bottomSquare != null)
			ref.bottomSquare.transfertInto(pageModel.bottomHalf_top, pageModel.bottomHalf_bottom, inXSecondes, style);
		if (!ref.parallaxReader.isInTransfer())
			transferFinished(ref);
	}

	/** Called once the reader has swapped in the transferred layers: that page becomes the current one. */
	static void transferFinished(Parallax_Heart ref)
	{
		WholePage_Model was = ref.currentPage, wasIncoming = ref.currentTransfertPage;
		ref.currentPage = ref.currentTransfertPage;
		ref.currentTransfertPage = null;
		handOver(ref, was, wasIncoming);
	}

	/** The heart lets go of both its pages: it is disposed. */
	static void leave(Parallax_Heart ref)
	{
		WholePage_Model was = ref.currentPage, wasIncoming = ref.currentTransfertPage;
		ref.currentPage = null;
		ref.currentTransfertPage = null;
		handOver(ref, was, wasIncoming);
	}

	/**
	 * Holds the pages the heart shows now that it did not before ({@code was}, {@code wasIncoming}), then lets go of
	 * the ones it no longer shows: a page's own atlas is freed only once no heart shows it (one page in two hearts, r331).
	 * Each page counts once per heart, current and incoming alike.
	 */
	private static void handOver(Parallax_Heart ref, WholePage_Model was, WholePage_Model wasIncoming)
	{
		WholePage_Model now = ref.currentPage, incoming = ref.currentTransfertPage;
		if (now != null && now != was && now != wasIncoming)
			now.holdAtlas();
		if (incoming != null && incoming != now && incoming != was && incoming != wasIncoming)
			incoming.holdAtlas();
		if (was != null && was != now && was != incoming)
			was.releaseAtlas();
		if (wasIncoming != null && wasIncoming != was && wasIncoming != now && wasIncoming != incoming)
			wasIncoming.releaseAtlas();
	}
}
