package com.mygdx.parallax;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

import android.os.Bundle;
import jks.tools2d.parallax.mains.Testing_Day;

public class AndroidLauncher extends AndroidApplication 
{
	
	@Override
	protected void onCreate (Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
		initialize(new Testing_Day(), config);
	}

}
