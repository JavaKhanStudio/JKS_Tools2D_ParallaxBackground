package jks.tools2d.parallax.editor.vue.edition;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane.TabbedPaneStyle;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneAdapter;

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
	
	public Tab buildColorPalette(String colorSection,boolean topSquare)
	{
		Table secondTable ; 
		SquareBackground square = topSquare ? Parallax_Heart.topSquare : Parallax_Heart.bottomSquare ; 
		
		final ExtendedColorPicker topPicker = new ExtendedColorPicker() ; 
		final ExtendedColorPicker bottomPicker = new ExtendedColorPicker() ; 
		topPicker.setListener(buildListener(topPicker,square, true));
		bottomPicker.setListener(buildListener(bottomPicker,square, false));
		
		
		secondTable = new Table() ; 
		
		secondTable.add(topPicker);
		secondTable.row() ; 
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
	
	
	public ColorPickerListener buildListener(ExtendedColorPicker picker, SquareBackground square,  boolean top)
	{
		return new ColorPickerListener()
		{
			boolean firstTime = true ; 
			boolean inInitTime = true ; 
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
