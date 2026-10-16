package jks.tools2d.parallax.browsertest;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.SelectElement;

import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.pages.Parallax_Model;
import jks.tools2d.parallax.pages.Utils_Page_Json;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * The hills lab (r384, Simon: "after is way worse right now. Try something else, and make this a lab"): r365's AI forest
 * page drawn twice by the real reader in WebGL, its hills layer (l2) in each panel painted by the recipe that panel's
 * choice picks, every other layer the same pixels. The recipes are tools/r384-hills/try.py's, painted over three seeds
 * (7020 is the page's own), repacked by core/test-data/hills/make_lab.py with their measures (candidates.json): what
 * ships (img2img 0.65, flat), r384's patch (0.70, the pale ranges behind), and the second round: the hills inpainted
 * inside their shape only (the sky kept, nothing painted behind), and img2img guided by the shape's outline. The view
 * shows the page, the page without the pines in front, or the hills alone on the sky. Opened with hills-lab.html:
 * tools/hills-lab.sh.
 * <p>
 * {@code &clip=1}: nothing moves by itself, {@code window.hillsLabAct(seconds)} steps both hearts at 1/60 s.
 */
public class HillsLab extends ApplicationAdapter implements LabKit.Clip
{
	static final String PAGE = "hills-lab/forest.jplax", CANDIDATES = "hills-lab/candidates.json";
	/** The page's hills layer: its index in the page's list, the region a candidate replaces. */
	static final int HILLS = 2, PINES = 3;
	static final float WORLD_WIDTH = FogLab.WORLD_WIDTH, WORLD_HEIGHT = FogLab.WORLD_HEIGHT;
	/** Two panels side by side, each the round's whole 16:9 frame. */
	static final int FRAME_WIDTH = FogLab.FRAME_WIDTH, FRAME_HEIGHT = FogLab.FRAME_HEIGHT;
	static final String[] VIEWS = { "the page", "without the pines", "hills alone" };
	/** What the lab opens on: A what ships, B the first inpainted one, the page's own seed, scrolling. */
	static final int A = 0, B = 2;
	static final float SCROLL = 40;

	private final Parallax_Heart[] hearts = new Parallax_Heart[2];
	private String[] names, shown;
	private JsonValue candidates;
	private int[] seeds;
	private SelectElement[] picks = new SelectElement[2];
	private SelectElement seed, view;
	private InputElement scroll;
	private Element readout;
	/** What each heart shows: its candidate, seed and view, -1 before the first. */
	private final int[][] showing = { { -1, -1, -1 }, { -1, -1, -1 } };
	private boolean clip;
	private SpriteBatch batch;

	@Override
	public void create()
	{
		clip = "1".equals(com.google.gwt.user.client.Window.Location.getParameter("clip"));
		JsonValue root = new JsonReader().parse(Gdx.files.internal(CANDIDATES));
		seeds = root.get("seeds").asIntArray();
		candidates = root.get("candidates");
		names = new String[candidates.size];
		shown = new String[candidates.size];
		for (int i = 0; i < names.length; i++)
		{
			names[i] = candidates.get(i).getString("name");
			shown[i] = candidates.get(i).getString("shown");
		}
		batch = new SpriteBatch();
		for (int i = 0; i < hearts.length; i++)
		{
			OrthographicCamera camera = new OrthographicCamera();
			camera.setToOrtho(false, WORLD_WIDTH, WORLD_HEIGHT);
			hearts[i] = new Parallax_Heart(camera, batch, WORLD_WIDTH, WORLD_HEIGHT);
		}
		buildControls();
		applyControls();
		if (clip)
			LabKit.exportClip("hillsLab", this);
	}

	private void buildControls()
	{
		Document document = Document.get();
		LabControls.panelLabels(document, "hills-labels", "A", "B");
		Element titles = document.getElementById("hills-labels");
		picks[0] = LabControls.panelChoice(document, (Element) titles.getChild(0), "A: ", "hillsA", "A's hills (the recipe that painted the left panel's hills layer)", shown, A);
		picks[1] = LabControls.panelChoice(document, (Element) titles.getChild(1), "B: ", "hillsB", "B's hills (the recipe that painted the right panel's hills layer)", shown, B);
		view = LabControls.stageChoice(document, "hills-labels", "view", "view (the whole page; without the pines in front; the hills alone on the sky)", VIEWS, 0);
		Element controls = document.getElementById("hills-controls");
		String[] seedNames = new String[seeds.length];
		for (int i = 0; i < seeds.length; i++)
			seedNames[i] = seeds[i] + (i == 0 ? " (the page's own)" : "");
		seed = LabControls.choice(document, controls, "seed", "seed (the same seed in both panels: 7020 painted the page's hills)", seedNames, 0);
		scroll = LabControls.slider(document, controls, "cameraScroll", "camera scroll (units a second)", -160, 160, 5, SCROLL);
		readout = LabControls.readout(document, controls, "hills-readout");
	}

	/** Each heart's page as the choices say (built again only when they change), the scroll, the readout. */
	private void applyControls()
	{
		int s = LabControls.read(seed), v = LabControls.read(view);
		float sc = LabControls.read(scroll);
		StringBuilder line = new StringBuilder();
		for (int i = 0; i < hearts.length; i++)
		{
			int c = LabControls.read(picks[i]);
			if (showing[i][0] != c || showing[i][1] != s || showing[i][2] != v)
			{
				hearts[i].setPage(page(c, s, v));
				showing[i][0] = c;
				showing[i][1] = s;
				showing[i][2] = v;
			}
			hearts[i].screenSpeedConstantX = sc;
			JsonValue m = candidates.get(c).get("measures").get(s);
			line.append(i == 0 ? "A " : "; B ").append(names[c]).append(": ").append(candidates.get(c).getString("how")).append(" at ")
					.append(candidates.get(c).getFloat("denoise")).append(", colour spread inside ").append(m.getFloat("std"))
					.append(" (today's ").append(candidates.get(0).get("measures").get(s).getFloat("std")).append("), shape kept ")
					.append(Math.round(100 * m.getFloat("iou"))).append("%");
		}
		readout.setInnerText(line.toString());
	}

	/** The forest page, its hills painted by candidate {@code c} at seed index {@code s}, the layers {@code v} keeps. */
	private WholePage_Model page(int c, int s, int v)
	{
		WholePage_Model page = Utils_Page_Json.loadPage(PAGE);
		Parallax_Model hills = page.pageModel.pageList.get(HILLS);
		hills.regionName = "h_" + names[c] + "_" + seeds[s];
		if (v >= 1)
			page.pageModel.pageList.remove(PINES);
		if (v == 2)
		{
			page.pageModel.pageList.clear();
			page.pageModel.pageList.add(hills);
		}
		return page;
	}

	/** Steps both hearts by {@code seconds}, at 1/60 s: the clip's clock. */
	@Override
	public void act(float seconds)
	{
		applyControls();
		LabKit.clip(seconds, hearts);
	}

	@Override
	public void render()
	{
		if (!clip)
		{
			applyControls();
			LabKit.act(LabKit.liveDelta(), hearts);
		}
		int height = Gdx.graphics.getBackBufferHeight();
		LabKit.beginPanels();
		for (int i = 0; i < hearts.length; i++)
		{
			if (LabKit.panel(i * (FRAME_WIDTH + 4), height, FRAME_WIDTH, FRAME_HEIGHT, FRAME_HEIGHT, 0))
				hearts[i].render();
		}
		LabKit.endPanels();
	}

	@Override
	public void dispose()
	{batch.dispose();}
}
