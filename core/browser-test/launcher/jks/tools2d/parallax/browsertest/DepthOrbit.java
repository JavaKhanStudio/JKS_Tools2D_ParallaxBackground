package jks.tools2d.parallax.browsertest;

import com.badlogic.gdx.InputAdapter;
import com.google.gwt.dom.client.InputElement;

/** {@link DepthLab}'s input (r539, out of the lab). Drag: turn round the page; the wheel: closer or farther; space: the slide. */
final class DepthOrbit extends InputAdapter
{
	private final DepthLab lab;
	private int lastX, lastY;

	DepthOrbit(DepthLab lab)
	{this.lab = lab;}

	@Override
	public boolean touchDown(int screenX, int screenY, int pointer, int button)
	{
		lastX = screenX;
		lastY = screenY;
		return true;
	}

	@Override
	public boolean touchDragged(int screenX, int screenY, int pointer)
	{
		float dx = screenX - lastX, dy = screenY - lastY;
		lastX = screenX;
		lastY = screenY;
		if (lab.slide < 1 && lab.goal != 1)
			lab.goal = 1;
		set(lab.yaw, LabControls.read(lab.yaw) - dx * 0.25f, -85, 85);
		set(lab.pitch, LabControls.read(lab.pitch) + dy * 0.2f, 2, 80);
		return true;
	}

	@Override
	public boolean scrolled(float amountX, float amountY)
	{
		set(lab.closer, LabControls.read(lab.closer) * (float) Math.pow(1.1, -amountY), 0.3f, 2.5f);
		return true;
	}

	@Override
	public boolean keyDown(int keycode)
	{
		if (keycode != com.badlogic.gdx.Input.Keys.SPACE)
			return false;
		lab.toggle();
		return true;
	}

	private void set(InputElement slider, float value, float min, float max)
	{slider.setValue(LabControls.text(Math.max(min, Math.min(max, value))));}
}
