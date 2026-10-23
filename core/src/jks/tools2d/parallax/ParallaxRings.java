package jks.tools2d.parallax;

import java.util.ArrayList;
import java.util.List;

/**
 * A page drawn round a 3D world, as rings (parallax-readers r35, r47; docs/page-as-3d-sky.md): each layer that draws an
 * image is an open cylinder round the world's centre, its radius from its speed, so a camera moving through the world
 * sees the layers slide by perspective alone. This class is the numbers only, for a 3D reader to draw (three.js, Godot,
 * jME): radius, the elevations the band spans, where it stands, how often its image goes round, the order to draw it in
 * and its fog. Every reader ports it line for line; engines/rounds/rings checks their numbers against these.
 * <p>
 * A band spans the elevations the page gives its layer, seen from the centre at {@link Options#foot}: the page's height
 * is {@link Options#pageDegrees} of sky, its bottom the horizon, before {@link Options#lift} and {@link Options#scale}
 * fit it on the world. The page's height is the one its world has at 16:9 ({@code worldWidth * 9 / 16}, 22.5 for 40
 * wide): a ring has no screen, so its page keeps one aspect. A still layer (speed 0) is infinitely far: its radius is
 * {@link Float#POSITIVE_INFINITY}, and the reader draws it on the sky round the camera, as it draws the gradients.
 * <p>
 * The scroll is the 2D reader's: a band's image is offset round the ring by its layer's
 * {@link ParallaxLayer#getCurrentDistanceX()} over its {@link ParallaxLayer#getTotalWidth()}, one tile being
 * {@code 1 / repeat} of the ring, so speedXAtRest, the game's speeds and cross-fades come from the reader as they are.
 */
public final class ParallaxRings
{
	/** No band reaches the zenith: a cylinder there would be infinitely high. */
	public static final float MAX_ELEVATION = (float) Math.toRadians(89);

	private ParallaxRings()
	{}

	/** What the 3D scene chooses: the page itself stores none of it. */
	public static final class Options
	{
		/** The front band's radius, in the world's units. */
		public float front = 100;
		/**
		 * The slowest moving band's radius when more than 0: the depths are then compressed into {@code front..far}
		 * ({@link #radiusOf}). 0: the library's depths, {@link ParallaxDepth#distanceOf} times {@link #front}.
		 */
		public float far = 0;
		/** The height of the eye the page is seen from: where its horizon is. */
		public float foot = 0;
		/** The page raised (lowered when negative) by this, in page units, before {@link #scale}. */
		public float lift = 0;
		/** The page squeezed (or stretched) about its horizon. */
		public float scale = 1;
		/** How many degrees of sky the page's height spans, at scale 1. */
		public float pageDegrees = 45;
		/** The page fog's strength ({@link ParallaxPageReader#fogOf}): the page's own, or the scene's. */
		public float fogStrength = 0;
	}

	/** One layer's ring. */
	public static final class Band
	{
		/** The layer's index in its page. */
		public int layer;
		/** The ring's radius, {@link Float#POSITIVE_INFINITY} for a still layer. */
		public float radius;
		/** The elevations of its bottom and top edges, in radians, seen from the centre at the foot. */
		public float bottom, top;
		/** Its bottom edge's height in the world, and its own height: a finite ring only (0 for a still one). */
		public float y, height;
		/** How many times its tile goes round the ring, at least once. */
		public int repeat;
		/** The share of a tile the image fills, the rest being padX: 1 without a pad. */
		public float imageShare;
		/** When to draw it: 0 first. Far first, as the rings share a centre and no depth sort can order them. */
		public int order;
		/** How much of it the fog hides ({@link ParallaxPageReader#fogOf}). */
		public float fog;
	}

	/**
	 * The page's two gradients by elevation, in radians: the top one from {@code topFrom} (its bottom colour) to
	 * {@code topTo} (its top colour), the bottom one from {@code bottomFrom} (its bottom colour) to {@code bottomTo} (its
	 * top colour), drawn over the top one where they meet, as the 2D heart draws them. Above the top gradient its top
	 * colour goes on to the zenith; under the horizon is the scene's fog.
	 */
	public static final class Gradient
	{
		public float topFrom, topTo, bottomFrom, bottomTo;
	}

	/** The page's height in its units, as a ring sees it: the world's at 16:9. */
	public static float pageHeightOf(float worldWidth)
	{return worldWidth * 9 / 16;}

