package jks.tools2d.parallax.editor.driver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent;
import com.badlogic.gdx.scenes.scene2d.utils.Layout;
import com.kotcrab.vis.ui.widget.color.BasicColorPicker;
import com.kotcrab.vis.ui.widget.spinner.IntSpinnerModel;
import com.kotcrab.vis.ui.widget.spinner.Spinner;

import jks.tools2d.libgdxutils.JksNumberSlider;
import jks.tools2d.libgdxutils.JksTextureList;
import jks.tools2d.parallax.editor.gvars.GVars_Heart_Editor;
import jks.tools2d.parallax.editor.gvars.GVars_UI;
import jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition;
import jks.tools2d.parallax.editor.vue.Vue_Edition;
import jks.tools2d.parallax.editor.vue.Vue_Selection;

/**
 * Lets another program drive the editor, for scripted demos and videos (the presenter tool). Off unless the editor is
 * launched with {@code --driver-port=N}; it listens on 127.0.0.1 only. One command per line, one reply per command,
 * {@code ok ...} or {@code err ...}. Commands run on the render thread, between two frames.
 *
 * <pre>
 * list                  "ok N", then N lines "name type x y w h": the named controls on screen
 * bounds TARGET         "ok x y w h": window pixels, origin at the top left; "preview.view" is the parallax preview
 * click TARGET          presses and releases the left button at the control's center
 * wheel TARGET N        moves the mouse to the control's center and turns the wheel N notches (negative: up)
 * set TARGET VALUE      slider, number slider, text field, check box (true/false), select box, spinner,
 *                       image list (index), color picker (#rrggbb)
 * open FILE             opens a .plaxpj/.plax/.jplax/.atlas as the start screen would (relative to editor/)
 * shot FILE.png         writes the next rendered frame
 * </pre>
 *
 * A TARGET is a control's name ({@code texture.sizeRatio}), or {@code text:LABEL} for a button found by its text: the
 * buttons of VisUI's dialogs (Yes, No, OK...) have no name.
 */
public final class EditorDriver
{
	private static final long REPLY_TIMEOUT_SECONDS = 10;
	/** The parallax preview is drawn in a GL viewport, not by an actor, so it gets a name of its own. */
	private static final String PREVIEW_VIEW = "preview.view";

	private final ServerSocket server;
	/** A screenshot waiting for the end of the next frame, with the future its reply goes to. */
	private volatile String pendingShot;
	private volatile CompletableFuture<String> pendingShotReply;

	private EditorDriver(int port) throws IOException
	{server = new ServerSocket(port, 4, InetAddress.getLoopbackAddress());}

	/** Starts listening, or returns null (and logs why) when the port cannot be opened. */
	public static EditorDriver start(int port)
	{
		try
		{
			EditorDriver driver = new EditorDriver(port);
			Thread accept = new Thread(driver::acceptLoop, "editor-driver");
			accept.setDaemon(true);
			accept.start();
			Gdx.app.log("EditorDriver", "listening on 127.0.0.1:" + port);
			return driver;
		}
		catch (IOException e)
		{
			Gdx.app.error("EditorDriver", "cannot listen on port " + port, e);
			return null;
		}
	}

	/** Reads {@code --driver-port=N} out of the launch arguments, 0 when absent. */
	public static int portFrom(String[] args)
	{
		for (String arg : args)
			if (arg.startsWith("--driver-port="))
				return Integer.parseInt(arg.substring("--driver-port=".length()));
		return 0;
	}

	public void stop()
	{
		try
		{server.close();}
		catch (IOException e)
		{
			// closing anyway
		}
	}

