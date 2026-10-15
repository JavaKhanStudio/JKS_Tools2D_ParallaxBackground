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
import jks.tools2d.parallax.pages.Sequence_Segment;
import jks.tools2d.parallax.pages.Utils_Page_Json;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * The pines lab (r421, Simon: "Turn this into a lab"): R1's ds8-forest drawn twice by the real reader in WebGL, its
 * front pines in each panel cut with layer.py's misty_pines rule as it ships (on) or forced off, every other layer the
 * same pixels. The rule keys out the pale, misty farther pines dreamshaper_8 paints at the forest's foot; on ds8-forest
 * it changes two of the front's four strips (l3_1, l3_2: core/test-data/pines/diff.json), so the front choice can hold
 * one strip still instead of the page's cycle, and the marks lay red where on and off differ, riding with their strip.
 * Data: core/test-data/pines/make_lab.py. Opened with pines-lab.html: tools/pines-lab.sh.
 * <p>
 * {@code &clip=1}: nothing moves by itself, {@code window.pinesLabAct(seconds)} steps both hearts at 1/60 s.
 */
public class PinesLab extends ApplicationAdapter implements LabKit.Clip
{
	static final String PAGE = "pines-lab/forest.jplax", DIFF = "pines-lab/diff.json";
	/** The page's front: a SEQUENCE of four strips, l3_0 to l3_3. */
	static final int FRONT = 3;
	static final float WORLD_WIDTH = FogLab.WORLD_WIDTH, WORLD_HEIGHT = FogLab.WORLD_HEIGHT;
	static final int FRAME_WIDTH = FogLab.FRAME_WIDTH, FRAME_HEIGHT = FogLab.FRAME_HEIGHT;
	static final String[] RULE = { "misty_pines on (ships)", "misty_pines off" };
	static final String[] VIEWS = { "the page", "the pines alone", "the foot, x2.5" };
	/** The foot view's zoom: the camera sees 1 / 2.5 of the world, its bottom on the page's bottom, its middle on the page's. */
	static final float FOOT_ZOOM = 0.4f;
	static final String[] MARKS = { "none", "red: what differs" };
	static final float SCROLL = 60;

	private final Parallax_Heart[] hearts = new Parallax_Heart[2];
	private final OrthographicCamera[] cameras = new OrthographicCamera[2];
	/** The front strips misty_pines changes, by region name, and the pixels it moves in each. */
	private String[] changed;
	private int[] moved;
	private String[] fronts;
	private final SelectElement[] picks = new SelectElement[2];
	private SelectElement view, front, marks;
	private InputElement scroll;
	private Element readout;
	private final int[][] showing = { { -1, -1, -1, -1 }, { -1, -1, -1, -1 } };
	private boolean clip;
	private SpriteBatch batch;

	@Override
	public void create()
	{
		clip = "1".equals(com.google.gwt.user.client.Window.Location.getParameter("clip"));
		JsonValue diff = new JsonReader().parse(Gdx.files.internal(DIFF)).get("changed");
		changed = new String[diff.size];
		moved = new int[diff.size];
		for (int i = 0; i < diff.size; i++)
		{
			changed[i] = diff.get(i).name;
			moved[i] = diff.get(i).asInt();
		}
		fronts = new String[1 + changed.length];
		fronts[0] = "the page's cycle";
		for (int i = 0; i < changed.length; i++)
			fronts[i + 1] = changed[i] + " alone";
		batch = new SpriteBatch();
		for (int i = 0; i < hearts.length; i++)
		{
			OrthographicCamera camera = cameras[i] = new OrthographicCamera();
			camera.setToOrtho(false, WORLD_WIDTH, WORLD_HEIGHT);
			hearts[i] = new Parallax_Heart(camera, batch, WORLD_WIDTH, WORLD_HEIGHT);
		}
		buildControls();
		applyControls();
		if (clip)
			LabKit.exportClip("pinesLab", this);
	}

