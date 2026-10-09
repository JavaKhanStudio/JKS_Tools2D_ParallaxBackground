package jks.tools2d.parallax.etc2check;

import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

/** Starts {@link Etc2Check} on OpenGL ES 3, as a game that ships ETC2 atlases would; the extra {@code hold} holds one page (r206). */
public class Etc2CheckLauncher extends AndroidApplication
{
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
		config.useGL30 = true;
		config.useImmersiveMode = true;
		initialize(new Etc2Check(getIntent().getStringExtra("hold")), config);
	}
}