	/** The elevation of {@code pageY} (page units up from its bottom edge), in radians, kept under the zenith. */
	public static float elevationOf(float pageY, float worldWidth, Options options)
	{
		float perUnit = (float) Math.toRadians(options.pageDegrees) / pageHeightOf(worldWidth);
		return Math.min(MAX_ELEVATION, (pageY + options.lift) * perUnit * options.scale);
	}

	/**
	 * The radius of {@code layer}'s ring, the page's speeds running from {@code slowest} (the slowest that moves) to
	 * {@code front}, as magnitudes. The library's depth: {@code options.front * front / speed}. With {@code options.far}:
	 * {@code options.front * (front / speed)^p}, {@code p = ln(far / options.front) / ln(front / slowest)}, which puts the
	 * slowest at {@code far} (deux-generaux lib/saga/sky.js's law). Still: infinite.
	 */
	public static float radiusOf(float front, float slowest, ParallaxLayer layer, Options options)
	{
		float speed = Math.abs(layer.getParallaxSpeedRatioX());
		if (!(speed > 0))
			return Float.POSITIVE_INFINITY;
		float depth = ParallaxDepth.distanceOf(front, layer);
		if (options.far > 0 && slowest > 0 && slowest < front)
		{
			double p = Math.log(options.far / options.front) / Math.log(front / slowest);
			return (float) (options.front * Math.pow(depth, p));
		}
		return options.front * depth;
	}

	/** The rings of {@code page}'s layers that draw an image, in page order (EMPTY and PARTICLES have none). */
	public static List<Band> bands(List<ParallaxLayer> page, float worldWidth, Options options)
	{
		float front = ParallaxPageReader.frontSpeedOf(page), slowest = front;
		for (int i = 0, n = page.size(); i < n; i++)
		{
			float speed = Math.abs(page.get(i).getParallaxSpeedRatioX());
			if (speed > 0 && speed < slowest)
				slowest = speed;
		}
		float pageHeight = pageHeightOf(worldWidth);
		List<Band> bands = new ArrayList<>();
		for (int i = 0, n = page.size(); i < n; i++)
		{
			ParallaxLayer layer = page.get(i);
			if (!layer.drawsImage())
				continue;
			Band band = new Band();
			band.layer = i;
			band.radius = radiusOf(front, slowest, layer, options);
			float foot = layer.getDecalPercentY() * pageHeight / 100, high = layer.getRegionHeight();
			band.bottom = elevationOf(foot, worldWidth, options);
			band.top = elevationOf(foot + high, worldWidth, options);
			float tile = layer.getTotalWidth();
			band.imageShare = tile > 0 ? layer.getWidth() / tile : 1;
			band.repeat = 1;
			if (band.radius != Float.POSITIVE_INFINITY)
			{
				band.y = options.foot + band.radius * (float) Math.tan(band.bottom);
				band.height = band.radius * (float) (Math.tan(band.top) - Math.tan(band.bottom));
				// a tile as wide, for the band's height, as the image is for the layer's: the painting keeps its aspect
				if (band.height > 0 && high > 0)
					band.repeat = Math.max(1, Math.round((float) (2 * Math.PI * band.radius) / (tile * band.height / high)));
			}
			else if (band.top > band.bottom && high > 0)
				band.repeat = Math.max(1, Math.round((float) (2 * Math.PI) / (tile * (band.top - band.bottom) / high)));
			band.fog = ParallaxPageReader.fogOf(options.fogStrength, front, layer);
			bands.add(band);
		}
		// far first; among equals, page order (back to front)
		List<Band> sorted = new ArrayList<>(bands);
		for (int i = 1; i < sorted.size(); i++)
			for (int j = i; j > 0 && sorted.get(j).radius > sorted.get(j - 1).radius; j--)
				sorted.set(j, sorted.set(j - 1, sorted.get(j)));
		for (int i = 0; i < sorted.size(); i++)
			sorted.get(i).order = i;
		return bands;
	}

	/**
	 * The page's gradients by elevation: each covers the part of the page's height the 2D heart gives it
	 * ({@code topHalfSize} and {@code bottomHalfSize}, the part each leaves uncovered).
	 */
	public static Gradient gradient(float topHalfSize, float bottomHalfSize, float worldWidth, Options options)
	{
		float h = pageHeightOf(worldWidth);
		Gradient g = new Gradient();
		g.topFrom = elevationOf(h * topHalfSize, worldWidth, options);
		g.topTo = elevationOf(h, worldWidth, options);
		g.bottomFrom = elevationOf(0, worldWidth, options);
		g.bottomTo = elevationOf(h * (1 - bottomHalfSize), worldWidth, options);
		return g;
	}
}
