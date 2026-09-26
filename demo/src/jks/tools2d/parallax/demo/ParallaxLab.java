package jks.tools2d.parallax.demo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ScreenUtils;

import jks.tools2d.parallax.heart.Gvars_Parallax;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.pages.Utils_Page_Json;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * The grading lab (r73): shows the scenes of one round blind, one at a time, scrolling, and saves the grade given to
 * each in the round's grades.json, so the next round (and the parallax-pages skill) learns what reads as good.
 * <p>
 * A round is a folder holding round.json (the scenes in the order shown, each a page file and the folder of its atlas,
 * both relative to the working directory, and what the scene tests, hidden until revealed) and the page files. Run from
 * the repository root:
 *
 * <pre>
 * ./gradlew :demo:lab                                   the latest round under demo/lab
 * ./gradlew :demo:lab --args="demo/lab/round1"
 * ./gradlew :demo:lab --args="demo/lab/round1 --shots demo/build/lab/round1"   stills of every scene, then exits
 * </pre>
 *
 * Keys: 1-5 grade (5 = best) and go on, ENTER/BACKSPACE next/previous, SPACE pause, LEFT/RIGHT scroll by hand, UP/DOWN
 * scroll speed, R restart the scene, H show what the scene tests.
 */
public class ParallaxLab extends ApplicationAdapter
{
	private static final float SPEED = 60, MANUAL_SPEED = 400;
	private static final float[] SHOT_TIMES = { 0, 6, 12 };

	private final Path roundDir;
	private final Path shotsDir;
	private final List<Scene> scenes = new ArrayList<>();
	private final Map<String, Integer> grades = new LinkedHashMap<>();

	private Parallax_Heart heart;
	private int current;
	private boolean paused, reveal;
	private float speedFactor = 1, sinceGrade = -1;

	private SpriteBatch hudBatch;
	private ShapeRenderer shapes;
	private BitmapFont font;

	private static final class Scene
	{
		String id, page, atlasDir, about;
	}

	public ParallaxLab(Path roundDir, Path shotsDir)
	{
		this.roundDir = roundDir;
		this.shotsDir = shotsDir;
	}

	public static void main(String[] args) throws IOException
	{
		Path round = null, shots = null;
		for (int i = 0; i < args.length; i++)
		{
			if ("--shots".equals(args[i]))
				shots = Paths.get(args[++i]);
			else
				round = Paths.get(args[i]);
		}
		if (round == null)
			round = latestRound(Paths.get("demo/lab"));

		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setTitle("Parallax lab - " + round.getFileName());
		config.setWindowIcon("parallaxIcon.png");
		config.setWindowedMode(1280, 720);
		config.useVsync(true);
		new Lwjgl3Application(new ParallaxLab(round, shots), config);
	}

	private static Path latestRound(Path lab) throws IOException
	{
		try (java.util.stream.Stream<Path> dirs = Files.list(lab))
		{
			return dirs.filter(d -> Files.isRegularFile(d.resolve("round.json"))).sorted().reduce((a, b) -> b)
					.orElseThrow(() -> new IOException("no round.json under " + lab.toAbsolutePath()));
		}
	}

