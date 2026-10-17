package jks.tools2d.parallax.rounds;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import jks.tools2d.parallax.Enum_Detail;
import jks.tools2d.parallax.LayerDetail;
import jks.tools2d.parallax.TransfertStyle;

/**
 * A reader round's round.json, read once for every Java runner (r312): the libGDX reference renderer
 * ({@code ParallaxShots}, shots/) and the jME frame runner ({@code JmeParallaxShots}, engines/jme/tests). A new round
 * field goes here, and in the runners that play it. engines/godot/tests/shots.gd reads the same file a third time, in
 * GDScript: add it there too.
 * <p>
 * A round is a folder holding round.json: the scenes, each a page file and the folder of its atlas, both relative to
 * the working directory (the repository root). Each scene scrolls at 60 units/s stepped at 1/60 s and is saved
 * {@code <id>-t0/-t6/-t12.png}, 0, 6 and 12 s in, 1280x720. A scene may also:
 * {@code "transfer": {"at": 4, "seconds": 4, "page": ..., "atlasDir": ...}} cross-fade into that page (no page: into the
 * page on screen; a list of them: one after the other, r98; {@code "style": {"kind": "DEPTH_STAGGER", "stagger": 1}}:
 * drawn in that {@link TransfertStyle}, r249), {@code "tint": {"at": 4, "seconds": 4, "color": [r, g, b, a]}} tint, both
 * started {@code at} seconds into the scroll (r94, engines/rounds/transfer); {@code "speedY": 30} also scroll up, and
 * {@code "resize": {"at": 3, "size": [720, 1280]}} resize the window mid-scroll (r95, engines/rounds/conformance);
 * {@code "hooks": {"slot": {"region": "parallax4", "position": 1}}} draws the EMPTY layer named slot with that atlas
 * region stretched over each tile (r177, engines/rounds/effects; {@code "planet": true} stands it on its tile's foot
 * round the page's planet, placed by {@code planetPlaceAt} and turned by {@code planetAngleAt}, r347); {@code "sequenceSeed": 1234} draws the SEQUENCE layers from that
 * game seed (r183, engines/rounds/sequence); {@code "film": {"seconds": 8, "fps": 30}} goes on scrolling after the last
 * still and saves every frame of those seconds, {@code <id>-f0000.png} on (r239: tools/ai-film.sh makes them a video;
 * libGDX only); {@code "zoom": [1, 0.5]} zooms the camera straight from the first at 0 s to the second at 12 s, set
 * before each still, and {@code "dolly": true} dollies it ({@code ParallaxPageReader.setDolly}, r278,
 * engines/rounds/dolly). {@code "detail": "MEDIUM"} draws it at that {@link Enum_Detail}
 * ({@code ParallaxPageReader.setDetail}, r464), {@code "detailDefault": {"mirror": "MEDIUM"}} every layer from those
 * levels ({@code setLayerDefaultDetail}, keys shown, effect, glint and mirror) and {@code "layerDetail": {"mist":
 * {"shown": "MEDIUM"}}} the layers of that name ({@code setLayerDetail}; engines/rounds/shaders, engines/rounds/glint).
 */
public final class Round
{
	public final List<Scene> scenes;

	private Round(List<Scene> scenes)
	{
		this.scenes = Collections.unmodifiableList(scenes);
	}

	public static final class Scene
	{
		public String id, page, atlasDir;
		public final List<Transfer> transfers = new ArrayList<>();
		public final List<Hook> hooks = new ArrayList<>();
		public float tintAt = -1, tintSeconds, speedY, resizeAt = -1;
		public int resizeWidth, resizeHeight;
		public Color tint;
		/** The game's seed for the SEQUENCE layers; null: the pages' own. */
		public Integer sequenceSeed;
		/** Frames saved after the last still, {@code filmFps} a second; 0: none. */
		public int filmFrames, filmFps;
		/** The camera's zoom at 0 s and at 12 s, and whether the reader dollies it. */
		public float zoomFrom = 1, zoomTo = 1;
		public boolean dolly;
		/** The level the scene is drawn at, every layer's rule, and the named layers' own (r464). */
		public Enum_Detail detail = Enum_Detail.HIGH;
		public LayerDetail detailDefault = LayerDetail.DEFAULT;
		public final Map<String, LayerDetail> layerDetails = new LinkedHashMap<>();

		/** The scene's zoom {@code at} seconds in: straight from its first at 0 s to its second at 12 s. */
		public float zoomAt(float at)
		{return zoomFrom + (zoomTo - zoomFrom) * (at / 12);}
	}

	/** An EMPTY layer's hook from a round: stretches the n-th atlas region of that name over every tile (r177). */
	public static final class Hook
	{
		public String layer, region;
		public int position;
		/** Round a planet, the region stood on its tile's foot: placed and turned as the planet says (r347). */
		public boolean planet;
	}

	/** A cross-fade {@code at} seconds into the scroll; {@code page} null: into the page on screen. */
	public static final class Transfer
	{
		public float at, seconds;
		public String page, atlasDir;
		public TransfertStyle style = TransfertStyle.FADE;
	}