	/** Called at the end of every frame, before the buffers are swapped: the only moment the frame can be read. */
	public void afterRender()
	{
		String file = pendingShot;
		if (file == null)
			return;
		pendingShot = null;

		CompletableFuture<String> reply = pendingShotReply;
		try
		{
			Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
			// The frame buffer is upside down and its alpha is whatever the blending left: the PNG must be opaque.
			Pixmap flipped = new Pixmap(pixmap.getWidth(), pixmap.getHeight(), Pixmap.Format.RGBA8888);
			for (int y = 0; y < pixmap.getHeight(); y++)
				for (int x = 0; x < pixmap.getWidth(); x++)
					flipped.drawPixel(x, pixmap.getHeight() - 1 - y, pixmap.getPixel(x, y) | 0xff);
			PixmapIO.writePNG(Gdx.files.absolute(new java.io.File(file).getAbsolutePath()), flipped);
			pixmap.dispose();
			flipped.dispose();
			reply.complete("ok " + file);
		}
		catch (RuntimeException e)
		{reply.complete("err " + e);}
	}

	private void acceptLoop()
	{
		while (!server.isClosed())
		{
			try
			{
				Socket client = server.accept();
				Thread session = new Thread(() -> serve(client), "editor-driver-client");
				session.setDaemon(true);
				session.start();
			}
			catch (IOException e)
			{
				// the server was closed
			}
		}
	}

	private void serve(Socket client)
	{
		try (client;
				BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
				PrintWriter out = new PrintWriter(client.getOutputStream(), true, StandardCharsets.UTF_8))
		{
			String line;
			while ((line = in.readLine()) != null)
			{
				if (line.isBlank())
					continue;
				out.println(reply(line.trim()));
			}
		}
		catch (IOException e)
		{
			// the client went away
		}
	}

	private String reply(String line)
	{
		CompletableFuture<String> reply = new CompletableFuture<>();
		Gdx.app.postRunnable(new Runnable()
		{
			@Override
			public void run()
			{
				// After a resize the widgets are rebuilt, and every tab goes back to its first: wait for that.
				if (GVars_Heart_Editor.vue instanceof Vue_Edition && ((Vue_Edition) GVars_Heart_Editor.vue).isRebuildPending())
				{
					if (!reply.isDone())
						Gdx.app.postRunnable(this);
					return;
				}

				try
				{
					String result = execute(line, reply);
					if (result != null)
						reply.complete(result);
				}
				catch (RuntimeException e)
				{reply.complete("err " + e);}
			}
		});

		try
		{
			String text = reply.get(REPLY_TIMEOUT_SECONDS, TimeUnit.SECONDS).replace('\r', ' ');
			// Only "list" answers on several lines; an error message must stay on one.
			return text.startsWith("err") ? text.replace('\n', ' ') : text;
		}
		catch (Exception e)
		{return "err no reply from the editor: " + e;}
	}

	/** Runs one command on the render thread. Returns its reply, or null when {@code reply} is completed later. */
	private String execute(String line, CompletableFuture<String> reply)
	{
		String[] words = line.split(" ", 3);
		String command = words[0].toLowerCase(Locale.ROOT);
		String target = words.length > 1 ? words[1] : null;
		String value = words.length > 2 ? words[2] : null;

		switch (command)
		{
			case "list":
				return list();
			case "open":
				if (target == null)
					return "err usage: open FILE";
				String file = line.substring("open ".length()).trim();
				return Vue_Selection.selectSingleFile(Gdx.files.absolute(new java.io.File(file).getAbsolutePath())) ? "ok" : "err cannot open " + file;
			case "shot":
				if (target == null)
					return "err usage: shot FILE.png";
				pendingShotReply = reply;
				pendingShot = line.substring("shot ".length()).trim();
				return null;
			case "bounds":
			case "click":
			case "set":
			case "wheel":
				if (target == null)
					return "err usage: " + command + " TARGET";
				// A text:LABEL target may hold spaces; only "set" and "wheel" have a value after it.
				if (!command.equals("set") && !command.equals("wheel"))
					target = line.substring(command.length()).trim();
				if (command.equals("bounds") && target.equals(PREVIEW_VIEW))
					return "ok " + format(previewBounds());
				Actor actor = find(target);
				if (actor == null)
					return "err no control on screen: " + target;
				if (command.equals("bounds"))
					return "ok " + format(screenBounds(actor));
				if (command.equals("click"))
					return click(actor);
				if (command.equals("wheel"))
					return value == null ? "err usage: wheel TARGET N" : wheel(actor, Integer.parseInt(value.trim()), reply);
				if (value == null)
					return "err usage: set TARGET VALUE";
				return set(actor, value);
			default:
				return "err unknown command: " + command;
		}
	}