	private void buildControls()
	{
		Document document = Document.get();
		LabControls.panelLabels(document, "pines-labels", "A", "B");
		Element titles = document.getElementById("pines-labels");
		picks[0] = LabControls.panelChoice(document, (Element) titles.getChild(0), "A: ", "pinesA", "A's front pines (cut with misty_pines on, as it ships, or off)", RULE, 0);
		picks[1] = LabControls.panelChoice(document, (Element) titles.getChild(1), "B: ", "pinesB", "B's front pines (cut with misty_pines on, as it ships, or off)", RULE, 1);
		view = LabControls.stageChoice(document, "pines-labels", "view", "view (the whole page; the front pines alone on the sky; the page's bottom, where the misty pines are, 2.5 times closer)", VIEWS, 0);
		Element controls = document.getElementById("pines-controls");
		front = LabControls.choice(document, controls, "front", "front strip (the page's cycle of four, or one strip the rule changes, repeated)", fronts, 0);
		marks = LabControls.choice(document, controls, "marks", "marks (red over the pixels misty_pines keys out or keeps)", MARKS, 0);
		scroll = LabControls.slider(document, controls, "cameraScroll", "camera scroll (units a second)", -240, 240, 5, SCROLL);
		readout = LabControls.readout(document, controls, "pines-readout");
	}

	private void applyControls()
	{
		int v = LabControls.read(view), f = LabControls.read(front), m = LabControls.read(marks);
		float sc = LabControls.read(scroll);
		for (int i = 0; i < hearts.length; i++)
		{
			int r = LabControls.read(picks[i]);
			if (showing[i][0] != r || showing[i][1] != v || showing[i][2] != f || showing[i][3] != m)
			{
				hearts[i].setPage(page(r == 1, v, f, m == 1));
				cameras[i].zoom = v == 2 ? FOOT_ZOOM : 1;
				cameras[i].position.y = WORLD_HEIGHT * cameras[i].zoom / 2;
				cameras[i].update();
				showing[i][0] = r;
				showing[i][1] = v;
				showing[i][2] = f;
				showing[i][3] = m;
			}
			hearts[i].screenSpeedConstantX = sc;
		}
		StringBuilder line = new StringBuilder("misty_pines changes ");
		for (int i = 0; i < changed.length; i++)
			line.append(i == 0 ? "" : ", ").append(changed[i]).append(" (").append(moved[i]).append(" px)");
		line.append(" of the front's four strips; the other layers are the same pixels in both panels");
		readout.setInnerText(line.toString());
	}

	/** ds8-forest, its front cut with misty_pines off or on, view {@code v}, front strip {@code f}, marks or not. */
	private WholePage_Model page(boolean off, int v, int f, boolean marked)
	{
		WholePage_Model page = Utils_Page_Json.loadPage(PAGE);
		Parallax_Model pines = page.pageModel.pageList.get(FRONT);
		Parallax_Model marks = Utils_Page_Json.loadPage(PAGE).pageModel.pageList.get(FRONT);
		if (f > 0)
		{
			String only = changed[f - 1];
			pines.sequenceSegments.removeIf(s -> !s.regionName.equals(only));
			marks.sequenceSegments.removeIf(s -> !s.regionName.equals(only));
		}
		for (Sequence_Segment s : pines.sequenceSegments)
			if (off && isChanged(s.regionName))
				s.regionName = s.regionName + "_off";
		for (Sequence_Segment s : marks.sequenceSegments)
			s.regionName = "m" + s.regionName.substring(s.regionName.lastIndexOf('_') + 1);
		marks.name = "marks";
		if (v == 1)
		{
			page.pageModel.pageList.clear();
			page.pageModel.pageList.add(pines);
		}
		if (marked)
			page.pageModel.pageList.add(marks);
		return page;
	}

	private boolean isChanged(String region)
	{
		for (String c : changed)
			if (c.equals(region))
				return true;
		return false;
	}

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
			LabKit.panel(i * (FRAME_WIDTH + 4), height, FRAME_WIDTH, FRAME_HEIGHT, FRAME_HEIGHT, 0);
			hearts[i].render();
		}
		LabKit.endPanels();
	}

	@Override
	public void dispose()
	{batch.dispose();}
}
