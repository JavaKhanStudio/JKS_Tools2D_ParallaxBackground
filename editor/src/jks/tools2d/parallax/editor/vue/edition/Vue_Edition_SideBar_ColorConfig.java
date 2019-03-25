package jks.tools2d.parallax.editor.vue.edition;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
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
import jks.tools2d.parallax.editor.gvars.GVars_Ui;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.side.SquareBackground;

public class Vue_Edition_SideBar_ColorConfig extends Tab
{

	Table mainTable  ; 
	
	Vue_Edition_SideBar_ColorConfig()
	{
		super(false, false);
		
		final VisTable container = new VisTable();
		TabbedPane tabbedPane = new TabbedPane(GVars_Ui.baseSkin.get("default", TabbedPaneStyle.class));
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
		SquareBackground square = topSquare ? Parallax_Heart.topSquare : Parallax_Heart.bottomSquare ; 
		
		final ExtendedColorPicker topPicker = new ExtendedColorPicker() ; 
		topPicker.setListener(buildListener(topPicker,square, true,true));
		ImageButton topColorSelector = new ImageButton(Utils_Interface.buildDrawingRegionTexture("editor/interfaces/placeholder.png")) 
		{
			@Override
			public float getPrefWidth()
			{
				return 50 ; 
			}
			
			@Override
			public float getPrefHeight()
			{return  getPrefWidth() ; }
		}; 
		
		topColorSelector.setScale(0.5f);
		
		
		
		final ExtendedColorPicker bottomPicker = new ExtendedColorPicker() ; 
		bottomPicker.setListener(buildListener(bottomPicker,square, false,true));
		ImageButton bottomColorSelector = new ImageButton(Utils_Interface.buildDrawingRegionTexture("editor/interfaces/placeholder.png")) ; 
		
		
	
		VisCheckBox topCheckBox = new VisCheckBox("Is active");
		topCheckBox.setChecked(square != null);
		topCheckBox.addListener(new InputListener()
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
					if(!topCheckBox.isChecked())
					{
						Parallax_Heart.topSquare = null ; 
						topPicker.setVisible(false);
						bottomPicker.setVisible(false);
					}
					else
					{
						Parallax_Heart.topSquare = new SquareBackground(topPicker.getColor(),bottomPicker.getColor(),0,true) ;
						
						topPicker.setListener(buildListener(topPicker,Parallax_Heart.topSquare, true,false));
						bottomPicker.setListener(buildListener(bottomPicker,Parallax_Heart.topSquare, false,false));
						
						topPicker.setVisible(true);
						bottomPicker.setVisible(true);
						topPicker.updateUI() ;
						bottomPicker.updateUI();
					}
				}
				else
				{
					if(!topCheckBox.isChecked())
					{
						Parallax_Heart.bottomSquare = null ; 
						topPicker.setVisible(false);
						bottomPicker.setVisible(false);
					}
					else
					{
						Parallax_Heart.bottomSquare = new SquareBackground(topPicker.getColor(),bottomPicker.getColor(),0.5f,false) ;
						
						topPicker.setListener(buildListener(topPicker,Parallax_Heart.bottomSquare, true,false));
						bottomPicker.setListener(buildListener(bottomPicker,Parallax_Heart.bottomSquare, false,false));
						
						topPicker.setVisible(true);
						bottomPicker.setVisible(true);
						
						topPicker.updateUI() ;
						bottomPicker.updateUI();
					}
				}
			}
			
		}) ; 
		
		secondTable = new Table() ; 
		
		secondTable.add(topCheckBox).row();
		secondTable.add(new VisLabel("Top picker")).row();
		secondTable.add(topColorSelector).row();;
		secondTable.add(topPicker).row();
		secondTable.add(new VisLabel("Bottom picker")).row();
		secondTable.add(bottomPicker);
		
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
					if(top && newColor.equals(square.top_backColor))
						return ; 
					if(!top && newColor.equals(square.bottom_backColor))
						return ; 
					
					if(top)
						picker.setColor(square.top_backColor) ; 
					else
						picker.setColor(square.bottom_backColor) ; 
					
					firstTime = false ; 
					return ; 
				}
			
				if(top)
					square.top_backColor = newColor ;
				else
					square.bottom_backColor = newColor ;					
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
		return "Color Config";
	}

	@Override
	public Table getContentTable()
	{
		return mainTable;
	}
	
	
}