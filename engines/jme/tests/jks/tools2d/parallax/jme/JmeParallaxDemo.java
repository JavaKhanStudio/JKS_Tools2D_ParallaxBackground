package jks.tools2d.parallax.jme;

import java.nio.file.Paths;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.jme3.app.SimpleApplication;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Quad;
import com.jme3.system.AppSettings;

import jks.tools2d.parallax.TransfertStyle;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * The jME twin of the editor repository's demo/ (ParallaxDemo): Hiver and Printemps, read from their .plax files, behind a spinning cube.
 * SPACE winter/spring, T transfert style (depth stagger, fade, dissolve, through white, fog creep), N night tint,
 * LEFT / RIGHT scroll, R reset.
 * {@code ./gradlew :jme:run} (from the repository root: it reads core/test-data/samples).
 * {@code -Dparallax.jme.demoShot=out.png} presses SPACE and N, saves the frame once both fades are over, and quits
 * (tools/start-demo-check.sh); with {@code -Dparallax.jme.demoShotMid=true}, SPACE alone and the frame halfway through.
 */
public class JmeParallaxDemo extends SimpleApplication implements ActionListener
{
	private static final float TRANSFER_SECONDS = 3;
	private static final float MANUAL_SPEED = 400;
	private static final Color NIGHT_TINT = new Color(0.45f, 0.5f, 0.85f, 1);
	/**
	 * What SPACE switches through, T picks (TransfertStyle), at the transfert lab's opening values: the back layers change
	 * first, then the front ones; every layer at once; in patches; through white; under a mist.
	 */
	private static final TransfertStyle[] STYLES = {TransfertStyle.depthStagger(1), TransfertStyle.FADE,
		TransfertStyle.dissolve(5, 0.08f, 0.5f), TransfertStyle.throughColor(Color.WHITE, 0.5f),
		TransfertStyle.fogCreep(new Color(0.93f, 0.95f, 0.97f, 1), 0.5f)};
	private static final String[] STYLE_NAMES = {"depth stagger", "fade", "dissolve", "through white", "fog creep"};
	private static final String HUD = "SPACE: winter/spring   T: style (%s)   N: night tint   LEFT/RIGHT: scroll   R: reset";

	private PlaxBackground bg;
	private WholePage_Model winter, spring;
	private TextureAtlas winterAtlas, springAtlas;
	private boolean showingWinter = true, night, left, right;
	private int style;
	private BitmapText hud;
	private Geometry hudBack;
	private Geometry cube;
	private final String demoShot = System.getProperty("parallax.jme.demoShot");
	private final boolean demoShotMid = Boolean.getBoolean("parallax.jme.demoShotMid");
	private final FrameGrab grab = new FrameGrab();
	private float elapsed;
	private boolean pressed;

	public static void main(String[] args)
	{
		JmeParallaxDemo app = new JmeParallaxDemo();
		AppSettings settings = new AppSettings(true);
		settings.setResolution(1280, 720);
		settings.setResizable(true);
		settings.setGammaCorrection(false);
		settings.setTitle("JKS parallax in jMonkeyEngine");
		app.setSettings(settings);
		app.setShowSettings(false);
		app.start();
	}

	@Override
	public void simpleInitApp()
	{
		setDisplayStatView(false);
		flyCam.setEnabled(false);
		assetManager.registerLocator(Paths.get("core/test-data/samples").toAbsolutePath().toString(), FileLocator.class);

		winter = PlaxBackground.loadPage(assetManager, "hiver/Hiver.plax");
		spring = PlaxBackground.loadPage(assetManager, "printemps/Printemps.plax");
		winterAtlas = JmeAtlas.load(assetManager, winter.pageModel.atlasName);
		springAtlas = JmeAtlas.load(assetManager, spring.pageModel.atlasName);
		bg = new PlaxBackground();
		bg.speedConstantX = 60;
		bg.setPage(winter, winterAtlas);
		stateManager.attach(bg);

		// The game's own scene, drawn over the background.
		cube = new Geometry("cube", new Box(1, 1, 1));
		Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		material.setColor("Color", ColorRGBA.Orange);
		cube.setMaterial(material);
		rootNode.attachChild(cube);

		// On a dark band: a transfert through white or into a mist turns the whole screen near white.
		Material back = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		back.setColor("Color", new ColorRGBA(0, 0, 0, 0.5f));
		back.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
		hudBack = new Geometry("hudBack", new Quad(1, 1));
		hudBack.setMaterial(back);
		guiNode.attachChild(hudBack);
		hud = new BitmapText(guiFont);
		hud.setLocalTranslation(10, cam.getHeight() - 10, 0);
		guiNode.attachChild(hud);
		showHud();

		inputManager.addMapping("season", new KeyTrigger(KeyInput.KEY_SPACE));
		inputManager.addMapping("style", new KeyTrigger(KeyInput.KEY_T));
		inputManager.addMapping("night", new KeyTrigger(KeyInput.KEY_N));
		inputManager.addMapping("reset", new KeyTrigger(KeyInput.KEY_R));
		inputManager.addMapping("left", new KeyTrigger(KeyInput.KEY_LEFT));
		inputManager.addMapping("right", new KeyTrigger(KeyInput.KEY_RIGHT));
		inputManager.addListener(this, "season", "style", "night", "reset", "left", "right");
		guiViewPort.addProcessor(grab);
	}

	@Override
	public void onAction(String name, boolean pressed, float tpf)
	{
		if (name.equals("left"))
			left = pressed;
		else if (name.equals("right"))
			right = pressed;
		else if (!pressed)
			return;
		else if (name.equals("season"))
		{
			showingWinter = !showingWinter;
			bg.transfertIntoPage(showingWinter ? winter : spring, showingWinter ? winterAtlas : springAtlas, TRANSFER_SECONDS,
				STYLES[style]);
		}
		else if (name.equals("style"))
		{
			style = (style + 1) % STYLES.length;
			showHud();
		}
		else if (name.equals("night"))
		{
			night = !night;
			bg.tintTo(night ? NIGHT_TINT : Color.WHITE, TRANSFER_SECONDS);
		}
		else if (name.equals("reset"))
			bg.resetPositions();
	}

	private void showHud()
	{
		hud.setText(String.format(HUD, STYLE_NAMES[style]));
		hudBack.setLocalScale(hud.getLineWidth() + 12, hud.getLineHeight() + 6, 1);
		hudBack.setLocalTranslation(4, cam.getHeight() - 13 - hud.getLineHeight(), -1);
	}

	@Override
	public void simpleUpdate(float tpf)
	{
		cube.rotate(tpf * 0.4f, tpf * 0.7f, 0);
		if (left)
			bg.speedConsumableX = -MANUAL_SPEED;
		else if (right)
			bg.speedConsumableX = MANUAL_SPEED;

		if (demoShot != null)
		{
			elapsed += tpf;
			if (!pressed && elapsed > 0.5f)
			{
				pressed = true;
				onAction("season", true, 0);
				if (!demoShotMid)
					onAction("night", true, 0);
			}
			else if (pressed && elapsed > 0.5f + (demoShotMid ? TRANSFER_SECONDS / 2 : TRANSFER_SECONDS + 0.5f))
			{
				if (grab.isPending())
					return;
				if (elapsed < 99)
				{
					grab.grab(new java.io.File(demoShot));
					elapsed = 100;
				}
				else
					stop();
			}
		}
	}
}