	private static String list()
	{
		List<Actor> named = new ArrayList<>();
		collect(GVars_UI.mainUi.getRoot(), named);
		StringBuilder reply = new StringBuilder("ok ").append(named.size());
		for (Actor actor : named)
			reply.append('\n').append(actor.getName()).append(' ').append(actor.getClass().getSimpleName().isEmpty() ? actor.getClass().getSuperclass().getSimpleName() : actor.getClass().getSimpleName())
					.append(' ').append(format(screenBounds(actor)));
		return reply.toString();
	}

	private static void collect(Actor actor, List<Actor> named)
	{
		if (!actor.isVisible())
			return;
		if (actor.getName() != null)
			named.add(actor);
		if (actor instanceof Group)
			for (Actor child : ((Group) actor).getChildren())
				collect(child, named);
	}

	/** The shown control with this name, or the shown button with this text for {@code text:LABEL}. */
	private static Actor find(String target)
	{
		List<Actor> shown = new ArrayList<>();
		collectAll(GVars_UI.mainUi.getRoot(), shown);

		boolean byText = target.startsWith("text:");
		String wanted = byText ? target.substring("text:".length()) : target;
		for (Actor actor : shown)
		{
			if (!byText && wanted.equals(actor.getName()))
				return actor;
			if (byText && actor instanceof TextButton && wanted.equalsIgnoreCase(((TextButton) actor).getText().toString().trim()))
				return actor;
		}
		return null;
	}

	private static void collectAll(Actor actor, List<Actor> shown)
	{
		if (!actor.isVisible())
			return;
		shown.add(actor);
		if (actor instanceof Group)
			for (Actor child : ((Group) actor).getChildren())
				collectAll(child, shown);
	}

	/** x, y, width, height in window pixels, y down from the top left. */
	private static float[] screenBounds(Actor actor)
	{
		validateDownTo(actor);
		Stage stage = GVars_UI.mainUi;
		Vector2 a = stage.stageToScreenCoordinates(actor.localToStageCoordinates(new Vector2(0, 0)));
		Vector2 b = stage.stageToScreenCoordinates(actor.localToStageCoordinates(new Vector2(actor.getWidth(), actor.getHeight())));
		return new float[] { Math.min(a.x, b.x), Math.min(a.y, b.y), Math.abs(b.x - a.x), Math.abs(b.y - a.y) };
	}

	/**
	 * Tables place their children when they are next drawn: a command arriving right after a screen or a tab was built
	 * would read the positions of before. Lays out every table from the stage down to the actor first.
	 */
	private static void validateDownTo(Actor actor)
	{
		List<Layout> chain = new ArrayList<>();
		for (Actor a = actor; a != null; a = a.getParent())
			if (a instanceof Layout)
				chain.add(0, (Layout) a);
		for (Layout layout : chain)
			layout.validate();
	}

	private static float[] previewBounds()
	{
		// parr_Pos_Y counts from the bottom of the window, as glViewport does.
		return new float[] { GVars_Vue_Edition.parr_Pos_X, Gdx.graphics.getHeight() - GVars_Vue_Edition.parr_Pos_Y - GVars_Vue_Edition.parr_Size_Y,
				GVars_Vue_Edition.parr_Size_X, GVars_Vue_Edition.parr_Size_Y };
	}

	private static String format(float[] bounds)
	{return Math.round(bounds[0]) + " " + Math.round(bounds[1]) + " " + Math.round(bounds[2]) + " " + Math.round(bounds[3]);}

