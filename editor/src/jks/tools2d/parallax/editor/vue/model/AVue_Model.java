package jks.tools2d.parallax.editor.vue.model;

import static jks.tools2d.parallax.editor.gvars.GVars_Ui.mainUi;

import java.util.ArrayList;

public abstract class AVue_Model implements ImportAction
{

	public OverlayModel overlay ; 
	public AView_Model_Filter filter ; 
	
	public abstract void init() ;
	public abstract void destroy() ;
	public abstract void restart() ; 
	
	public abstract void update (float delta) ;
	public abstract void render () ;
	
	
	public void clear()
	{
		
	}
	
	
	public void drawInterface()
	{
		mainUi.draw() ;			
	}

	
}