	@Override
	public void create()
	{
		JsonValue round = new JsonReader().parse(new FileHandle(roundDir.resolve("round.json").toFile()));
		for (JsonValue s = round.get("scenes").child; s != null; s = s.next)
		{
			Scene scene = new Scene();
			scene.id = s.getString("id");
			scene.page = s.getString("page");
			scene.atlasDir = s.getString("atlasDir");
			scene.about = s.getString("about", "");
			scenes.add(scene);
		}
		readGrades();

		heart = new Parallax_Heart();
		hudBatch = new SpriteBatch();
		shapes = new ShapeRenderer();
		font = new BitmapFont();
		font.getData().setScale(1.25f);

		if (shotsDir != null)
		{
			takeShots();
			Gdx.app.exit();
			return;
		}

		current = firstUngraded();
		show(current);
		Gdx.input.setInputProcessor(new InputAdapter()
		{
			@Override
			public boolean keyDown(int keycode)
			{
				if (keycode >= Keys.NUM_1 && keycode <= Keys.NUM_5)
					grade(keycode - Keys.NUM_0);
				else if (keycode >= Keys.NUMPAD_1 && keycode <= Keys.NUMPAD_5)
					grade(keycode - Keys.NUMPAD_0);
				else if (keycode == Keys.ENTER || keycode == Keys.PAGE_DOWN)
					show(Math.min(current + 1, scenes.size()));
				else if (keycode == Keys.BACKSPACE || keycode == Keys.PAGE_UP)
					show(Math.max(current - 1, 0));
				else if (keycode == Keys.SPACE)
					paused = !paused;
				else if (keycode == Keys.UP)
					speedFactor = Math.min(speedFactor * 2, 8);
				else if (keycode == Keys.DOWN)
					speedFactor = Math.max(speedFactor / 2, 0.25f);
				else if (keycode == Keys.R)
					show(current);
				else if (keycode == Keys.H)
					reveal = !reveal;
				else
					return false;
				return true;
			}
		});
	}

	/** Shows scene {@code index}, from its start; {@code scenes.size()} is the summary. */
	private void show(int index)
	{
		current = index;
		sinceGrade = -1;
		if (index >= scenes.size())
			return;
		Scene scene = scenes.get(index);
		WholePage_Model page = Utils_Page_Json.loadPage(new FileHandle(Paths.get(scene.page).toFile()));
		heart.relativePath = Paths.get(scene.atlasDir).toAbsolutePath().toString();
		heart.setPage(page);
		heart.parallaxReader.resetPositions();
	}

	private void grade(int value)
	{
		if (current >= scenes.size())
			return;
		grades.put(scenes.get(current).id, value);
		writeGrades();
		sinceGrade = 0;
	}

	private int firstUngraded()
	{
		for (int i = 0; i < scenes.size(); i++)
			if (!grades.containsKey(scenes.get(i).id))
				return i;
		return scenes.size();
	}

	@Override
	public void render()
	{
		float delta = Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f);

		// A grade shows for a moment, then the next scene not graded yet comes up.
		if (sinceGrade >= 0 && (sinceGrade += delta) > 0.7f)
		{
			int next = firstUngradedAfter(current);
			show(next);
		}

