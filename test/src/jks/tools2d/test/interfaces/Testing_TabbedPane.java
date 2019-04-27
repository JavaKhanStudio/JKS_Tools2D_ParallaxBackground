package jks.tools2d.test.interfaces;

import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisRadioButton;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane.TabbedPaneStyle;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneAdapter;

import jks.tools2d.libgdxutils.JksNumberSlider;
import jks.tools2d.parallax.editor.gvars.GVars_Ui;

public class Testing_TabbedPane extends UiTestModel
{

	@Override
	public void create () 
	{
		GVars_Ui.init();
		buildTabbed() ; 
		buildSlider(); 
		buildRadio() ; 
	}
	
	public void buildRadio()
	{
		ButtonGroup<VisRadioButton> groupeRadio = new ButtonGroup<VisRadioButton>() ;
		VisRadioButton radio = new VisRadioButton("test1");
		radio.setBounds(400, 400, 100, 100);
		VisRadioButton radio2 = new VisRadioButton("test2");
		radio2.setBounds(500, 500, 100, 100);
		
		groupeRadio.add(radio,radio2);
		
		
		GVars_Ui.mainUi.addActor(radio);
		GVars_Ui.mainUi.addActor(radio2);
	}
	
	public void buildSlider()
	{
		JksNumberSlider slider = new JksNumberSlider(0, 100, 1, GVars_Ui.baseSkin )
		{		
			@Override
			public void actionOnSliderMovement()
			{
				// TODO Auto-generated method stub
				
			}
		};
		GVars_Ui.mainUi.addActor(slider);
		slider.setBounds(100, 300, 100, 100);
	}
	
	public void buildTabbed()
	{
		Table theTable = new Table(GVars_Ui.baseSkin) ; 
		final VisTable container = new VisTable();

		TabbedPaneStyle style = GVars_Ui.baseSkin.get("default", TabbedPaneStyle.class) ; 
		TabbedPane tabbedPane = new TabbedPane(style);
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

		theTable.add(tabbedPane.getTable()).expandX().fillX();
		theTable.row();
		theTable.add(container).expand().fill();
		
		
		tabbedPane.add(new TestTab("tab1"));
		tabbedPane.add(new TestTab("tab2"));
		tabbedPane.add(new TestTab("tab3"));


//		debugAll();
		theTable.setSize(300, 200);
		GVars_Ui.mainUi.addActor(theTable);
	}
	
	public Testing_TabbedPane()
	{
		
	}

	private class TestTab extends Tab
	{
		private String title;
		private Table content;

		public TestTab(String title)
		{
			super(false, false);
			this.title = title;
			content = new VisTable();
			content.add(new VisLabel(title));
		}

		@Override
		public String getTabTitle()
		{
			return title;
		}

		@Override
		public Table getContentTable()
		{
			return content;
		}
	}
	
}