package jks.tools2d.parallax.editor.vue.model;

import static jks.tools2d.parallax.editor.gvars.GVars_Ui.mainUi;

public abstract class AVue_Model implements ImportAction
{

	public OverlayModel overlay ; 
	public AView_Model_Filter filter ; 
	
	public abstract void init() ;
	public abstract void destroy() ;
	public abstract void restart() ; 
	
	public abstract void update (float delta) ;
	public abstract void render () ;
	public abstract void resize(int x, int y) ; 
	
	
	public void clear()
	{
		
	}
	
	
	public void drawInterface()
	{
		mainUi.draw() ;			
	}

	
}