		ScreenUtils.clear(Color.BLACK);
		if (current < scenes.size())
		{
			heart.screenSpeedConstantX = paused ? 0 : SPEED * speedFactor;
			if (Gdx.input.isKeyPressed(Keys.LEFT))
				heart.screenSpeedConsumableX = -MANUAL_SPEED;
			if (Gdx.input.isKeyPressed(Keys.RIGHT))
				heart.screenSpeedConsumableX = MANUAL_SPEED;
			heart.act(delta);
			heart.render();
			drawHud();
		}
		else
			drawSummary();
	}

	private int firstUngradedAfter(int index)
	{
		for (int i = index + 1; i < scenes.size(); i++)
			if (!grades.containsKey(scenes.get(i).id))
				return i;
		return firstUngraded();
	}

	private void drawHud()
	{
		Scene scene = scenes.get(current);
		Integer given = grades.get(scene.id);
		String line1 = "Scene " + (current + 1) + " / " + scenes.size() + "   " + scene.id
				+ (given != null ? "   graded " + given : "") + (paused ? "   PAUSED" : "")
				+ (speedFactor != 1 ? "   speed x" + speedFactor : "");
		String line2 = "1-5 grade (5 best)   ENTER / BACKSPACE next / previous   SPACE pause   LEFT/RIGHT scroll   UP/DOWN speed   R restart   H what it tests";
		int height = reveal ? 84 : 58;
		bar(height);
		hudBatch.begin();
		float top = Gdx.graphics.getHeight() - 8;
		font.setColor(given != null ? Color.GOLD : Color.WHITE);
		font.draw(hudBatch, line1, 10, top);
		font.setColor(Color.LIGHT_GRAY);
		font.draw(hudBatch, line2, 10, top - 24);
		if (reveal)
		{
			font.setColor(Color.SKY);
			font.draw(hudBatch, scene.about, 10, top - 48);
		}
		hudBatch.end();
	}

	private void drawSummary()
	{
		ScreenUtils.clear(0.08f, 0.08f, 0.1f, 1);
		hudBatch.begin();
		float y = Gdx.graphics.getHeight() - 16;
		font.setColor(Color.WHITE);
		font.draw(hudBatch, "Round " + roundDir.getFileName() + ": " + grades.size() + " of " + scenes.size()
				+ " graded   (BACKSPACE to go back)", 16, y);
		font.setColor(Color.LIGHT_GRAY);
		font.draw(hudBatch, "saved in " + roundDir.resolve("grades.json"), 16, y - 24);
		y -= 60;
		for (Scene scene : scenes)
		{
			Integer given = grades.get(scene.id);
			// What a scene tests stays hidden until it is graded: skipping ahead with ENTER must not unblind the rest.
			font.setColor(given == null ? Color.GRAY : given >= 4 ? Color.GREEN : given <= 2 ? Color.SALMON : Color.WHITE);
			font.draw(hudBatch, scene.id + "   " + (given == null ? "-   not graded yet" : given + "   " + scene.about), 16, y);
			y -= 24;
		}
		hudBatch.end();
	}

	private void bar(int height)
	{
		Gdx.gl.glEnable(GL20.GL_BLEND);
		shapes.begin(ShapeRenderer.ShapeType.Filled);
		shapes.setColor(0, 0, 0, 0.6f);
		shapes.rect(0, Gdx.graphics.getHeight() - height, Gdx.graphics.getWidth(), height);
		shapes.end();
	}

	/** Stills of every scene at a few moments of the same scroll, without the HUD, for a contact sheet. */
	private void takeShots()
	{
		try
		{Files.createDirectories(shotsDir);}
		catch (IOException e)
		{throw new RuntimeException(e);}
		float step = 1 / 60f;
		heart.screenSpeedConstantX = SPEED;
		for (int i = 0; i < scenes.size(); i++)
		{
			show(i);
			float time = 0;
			for (float at : SHOT_TIMES)
			{
				for (; time < at; time += step)
					heart.act(step);
				ScreenUtils.clear(Color.BLACK);
				heart.render();
				Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
				PixmapIO.writePNG(new FileHandle(shotsDir.resolve(scenes.get(i).id + "-t" + (int) at + ".png").toFile()), pixmap, 6, true);
				pixmap.dispose();
			}
		}
	}

	private void readGrades()
	{
		Path file = roundDir.resolve("grades.json");
		if (!Files.isRegularFile(file))
			return;
		JsonValue json = new JsonReader().parse(new FileHandle(file.toFile()));
		for (JsonValue g = json.get("grades").child; g != null; g = g.next)
			grades.put(g.name, g.getInt("grade"));
	}

	/** Rewritten whole on every grade, through a temporary file: a crash never leaves half a file. */
	private void writeGrades()
	{
		StringBuilder out = new StringBuilder("{\n  \"round\": \"" + roundDir.getFileName() + "\",\n  \"updated\": \""
				+ OffsetDateTime.now() + "\",\n  \"grades\": {");
		String separator = "\n";
		for (Scene scene : scenes)
			if (grades.containsKey(scene.id))
			{
				out.append(separator).append("    \"").append(scene.id).append("\": {\"grade\": ").append(grades.get(scene.id)).append('}');
				separator = ",\n";
			}
		out.append("\n  }\n}\n");
		try
		{
			Path file = roundDir.resolve("grades.json"), temp = roundDir.resolve("grades.json.tmp");
			Files.write(temp, out.toString().getBytes(StandardCharsets.UTF_8));
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (IOException e)
		{Gdx.app.error("lab", "could not save the grades", e);}
	}

	@Override
	public void resize(int width, int height)
	{
		heart.resize(width, height);
		hudBatch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
		shapes.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
	}

	@Override
	public void dispose()
	{
		heart.dispose();
		hudBatch.dispose();
		shapes.dispose();
		font.dispose();
		Gvars_Parallax.getManager().dispose();
	}
}
