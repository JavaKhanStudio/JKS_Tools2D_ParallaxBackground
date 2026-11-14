package jks.tools2d.parallax;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * The batch a layer behind a tower seen from close by is drawn through (r350, docs/depth-tricks.md §2): the layer is a
 * panorama, the far side of a cylinder of radius {@code R} round the tower's axis, seen by a camera {@code D} from that
 * axis ({@code q = D / R}). Its point at angle {@code t} from the middle of that far side is drawn at
 * {@code axis + rho sin t (q + 1) / (q + cos t)} and scaled up and down about the eye by {@code (q + 1) / (q + cos t)},
 * the flat layer's point an arc {@code s} from the axis being the one at {@code t = s / rho}: at the view's middle the
 * layer is as flat, toward its sides wider and taller, so its lines below the eye bow down toward the sides and the art
 * speeds up as it nears them ({@code rho}, the layer's radius at its own scale, is set by the reader:
 * {@link ParallaxPageReader#panoramaRadiusOf}). Each {@code draw(region, x, y, width, height)} (the one call every
 * engine's batch draws) is cut at the {@link #STRIPS} strips of equal width the view is divided in, fixed in it, each
 * piece placed by its edges and scaled at its strip's middle; what lies outside the view is not drawn. The other draws
 * go to the batch under it as is. One per reader, set for each layer; nothing allocated in a frame.
 */
final class PanoramaBatch extends WrappingBatch
{
	/** How many strips the view is cut in, across. */
	static final int STRIPS = 128;

	private float rho, q, axisX, eyeY;
	/** Each strip's left edge, then the last's right one, as arcs along the panorama from the axis; each strip's scale. */
	private final float[] edges = new float[STRIPS + 1], scales = new float[STRIPS];
	/** The view's left edge and each strip's width. */
	private float viewLeft, step;
	/** The piece of a region a strip draws, set before each piece's draw. */
	private final TextureRegion piece = new TextureRegion();

	/**
	 * Draws through {@code batch} a panorama of radius {@code rho} at its own scale, the camera {@code q} of its radius
	 * from the axis at {@code axisX}, scaled about {@code eyeY}, across the view from {@code viewLeft}.
	 */
	PanoramaBatch set(Batch batch, float rho, float q, float axisX, float eyeY, float viewLeft, float viewWidth)
	{
		this.batch = batch;
		this.rho = rho;
		this.q = q;
		this.axisX = axisX;
		this.eyeY = eyeY;
		this.viewLeft = viewLeft;
		step = viewWidth / STRIPS;
		for (int i = 0; i <= STRIPS; i++)
			edges[i] = rho * angleAt(rho, q, viewLeft + i * step - axisX);
		for (int i = 0; i < STRIPS; i++)
			scales[i] = scaleAt(q, angleAt(rho, q, viewLeft + (i + 0.5f) * step - axisX));
		return this;
	}

	/** Where the panorama's point at angle {@code t} is drawn, from the axis. */
	static float screenOf(float rho, float q, float t)
	{return rho * (float) Math.sin(t) * (q + 1) / (q + (float) Math.cos(t));}

	/** How much the panorama is scaled up and down about the eye at angle {@code t}: 1 at its middle. */
	static float scaleAt(float q, float t)
	{return (q + 1) / (q + (float) Math.cos(t));}

	/**
	 * The angle of the panorama's point drawn {@code x} from the axis ({@link #screenOf} turned round):
	 * {@code rho (q + 1) sin t - x cos t = x q}, so {@code t = atan2(x, rho (q + 1)) + asin(x q / A)},
	 * {@code A = sqrt(rho^2 (q + 1)^2 + x^2)}. The camera inside the cylinder ({@code q <= 1}), every x has one.
	 */
	static float angleAt(float rho, float q, float x)
	{
		double c = rho * (q + 1), a = Math.sqrt(c * c + x * x);
		return (float) (Math.atan2(x, c) + Math.asin(Math.max(-1, Math.min(1, x * q / a))));
	}

	/**
	 * The region over {@code x} to {@code x + width} (a negative width, flipped on X, runs it right to left), cut at the
	 * strips, each piece placed round the panorama and scaled about the eye.
	 */
	@Override
	public void draw(TextureRegion region, float x, float y, float width, float height)
	{
		float a = Math.min(x, x + width) - axisX, b = Math.max(x, x + width) - axisX;
		if (!(b > a) || !(step > 0) || b <= edges[0] || a >= edges[STRIPS])
			return;
		// The first strip whose right edge is past a: edges grow left to right.
		int lo = 0, hi = STRIPS - 1;
		while (lo < hi)
		{
			int mid = (lo + hi) >>> 1;
			if (edges[mid + 1] <= a)
				lo = mid + 1;
			else
				hi = mid;
		}
		float u = region.getU(), u2 = region.getU2();
		piece.setTexture(region.getTexture());
		for (int strip = lo; strip < STRIPS && edges[strip] < b; strip++)
		{
			float p0 = Math.max(a, edges[strip]), p1 = Math.min(b, edges[strip + 1]);
			if (!(p1 > p0))
				continue;
			// The piece's share of the region, from x along the width: decreasing when it runs right to left.
			float t0 = (p0 + axisX - x) / width, t1 = (p1 + axisX - x) / width;
			if (piece.getTexture() != null)
				piece.setRegion(u + (u2 - u) * Math.min(t0, t1), region.getV(), u + (u2 - u) * Math.max(t0, t1), region.getV2());
			float x0 = p0 == edges[strip] ? viewLeft + strip * step : axisX + screenOf(rho, q, p0 / rho);
			float x1 = p1 == edges[strip + 1] ? viewLeft + (strip + 1) * step : axisX + screenOf(rho, q, p1 / rho);
			float scale = scales[strip], pieceY = eyeY + (y - eyeY) * scale, pieceH = height * scale;
			if (width > 0)
				batch.draw(piece, x0, pieceY, x1 - x0, pieceH);
			else
				batch.draw(piece, x1, pieceY, x0 - x1, pieceH);
		}
	}
}