	/** Reads {@code roundDir}/round.json. */
	public static Round read(Path roundDir)
	{
		JsonValue round;
		try
		{round = new JsonReader().parse(Files.readString(roundDir.resolve("round.json")));}
		catch (IOException e)
		{throw new UncheckedIOException(e);}
		List<Scene> scenes = new ArrayList<>();
		for (JsonValue s = round.get("scenes").child; s != null; s = s.next)
		{
			Scene scene = new Scene();
			scene.id = s.getString("id");
			scene.page = s.getString("page");
			scene.atlasDir = s.getString("atlasDir");
			scene.speedY = s.getFloat("speedY", 0);
			if (s.has("sequenceSeed"))
				scene.sequenceSeed = s.getInt("sequenceSeed");
			JsonValue zoom = s.get("zoom");
			if (zoom != null)
			{
				scene.zoomFrom = zoom.getFloat(0);
				scene.zoomTo = zoom.getFloat(1);
			}
			scene.dolly = s.getBoolean("dolly", false);
			scene.detail = Enum_Detail.valueOf(s.getString("detail", "HIGH"));
			scene.detailDefault = layerDetail(s.get("detailDefault"));
			JsonValue layerDetails = s.get("layerDetail");
			if (layerDetails != null)
				for (JsonValue d = layerDetails.child; d != null; d = d.next)
					scene.layerDetails.put(d.name, layerDetail(d));
			JsonValue film = s.get("film");
			if (film != null)
			{
				scene.filmFps = film.getInt("fps", 30);
				scene.filmFrames = Math.round(film.getFloat("seconds") * scene.filmFps);
			}
			JsonValue resize = s.get("resize");
			if (resize != null)
			{
				scene.resizeAt = resize.getFloat("at");
				scene.resizeWidth = resize.get("size").getInt(0);
				scene.resizeHeight = resize.get("size").getInt(1);
			}
			JsonValue transfers = s.get("transfer");
			if (transfers != null)
				for (JsonValue t = transfers.isArray() ? transfers.child : transfers; t != null; t = transfers.isArray() ? t.next : null)
				{
					Transfer transfer = new Transfer();
					transfer.at = t.getFloat("at");
					transfer.seconds = t.getFloat("seconds");
					transfer.page = t.getString("page", null);
					transfer.atlasDir = t.getString("atlasDir", scene.atlasDir);
					transfer.style = style(t.get("style"));
					scene.transfers.add(transfer);
				}
			JsonValue hooks = s.get("hooks");
			if (hooks != null)
				for (JsonValue h = hooks.child; h != null; h = h.next)
				{
					Hook hook = new Hook();
					hook.layer = h.name;
					hook.region = h.getString("region");
					hook.position = h.getInt("position", 0);
					hook.planet = h.getBoolean("planet", false);
					scene.hooks.add(hook);
				}
			JsonValue tint = s.get("tint");
			if (tint != null)
			{
				scene.tintAt = tint.getFloat("at");
				scene.tintSeconds = tint.getFloat("seconds");
				float[] c = tint.get("color").asFloatArray();
				scene.tint = new Color(c[0], c[1], c[2], c[3]);
			}
			scenes.add(scene);
		}
		return new Round(scenes);
	}

	/**
	 * A transfer's {@code "style": {"kind": "DEPTH_STAGGER", "stagger": 1}} or
	 * {@code {"kind": "DISSOLVE", "patches": 5, "softness": 0.08, "stagger": 0.5}} or
	 * {@code {"kind": "THROUGH_COLOR", "color": "ffffff", "stagger": 0}} or
	 * {@code {"kind": "FOG_CREEP", "color": "eef2f7", "stagger": 1}}; none, or another kind: FADE.
	 */
	static TransfertStyle style(JsonValue style)
	{
		String kind = style == null ? null : style.getString("kind", null);
		if ("DEPTH_STAGGER".equals(kind))
			return TransfertStyle.depthStagger(style.getFloat("stagger", 0));
		if ("DISSOLVE".equals(kind))
			return TransfertStyle.dissolve(style.getFloat("patches", 5), style.getFloat("softness", 0.08f), style.getFloat("stagger", 0));
		if ("THROUGH_COLOR".equals(kind))
			return TransfertStyle.throughColor(Color.valueOf(style.getString("color", "ffffff")), style.getFloat("stagger", 0));
		if ("FOG_CREEP".equals(kind))
			return TransfertStyle.fogCreep(Color.valueOf(style.getString("color", "ffffff")), style.getFloat("stagger", 0));
		return TransfertStyle.FADE;
	}

	/** A {@code {"shown": "HIGH", "effect": ..., "glint": ..., "mirror": ...}} over {@link LayerDetail#DEFAULT}; none: it. */
	static LayerDetail layerDetail(JsonValue levels)
	{
		LayerDetail detail = LayerDetail.DEFAULT;
		if (levels == null)
			return detail;
		if (levels.has("shown"))
			detail = detail.shownFrom(Enum_Detail.valueOf(levels.getString("shown")));
		if (levels.has("effect"))
			detail = detail.effectFrom(Enum_Detail.valueOf(levels.getString("effect")));
		if (levels.has("glint"))
			detail = detail.glintFrom(Enum_Detail.valueOf(levels.getString("glint")));
		if (levels.has("mirror"))
			detail = detail.mirrorFrom(Enum_Detail.valueOf(levels.getString("mirror")));
		return detail;
	}

	/** The n-th region of that name, in atlas order, as a page names its layers' images: a hook's region. */
	public static TextureRegion findRegion(TextureAtlas atlas, String name, int position)
	{
		int seen = 0;
		for (TextureAtlas.AtlasRegion region : atlas.getRegions())
			if (region.name.equals(name) && seen++ == position)
				return region;
		throw new IllegalArgumentException("no region " + name + " #" + position + " in the atlas");
	}
}
