package com.mygdx.game;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

import android.os.Bundle;
import jks.tools2d.parallax.test.Testing_Parallax;

public class AndroidLauncher extends AndroidApplication 
{
	
	@Override
	protected void onCreate (Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
		initialize(new Testing_Parallax(), config);
	}
	
	private static final long ROLE_ANY = 0x0; // can play in any match.
	private static final long ROLE_FARMER = 0x1; // 001 in binary
	private static final long ROLE_ARCHER = 0x2; // 010 in binary
	private static final long ROLE_WIZARD = 0x4; // 100 in binary

//	private void startQuickGame(long role) 
//	{
//	    // auto-match criteria to invite one random automatch opponent.
//	    // You can also specify more opponents (up to 3).
//	    Bundle autoMatchCriteria = RoomConfig.createAutoMatchCriteria(1, 1, role);
//
//	    // build the room config:
//	    RoomConfig roomConfig =
//	            RoomConfig.builder(mRoomUpdateCallback)
//	                    .setOnMessageReceivedListener(mMessageReceivedHandler)
//	                    .setRoomStatusUpdateCallback(mRoomStatusCallbackHandler)
//	                    .setAutoMatchCriteria(autoMatchCriteria)
//	                    .build();
//
//	    // prevent screen from sleeping during handshake
//	    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//
//	    // Save the roomConfig so we can use it if we call leave().
//	    mJoinedRoomConfig = roomConfig;
//
//	    // create room:
//	    Games.get
//	    Games.getRealTimeMultiplayerClient(this,null)
//	            .create(roomConfig);
//	}

}
