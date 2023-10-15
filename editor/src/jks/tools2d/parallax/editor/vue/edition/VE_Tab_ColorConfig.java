package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.gvars.GVars_UI.baseSkin;
import static jks.tools2d.parallax.editor.vue.Vue_Edition.parallax_Heart;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane.TabbedPaneStyle;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneAdapter;

import jks.tools2d.libgdxutils.Utils_Interface;
import jks.tools2d.libgdxutils.color.ColorPickerListener;
import jks.tools2d.libgdxutils.color.ExtendedColorPicker;
import jks.tools2d.parallax.editor.gvars.GVars_UI;
import jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition;
import jks.tools2d.parallax.side.SquareBackground; 

public class VE_Tab_ColorConfig extends Tab
{

	Table mainTable  ; 
	
	VE_Tab_ColorConfig()
	{
		super(false, false);
		
		final VisTable container = new VisTable();
		TabbedPane tabbedPane = new TabbedPane(GVars_UI.baseSkin.get("default", TabbedPaneStyle.class));
		tabbedPane.setAllowTabDeselect(false);
		tabbedPane.addListener(new TabbedPaneAdapter()
		{
			@Override
			public void switchedTab(Tab tab)
			{
				container.clearChildren();
				container.add(tab.getContentTable()).expand().fill();
			}
		});
		
		Tab topPalette = buildColorPalette("Top Square",true) ; 
		Tab bottomPalette = buildColorPalette("Bottom Square",false) ; 
	
		tabbedPane.add(topPalette);
		tabbedPane.add(bottomPalette);
		tabbedPane.switchTab(topPalette);
		mainTable = new Table() ; 
		mainTable.add(tabbedPane.getTable()).expandX().fillX();
		mainTable.row();
		mainTable.add(container).expand().fill();
		
	}
	
