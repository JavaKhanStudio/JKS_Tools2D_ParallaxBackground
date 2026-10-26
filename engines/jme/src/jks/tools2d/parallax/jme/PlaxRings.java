package jks.tools2d.parallax.jme;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendFunc;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.GeometryComparator;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.renderer.queue.TransparentComparator;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.shape.Sphere;
import com.jme3.texture.Texture2D;
import com.jme3.util.BufferUtils;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.ParallaxRings;
import jks.tools2d.parallax.ParallaxRings.Band;
import jks.tools2d.parallax.ParallaxRings.Gradient;
import jks.tools2d.parallax.TransfertStyle;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * A page as the sky of a 3D world (parallax-readers r50, docs/page-as-3d-sky.md): each layer that draws an image is an
 * open cylinder round this node, its radius from its speed, so a camera flying through the world sees the layers slide
 * by perspective alone; the page's gradients are a sphere round the camera. jME runs core as is: the numbers are core's
 * {@link ParallaxRings}, and the scroll, cross-fades and tint are a {@link PlaxBackground}'s own reader's. The drawing is
 * three.js's PlaxRings (engines/threejs/src/plax.js) in jME:
 * <pre>
 * PlaxBackground bg = new PlaxBackground();
 * stateManager.attach(bg);
 * bg.setEnabled(false);                         // the rings draw the page, not the flat background
 * bg.setPage(page, atlas);
 * PlaxRings rings = new PlaxRings(assetManager, bg);
 * rings.front = 330; rings.foot = 50;           // or far = 760, lift = -2, scale = 0.7, as the saga's sky
 * rootNode.attachChild(rings);
 * PlaxRings.sortIn(viewPort);                   // the bands far first: they share a centre, no depth sort orders them
 * // each frame: rings.update(tpf);
 * </pre>
 * The bands are in the Transparent bucket, depth tested and writing no depth, so the world drawn before them stays in
 * front; {@link #sortIn} gives that bucket a comparator that draws them first, far first. The sky is in the Sky bucket.
 * A band's image is each tile of its layer as core draws it (its regions inset by half a texel, trimmed and flipped, a
 * SEQUENCE layer's cycle), curved round the ring, its texture linear in angle and in elevation; a still layer's band is
 * on the sky round the camera. FLOOR, the dolly, towers, the planet and an animated patch are 2D ways to fake depth the
 * world now gives: a band draws its layer's image plain, as three.js's do.
 */
public class PlaxRings extends Node
{
	/** The user data a band's geometries carry: their place in the drawing order, 0 first ({@link #sortIn}). */
	public static final String ORDER = "plaxRingOrder";

	// ParallaxRings.Options: what the 3D scene chooses, read at each update.
	/** The front band's radius, in world units. */
	public float front = 100;
	/** The slowest moving band's radius when more than 0: the depths compressed into front..far. 0: the library's. */
	public float far = 0;
	/** The height of the eye the page is seen from: where its horizon is. */
	public float foot = 0;
	/** The page raised (lowered when negative) by this, in page units, before {@link #scale}. */
	public float lift = 0;
	/** The page squeezed (or stretched) about its horizon. */
	public float scale = 1;
	/** How many degrees of sky the page's height spans, at scale 1. */
	public float pageDegrees = 45;
	/** The page fog's strength ({@link ParallaxPageReader#fogOf}); NaN: the page's own. */
	public float fogStrength = Float.NaN;

	// The drawing's own, as three.js's options.
	/** The fog's colour; null: the page's. */
	public Color fogColor;
	/** How much of each band's bottom melts into the fog, as a share of its height (sky.js's 0.22). */
	public float melt = 0.22f;
	/** Each band melts into the fog by its elevation above the camera's horizon, up to this, in radians: 0 none. */
	public float eyeMelt = 0;
	/** Up to which elevation the sky's fog fades into the page's gradients, in radians. */
	public float horizon = 0.12f;
	/** The background's tint laid on the sky's gradients and the fog's colour too, not only on the bands. */
	public boolean tintSky;

	private final AssetManager assets;
	private final PlaxBackground background;
	/** Each layer's band: what it was built from, its meshes. */
	private final Map<ParallaxLayer, Built> built = new IdentityHashMap<>();
	/** Each page's bands, and what they were computed from. */
	private final Map<List<ParallaxLayer>, Cached> cache = new IdentityHashMap<>();
	private final Geometry sky;
	private final TileBatch tiles;
	/** The page on screen's bands and gradient, as the last update laid them out. */
	private List<Band> bands = new ArrayList<>();
	private Gradient gradient;

	private static final class Built
	{
		Band band;
		final Node holder = new Node("plax ring");
		final List<Geometry> geometries = new ArrayList<>();
	}

	private static final class Cached
	{
		float[] key;
		List<Band> bands;
	}

	public PlaxRings(AssetManager assets, PlaxBackground background)
	{
		super("plax rings");
		this.assets = assets;
		this.background = background;
		this.tiles = new TileBatch(assets);
		sky = new Geometry("plax rings sky", new Sphere(24, 48, 1));
		Material material = new Material(assets, "jks/tools2d/parallax/jme/PlaxRingsSky.j3md");
		material.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
		material.getAdditionalRenderState().setDepthWrite(false);
		material.getAdditionalRenderState().setBlendMode(BlendMode.Off);
		sky.setMaterial(material);
		sky.setQueueBucket(Bucket.Sky);
		sky.setCullHint(CullHint.Never);
		attachChild(sky);
	}

	/**
	 * Gives {@code viewPort}'s Transparent bucket the order the bands are drawn in: every band before anything else of
	 * that bucket, far first (they share a centre, so no distance sort can order them); the rest back to front, as jME
	 * sorts it.
	 */
	public static void sortIn(ViewPort viewPort)
	{viewPort.getQueue().setGeometryComparator(Bucket.Transparent, new RingsFirst());}

	/** The options core's ParallaxRings takes for {@code page}: these, the fog the page's when not given. */
	public ParallaxRings.Options ringOptions(WholePage_Model page)
	{
		ParallaxRings.Options o = new ParallaxRings.Options();
		o.front = front;
		o.far = far;
		o.foot = foot;
		o.lift = lift;
		o.scale = scale;
		o.pageDegrees = pageDegrees;
		o.fogStrength = !Float.isNaN(fogStrength) ? fogStrength : page != null ? page.getFogStrength() : 0;
		return o;
	}

	/** The page on screen's bands, as the last {@link #update} laid them out: core's own. */
	public List<Band> bands()
	{return bands;}

	/** The page on screen's gradient by elevation, as the last {@link #update} laid it on the sky; null before a page. */
	public Gradient gradient()
	{return gradient;}

	/**
	 * Moves the background by {@code seconds} (its act: speedXAtRest's drift, the fades, the tint; 0 for none, when the
	 * game acts it itself), then lays the rings out as it is now.
	 */
	public void update(float seconds)
	{
		if (seconds != 0)
			background.act(seconds);
		ParallaxPageReader reader = background.getReader();
		List<ParallaxLayer> layers = reader.layers, incoming = reader.transferLayers;
		int total = Math.max(layers.size(), incoming.size());
		Map<ParallaxLayer, Built> live = new IdentityHashMap<>();
		// Far first, the outgoing page before the incoming one among equals: insertion order breaks the ties.
		Map<Built, Integer> drawn = new LinkedHashMap<>();
		bands = new ArrayList<>();
		for (int side = 0; side < 2; side++)
		{
			List<ParallaxLayer> page = side == 0 ? layers : incoming;
			WholePage_Model model = side == 0 ? background.getPage() : background.getTransfertPage();
			if (page.isEmpty() || model == null)
				continue;
			List<Band> sideBands = bandsOf(page, ringOptions(model));
			if (side == 0)
				bands = sideBands;
			int offset = total - page.size();
			for (Band band : sideBands)
			{
				ParallaxLayer layer = page.get(band.layer);
				Built b = sync(layer, band, model, side == 1, offset + band.layer, total);
				live.put(layer, b);
				drawn.put(b, drawn.size());
			}
		}
		for (Map.Entry<ParallaxLayer, Built> e : new ArrayList<>(built.entrySet()))
			if (!live.containsKey(e.getKey()))
				drop(e.getKey(), e.getValue());
		List<Built> order = new ArrayList<>(drawn.keySet());
		// Compared, not subtracted: two still bands are both infinitely far, and keep their order (a stable sort).
		order.sort((a, c) -> Float.compare(c.band.radius, a.band.radius));
		for (int i = 0; i < order.size(); i++)
			for (Geometry g : order.get(i).geometries)
				g.setUserData(ORDER, i);
		drawSky();
	}

	/** Drops every band and the sky's material. */
	public void dispose()
	{
		for (Map.Entry<ParallaxLayer, Built> e : new ArrayList<>(built.entrySet()))
			drop(e.getKey(), e.getValue());
		cache.clear();
	}

	/** The bands of {@code page}, computed again when the options, the layers or a sequence's cycle changed. */
	private List<Band> bandsOf(List<ParallaxLayer> page, ParallaxRings.Options o)
	{
		float[] key = new float[7 + 2 * page.size()];
		key[0] = o.front;
		key[1] = o.far;
		key[2] = o.foot;
		key[3] = o.lift;
		key[4] = o.scale;
		key[5] = o.pageDegrees;
		key[6] = o.fogStrength;
		for (int i = 0; i < page.size(); i++)
		{
			key[7 + 2 * i] = page.get(i).getDrawnSeed();
			key[8 + 2 * i] = page.get(i).getWidth();
		}
		Cached cached = cache.get(page);
		if (cached != null && java.util.Arrays.equals(cached.key, key))
			return cached.bands;
		// A page no longer shown is forgotten (a finished cross-fade's outgoing one).
		cache.keySet().removeIf(p -> p != background.getReader().layers && p != background.getReader().transferLayers);
		cached = new Cached();
		cached.key = key;
		cached.bands = ParallaxRings.bands(page, background.getWorldWidth(), o);
		cache.put(page, cached);
		return cached.bands;
	}

	/**
	 * Layer {@code layer}'s band, built when it is new or its numbers changed; then this frame's scroll, colour, fog and
	 * fade set on it. {@code slot} of {@code total}: its place in the cross-fade, 0 at the back.
	 */
	private Built sync(ParallaxLayer layer, Band band, WholePage_Model page, boolean isIncoming, int slot, int total)
	{
		Built b = built.get(layer);
		if (b == null || !same(b.band, band))
		{
			if (b != null)
				drop(layer, b);
			b = build(layer, band);
			built.put(layer, b);
		}
		b.band = band;
		// The 2D reader's scroll: the tile at currentDistanceX, one tile 1 / repeat of the ring.
		float tile = layer.getTotalWidth();
		float turn = tile > 0 ? -layer.getCurrentDistanceX() / tile * FastMath.TWO_PI / band.repeat : 0;
		b.holder.setLocalRotation(new Quaternion().fromAngleAxis(turn, Vector3f.UNIT_Y));

		ParallaxPageReader reader = background.getReader();
		TransfertStyle style = reader.getTransfertStyle();
		boolean transfert = !reader.transferLayers.isEmpty();
		float progress = reader.getIncomingAlpha();
		// During a cross-fade, the layer's opacity as the flat reader's (ParallaxPageReader.draw); a dissolve fades it by
		// its share, it has no mask; through a colour only one side of a slot shows, at full opacity, mixed toward it.
		float fade = 1, grade = 0;
		if (transfert)
		{
			if (style.getKind() == TransfertStyle.Kind.FADE)
				fade = isIncoming ? progress : reader.getOutgoingAlpha();
			else if (style.grades())
			{
				fade = style.showsIncoming(progress, slot, total) == isIncoming ? 1 : 0;
				grade = Math.max(0, Math.min(1, style.gradeAt(progress, slot, total)));
			}
			else
			{
				float ramp = style.slotRamp(progress, slot, total);
				fade = isIncoming ? ramp : 1 - ramp;
			}
		}
		Color tint = reader.getTint();
		float alpha = tint.a * fade;
		float[] fog = fogColor(page);
		for (Geometry g : b.geometries)
		{
			g.setCullHint(alpha > 0 ? (band.radius == Float.POSITIVE_INFINITY ? CullHint.Never : CullHint.Inherit) : CullHint.Always);
			Material m = g.getMaterial();
			m.setVector4("Tint", new com.jme3.math.Vector4f(packed(tint.r), packed(tint.g), packed(tint.b), packedAlpha(alpha)));
			m.setVector3("FogColor", new Vector3f(fog[0], fog[1], fog[2]));
			m.setFloat("Haze", band.fog);
			m.setFloat("Melt", melt);
			m.setFloat("EyeMelt", eyeMelt);
			if (grade > 0)
				m.setVector4("Grade", new com.jme3.math.Vector4f(style.getColorR(), style.getColorG(), style.getColorB(), grade));
			else
				m.setVector4("Grade", new com.jme3.math.Vector4f(0, 0, 0, 0));
		}
		return b;
	}

	/** Whether two bands of one layer are laid out alike: then its meshes stand. */
	private static boolean same(Band a, Band b)
	{
		return a.radius == b.radius && a.bottom == b.bottom && a.top == b.top && a.y == b.y && a.height == b.height
				&& a.repeat == b.repeat && a.imageShare == b.imageShare;
	}

	private static final int ROWS = 8;
	private static final float STEP = FastMath.TWO_PI / 192;

	/**
	 * Layer {@code layer}'s band as geometries, one per texture its images sit on, in a holder the scroll turns: each
	 * image of its tile laid round the ring once per repeat, curved, its texture linear in angle and in elevation. A
	 * still band is on a cylinder of radius 1, which its shader puts round the camera.
	 */
	private Built build(ParallaxLayer layer, Band band)
	{
		boolean still = band.radius == Float.POSITIVE_INFINITY;
		float radius = still ? 1 : band.radius, footY = still ? 0 : foot;
		float tile = layer.getTotalWidth(), high = layer.getRegionHeight(), turn = FastMath.TWO_PI / band.repeat;
		List<TileBatch.Quad> quads = tiles.capture(layer);
		Map<Texture2D, Run> runs = new LinkedHashMap<>();
		for (int k = 0; k < band.repeat; k++)
			for (TileBatch.Quad q : quads)
			{
				float w = q.x1 - q.x0;
				if (!(w > 0) || !(tile > 0) || !(high > 0))
					continue;
				Run run = runs.computeIfAbsent(q.texture, t -> new Run());
				int cols = Math.max(1, (int) Math.ceil(w / tile * turn / STEP));
				int first = run.vertices;
				for (int j = 0; j <= ROWS; j++)
				{
					float y = q.y0 + (q.y1 - q.y0) * j / ROWS, along = y / high;
					float height = footY + radius * (float) Math.tan(band.bottom + along * (band.top - band.bottom));
					float v = q.v0 + (q.v1 - q.v0) * j / ROWS;
					for (int i = 0; i <= cols; i++)
					{
						float a = k * turn + (q.x0 + w * i / cols) / tile * turn;
						run.add(radius * FastMath.sin(a), height, -radius * FastMath.cos(a), q.u0 + (q.u1 - q.u0) * i / cols, v, along);
					}
				}
				for (int j = 0; j < ROWS; j++)
					for (int i = 0; i < cols; i++)
					{
						int a = first + j * (cols + 1) + i, b = a + 1, c = a + cols + 1, d = c + 1;
						run.index(a, b, d, a, d, c);
					}
			}
		Built b = new Built();
		b.band = band;
		// Its bound is a unit cylinder at the centre, wherever its shader draws it: never culled for that.
		if (still)
			b.holder.setCullHint(CullHint.Never);
		for (Map.Entry<Texture2D, Run> e : runs.entrySet())
		{
			Geometry g = new Geometry("plax ring of layer " + band.layer, e.getValue().mesh());
			g.setMaterial(material(e.getKey(), still));
			g.setQueueBucket(Bucket.Transparent);
			if (still)
				g.setCullHint(CullHint.Never);
			b.holder.attachChild(g);
			b.geometries.add(g);
		}
		attachChild(b.holder);
		return b;
	}

	/** A band's material: blended as every layer is, behind the 3D world (it tests its depth and writes none). */
	private Material material(Texture2D texture, boolean still)
	{
		Material m = new Material(assets, "jks/tools2d/parallax/jme/PlaxRing.j3md");
		m.setTexture("ColorMap", texture);
		m.setBoolean("Still", still);
		m.getAdditionalRenderState().setBlendMode(BlendMode.Custom);
		m.getAdditionalRenderState().setCustomBlendFactors(BlendFunc.Src_Alpha, BlendFunc.One_Minus_Src_Alpha, BlendFunc.One,
				BlendFunc.One_Minus_Src_Alpha);
		m.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
		m.getAdditionalRenderState().setDepthTest(true);
		m.getAdditionalRenderState().setDepthWrite(false);
		return m;
	}

	private void drop(ParallaxLayer layer, Built b)
	{
		b.holder.removeFromParent();
		built.remove(layer);
	}

	/** The page's gradients on the sky, by elevation, the fog under the horizon. */
	private void drawSky()
	{
		WholePage_Model page = background.getPage();
		JmeGradient top = background.topGradient(), bottom = background.bottomGradient();
		sky.setCullHint(page != null && top != null ? CullHint.Never : CullHint.Always);
		if (page == null || top == null)
		{
			gradient = null;
			return;
		}
		gradient = ParallaxRings.gradient(page.topHalfSize, page.bottomHalfSize, background.getWorldWidth(), ringOptions(page));
		Material m = sky.getMaterial();
		m.setVector3("TopTo", packed(skyTinted(top.topColor)));
		m.setVector3("TopFrom", packed(skyTinted(top.bottomColor)));
		m.setVector3("BottomTo", packed(skyTinted(bottom.topColor)));
		m.setVector3("BottomFrom", packed(skyTinted(bottom.bottomColor)));
		m.setVector2("Top", new com.jme3.math.Vector2f(gradient.topFrom, gradient.topTo));
		m.setVector2("Bottom", new com.jme3.math.Vector2f(gradient.bottomFrom, gradient.bottomTo));
		float[] fog = fogColor(page);
		m.setVector3("FogColor", new Vector3f(fog[0], fog[1], fog[2]));
		m.setFloat("Horizon", horizon);
	}

	/** The fog's colour ({@link #fogColor}, else the page's, else a page's default), tinted with {@link #tintSky}. */
	public float[] fogColor(WholePage_Model page)
	{return skyTinted(fogColor != null ? fogColor : page != null ? page.fogColor : DEFAULT_FOG);}

	private static final Color DEFAULT_FOG = new Color(WholePage_Model.FOG_R, WholePage_Model.FOG_G, WholePage_Model.FOG_B, 1);

	/** Colour {@code c}'s red, green and blue, times the background's tint with {@link #tintSky}. */
	private float[] skyTinted(Color c)
	{
		Color t = background.getReader().getTint();
		return tintSky ? new float[] { c.r * t.r, c.g * t.g, c.b * t.b } : new float[] { c.r, c.g, c.b };
	}

	private static Vector3f packed(float[] c)
	{return new Vector3f(packed(c[0]), packed(c[1]), packed(c[2]));}

	/** A channel as libGDX packs a colour (Color.toFloatBits): 8 bits, truncated. */
	private static float packed(float c)
	{return (int) (255 * Math.min(1, Math.max(0, c))) / 255f;}

	/** An alpha as libGDX packs it: 8 bits, truncated, the lowest dropped, stretched back to 0..1 as SpriteBatch's shader does. */
	private static float packedAlpha(float a)
	{return ((int) (255 * Math.min(1, Math.max(0, a))) & 0xFE) / 254f;}

	/** One texture's share of a band: its vertices and triangles as they are laid. */
	private static final class Run
	{
		float[] positions = new float[256 * 3], uvs = new float[256 * 2], along = new float[256 * 2];
		int[] indices = new int[256 * 6];
		int vertices, count;

		void add(float x, float y, float z, float u, float v, float band)
		{
			if (vertices * 3 + 3 > positions.length)
			{
				positions = java.util.Arrays.copyOf(positions, positions.length * 2);
				uvs = java.util.Arrays.copyOf(uvs, uvs.length * 2);
				along = java.util.Arrays.copyOf(along, along.length * 2);
			}
			positions[vertices * 3] = x;
			positions[vertices * 3 + 1] = y;
			positions[vertices * 3 + 2] = z;
			uvs[vertices * 2] = u;
			uvs[vertices * 2 + 1] = v;
			along[vertices * 2] = band;
			vertices++;
		}

		void index(int... six)
		{
			if (count + six.length > indices.length)
				indices = java.util.Arrays.copyOf(indices, indices.length * 2);
			System.arraycopy(six, 0, indices, count, six.length);
			count += six.length;
		}

		Mesh mesh()
		{
			Mesh mesh = new Mesh();
			mesh.setBuffer(Type.Position, 3, BufferUtils.createFloatBuffer(java.util.Arrays.copyOf(positions, vertices * 3)));
			mesh.setBuffer(Type.TexCoord, 2, BufferUtils.createFloatBuffer(java.util.Arrays.copyOf(uvs, vertices * 2)));
			mesh.setBuffer(Type.TexCoord2, 2, BufferUtils.createFloatBuffer(java.util.Arrays.copyOf(along, vertices * 2)));
			mesh.setBuffer(Type.Index, 3, BufferUtils.createIntBuffer(java.util.Arrays.copyOf(indices, count)));
			mesh.updateBound();
			return mesh;
		}
	}

	/**
	 * What a layer's tile draws, recorded rather than drawn: core's own {@link ParallaxLayer#draw} through it gives each
	 * image's region (inset by half a texel), its place in the tile and its flips, a SEQUENCE layer's whole cycle.
	 */
	static final class TileBatch extends JmeBatch
	{
		/** One image of a tile: x from its left, y from its bottom, in the layer's units, u and v at those edges. */
		static final class Quad
		{
			Texture2D texture;
			float x0, x1, y0, y1, u0, u1, v0, v1;
		}

		private final List<Quad> quads = new ArrayList<>();

		TileBatch(AssetManager assets)
		{super(assets, new Node(), 0);}

		/** The images of one tile of {@code layer}, its animated patch left out. */
		List<Quad> capture(ParallaxLayer layer)
		{
			quads.clear();
			layer.setDrawPart(ParallaxLayer.DRAW_IMAGE);
			try
			{layer.draw(this, 0, 0);}
			finally
			{layer.setDrawPart(ParallaxLayer.DRAW_ALL);}
			return new ArrayList<>(quads);
		}

		/** A flip draws with a negative width or height: its edges are put back in order, their u and v with them. */
		@Override
		public void draw(TextureRegion region, float x, float y, float width, float height)
		{
			Quad q = new Quad();
			q.texture = ((JmeAtlas.PageTexture) region.getTexture()).texture;
			// SpriteBatch's corners: (x, v2) at the bottom left, (x + width, v) at the top right.
			boolean fx = width < 0, fy = height < 0;
			q.x0 = fx ? x + width : x;
			q.x1 = fx ? x : x + width;
			q.u0 = fx ? region.getU2() : region.getU();
			q.u1 = fx ? region.getU() : region.getU2();
			q.y0 = fy ? y + height : y;
			q.y1 = fy ? y : y + height;
			q.v0 = fy ? region.getV() : region.getV2();
			q.v1 = fy ? region.getV2() : region.getV();
			quads.add(q);
		}
	}

	/** The Transparent bucket's order with rings: the bands first, by {@link #ORDER}; the rest as jME sorts it. */
	static final class RingsFirst implements GeometryComparator
	{
		private final TransparentComparator rest = new TransparentComparator();

		@Override
		public void setCamera(Camera camera)
		{rest.setCamera(camera);}

		@Override
		public int compare(Geometry a, Geometry b)
		{
			Integer oa = a.getUserData(ORDER), ob = b.getUserData(ORDER);
			if (oa != null && ob != null)
				return Integer.compare(oa, ob);
			if (oa != null || ob != null)
				return oa != null ? -1 : 1;
			return rest.compare(a, b);
		}
	}
}
