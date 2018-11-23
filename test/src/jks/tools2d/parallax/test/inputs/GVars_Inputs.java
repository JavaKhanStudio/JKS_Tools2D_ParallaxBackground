package jks.tools2d.parallax.test.inputs ;

import static jks.tools2d.parallax.mains.GVars_TestingHeart.* ; 
import static jks.tools2d.parallax.heart.Parallax_Heart.* ;

public class GVars_Inputs 
{
	public static boolean touched, 
		upPressed, downPressed,
		leftPressed, rightPressed,
		zoomInPressed, zoomOutPressed 
		;

	
	
	public static void updateInput (float delta) 
	{
		if (leftPressed)
			worldCamera.position.add(-screenMovementSpeed, 0, 0); 
		
		if (rightPressed)
			worldCamera.position.add(screenMovementSpeed, 0, 0);
			
		if(upPressed)
			worldCamera.position.add(0,screenMovementSpeed, 0);
		
		if(downPressed)
			worldCamera.position.add(0,-screenMovementSpeed, 0);
		
		if(zoomInPressed)
			worldCamera.zoom *= 1.1f;
		
		if(zoomOutPressed)
			worldCamera.zoom /= 1.1f;

		worldCamera.update();
		batch.setProjectionMatrix(worldCamera.combined);
	}
}