	public Tab buildColorPalette(String colorSection, boolean topSquare)
	{
		Table secondTable ; 
		
		SquareBackground squareTempo = topSquare ? parallax_Heart.topSquare : parallax_Heart.bottomSquare ; ;
		SquareBackground square = squareTempo != null ? squareTempo : new SquareBackground() ; 
		
		final ExtendedColorPicker topPicker = new ExtendedColorPicker() ; 
		topPicker.setListener(buildListener(topPicker,square, true,true));
		
		final ExtendedColorPicker bottomPicker = new ExtendedColorPicker() ; 
		bottomPicker.setListener(buildListener(bottomPicker,square, false,true));
		
		ImageButton topColorSelector = buildColorSelector(topPicker) ; 
		ImageButton bottomColorSelector =  buildColorSelector(bottomPicker) ; 
		
		
		VisCheckBox activeBox = new VisCheckBox("Is active");
		activeBox.setChecked(square != null);
		activeBox.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{
				return true ; 
			}
			
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				if(topSquare)
				{
					if(!activeBox.isChecked())
					{
						parallax_Heart.topSquare.visible = false ; 
						topPicker.setVisible(false);
						bottomPicker.setVisible(false);
					}
					else
					{
						parallax_Heart.topSquare.visible = true ; 
//						= new SquareBackground(topPicker.getColor(),bottomPicker.getColor(),0,true) ;
//						
//						topPicker.setListener(buildListener(topPicker,parallax_Heart.topSquare, true,false));
//						bottomPicker.setListener(buildListener(bottomPicker,parallax_Heart.topSquare, false,false));
//						
						topPicker.setVisible(true);
						bottomPicker.setVisible(true);
						topPicker.updateUI() ;
						bottomPicker.updateUI();
					}
				}
				else
				{
					if(!activeBox.isChecked())
					{
						parallax_Heart.bottomSquare.visible = false ; 
						topPicker.setVisible(false);
						bottomPicker.setVisible(false);
					}
					else
					{
						parallax_Heart.bottomSquare.visible = true ;  
//						= new SquareBackground(topPicker.getColor(),bottomPicker.getColor(),0.5f,false) ;
//						
//						topPicker.setListener(buildListener(topPicker,parallax_Heart.bottomSquare, true,false));
//						bottomPicker.setListener(buildListener(bottomPicker,parallax_Heart.bottomSquare, false,false));
						
						topPicker.setVisible(true);
						bottomPicker.setVisible(true);
						
						topPicker.updateUI() ;
						bottomPicker.updateUI();
					}
				}
			}
			
		}) ; 
		
		Slider boxSize = new Slider(0, 100, 1, false,baseSkin);
		TextField boxSize_tf = new TextField("",baseSkin) ;
		boxSize.setValue((square.getHeight()/Gdx.graphics.getHeight()) * 100) ;
		boxSize_tf.setText(boxSize.getValue() + "");
		boxSize.addListener(new InputListener()
		{		
			@Override
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) 
			{
				touchDragged(event, x,y,pointer) ;
				return true ;
			}
			
			@Override
			public void touchDragged(InputEvent event, float x, float y, int pointer)
			{
				float value = (Gdx.graphics.getHeight() * boxSize.getValue())/100 ; 
				square.setHeight(topSquare, value); ; 
				boxSize_tf.setText(boxSize.getValue() + "");
			}
		}) ; 
		
		secondTable = new Table() ; 
		
		secondTable.add(activeBox).row();
		secondTable.add(boxSize) ;
		secondTable.add(boxSize_tf).width(40).row();
		
		secondTable.add(new VisLabel("Top Color")).row();
		secondTable.add(new VisLabel("Picker"));
		secondTable.add(topColorSelector).row();
		secondTable.add(topPicker).colspan(2).row();
		
		secondTable.add(new VisLabel("Bottom Color")).row();
		secondTable.add(new VisLabel("Picker"));
		secondTable.add(bottomColorSelector).row();
		secondTable.add(bottomPicker).colspan(2);
		
		topPicker.updateUI() ;
		bottomPicker.updateUI();
		
		Tab tab = new Tab(false, false)
		{		
			@Override
			public String getTabTitle()
			{
				return colorSection;
			}
			
			@Override
			public Table getContentTable()
			{
				return secondTable;
			}
			
			@Override 
			public void onShow () 
			{
				super.onShow();
				topPicker.updateUI() ;
				bottomPicker.updateUI();
			}
		};
		
		return tab ; 
	}
	
	public ImageButton buildColorSelector(ExtendedColorPicker picker) 
	{
		ImageButton colorSelector = new ImageButton(Utils_Interface.buildDrawingRegionTexture("editor/interfaces/colorSelection.png")) 
		{
			@Override
			public float getPrefWidth()
			{return 50 ;}
			
			@Override
			public float getPrefHeight()
			{return getPrefWidth() ; }
		}; 
		
		colorSelector.addListener(new InputListener()
		{
			@Override
			public boolean touchDown(InputEvent event, float x, float y, int pointer, int button)
			{
				GVars_Vue_Edition.colorPicked = picker ;  
				return true ; 
			}
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
				super.touchUp(event, x, y, pointer, button);
			}
			
		}) ;
		
		return colorSelector ; 
	}
	
	public ColorPickerListener buildListener(ExtendedColorPicker picker, SquareBackground square,  boolean top, boolean resetVisu)
	{
		return new ColorPickerListener()
		{
			boolean firstTime = resetVisu ; 
			boolean inInitTime = resetVisu ; 
			
			@Override
			public void changed(Color newColor)
			{
				if(inInitTime)
				{
					inInitTime = false ; 
					return ; 
				}
				if(firstTime)
				{
					if(top && newColor.equals(square.topColor))
						return ; 
					if(!top && newColor.equals(square.bottomColor))
						return ; 
					
					if(top)
						picker.setColor(square.topColor) ; 
					else
						picker.setColor(square.bottomColor) ; 
					
					firstTime = false ; 
					return ; 
				}
			
				if(top)
					square.topColor = newColor ;
				else
					square.bottomColor = newColor ;					
			}
			
			@Override
			public void reset(Color previousColor, Color newColor)
			{}
			@Override
			public void canceled(Color oldColor)
			{}
			@Override
			public void finished(Color newColor)
			{}
		};
		
	}
	
	@Override
	public String getTabTitle()
	{
		return "Background";
	}

	@Override
	public Table getContentTable()
	{
		return mainTable;
	}
}