	/** A real click, through the same input processors as the mouse, refused when another control covers the target. */
	private static String click(Actor actor)
	{
		float[] bounds = screenBounds(actor);
		int x = Math.round(bounds[0] + bounds[2] / 2), y = Math.round(bounds[1] + bounds[3] / 2);

		if (x < 0 || y < 0 || x >= Gdx.graphics.getWidth() || y >= Gdx.graphics.getHeight())
			return "err off screen at " + x + " " + y;

		Stage stage = GVars_UI.mainUi;
		Vector2 stagePoint = stage.screenToStageCoordinates(new Vector2(x, y));
		Actor hit = stage.hit(stagePoint.x, stagePoint.y, true);
		if (hit == null || !hit.isDescendantOf(actor))
			return "err covered by " + (hit == null ? "nothing" : describe(hit));

		InputProcessor input = Gdx.input.getInputProcessor();
		input.mouseMoved(x, y);
		input.touchDown(x, y, 0, Buttons.LEFT);
		input.touchUp(x, y, 0, Buttons.LEFT);
		return "ok " + x + " " + y;
	}

	/**
	 * A real turn of the wheel over the control, through the same input processors as the mouse. The wheel turns a
	 * frame after the mouse arrives: the stage fires enter (which gives a slider the wheel) only when it acts.
	 */
	private static String wheel(Actor actor, int notches, CompletableFuture<String> reply)
	{
		float[] bounds = screenBounds(actor);
		int x = Math.round(bounds[0] + bounds[2] / 2), y = Math.round(bounds[1] + bounds[3] / 2);

		if (x < 0 || y < 0 || x >= Gdx.graphics.getWidth() || y >= Gdx.graphics.getHeight())
			return "err off screen at " + x + " " + y;

		InputProcessor input = Gdx.input.getInputProcessor();
		input.mouseMoved(x, y);
		Gdx.app.postRunnable(() ->
		{
			for (int i = 0; i < Math.abs(notches); i++)
				input.scrolled(0, Math.signum(notches));
			reply.complete("ok " + x + " " + y);
		});
		return null;
	}

	private static String describe(Actor actor)
	{
		for (Actor a = actor; a != null; a = a.getParent())
			if (a.getName() != null)
				return a.getName();
		return actor.getClass().getName();
	}

	private static String set(Actor actor, String value)
	{
		if (actor instanceof JksNumberSlider)
		{
			JksNumberSlider slider = (JksNumberSlider) actor;
			slider.setValue(Float.parseFloat(value));
			slider.actionOnSliderMovement();
		}
		else if (actor instanceof Slider)
			((Slider) actor).setValue(Float.parseFloat(value));
		else if (actor instanceof TextField)
			((TextField) actor).setText(value);
		else if (actor instanceof Button)
			((Button) actor).setChecked(Boolean.parseBoolean(value));
		else if (actor instanceof SelectBox)
			return selectItem((SelectBox<?>) actor, value);
		else if (actor instanceof Spinner && ((Spinner) actor).getModel() instanceof IntSpinnerModel)
		{
			((IntSpinnerModel) ((Spinner) actor).getModel()).setValue(Integer.parseInt(value), false);
			fireChange(actor);
		}
		else if (actor instanceof JksTextureList)
		{
			JksTextureList list = (JksTextureList) actor;
			int index = Integer.parseInt(value);
			if (index < 0 || index >= list.getItems().size)
				return "err no item " + index + " (" + list.getItems().size + " items)";
			list.getSelection().choose(list.getItems().get(index));
		}
		else if (actor instanceof BasicColorPicker)
		{
			// getColor() is the actor's tint, not the picked color: hand the listener the parsed one.
			BasicColorPicker picker = (BasicColorPicker) actor;
			Color color = Color.valueOf(value);
			picker.setColor(color);
			if (picker.getListener() != null)
				picker.getListener().changed(color);
		}
		else
			return "err cannot set a " + actor.getClass().getName();
		return "ok";
	}

	private static <T> String selectItem(SelectBox<T> box, String value)
	{
		for (T item : box.getItems())
			if (String.valueOf(item).equals(value))
			{
				box.setSelected(item);
				return "ok";
			}
		return "err no item " + value;
	}

	private static void fireChange(Actor actor)
	{
		actor.fire(new ChangeEvent());
	}
}
