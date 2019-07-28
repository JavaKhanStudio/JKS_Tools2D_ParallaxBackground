package jks.tools2d.libgdxutils;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox.CheckBoxStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

public class JksCheckBox extends TextButton 
{
	private Image image;
	private CheckBoxStyle style;

	public JksCheckBox (String text, Skin skin, boolean isLeft) 
	{
		this(text, skin.get(CheckBoxStyle.class),isLeft);
	}

	public JksCheckBox (String text, Skin skin, String styleName) 
	{
		this(text, skin.get(styleName, CheckBoxStyle.class),true);
	}

	public JksCheckBox (String text, CheckBoxStyle style, boolean isLeft) 
	{
		super(text, style);
		clearChildren();
		Label label = getLabel();
		image = new Image(style.checkboxOff);
	
		if(isLeft)
		{
			add(image) ;
			add(label);		
		}
		else
		{
			add(label);
			add(image) ;
		}
	}

	public void setStyle (ButtonStyle style) 
	{
		if (!(style instanceof CheckBoxStyle)) throw new IllegalArgumentException("style must be a CheckBoxStyle.");
		super.setStyle(style);
		this.style = (CheckBoxStyle)style;
	}

	/** Returns the checkbox's style. Modifying the returned style may not have an effect until {@link #setStyle(ButtonStyle)} is
	 * called. */
	public CheckBoxStyle getStyle () 
	{return style;}

	public void draw (Batch batch, float parentAlpha) 
	{
		Drawable checkbox = null;
		if (isDisabled()) 
		{
			if (isChecked() && style.checkboxOnDisabled != null)
				checkbox = style.checkboxOnDisabled;
			else
				checkbox = style.checkboxOffDisabled;
		}
		if (checkbox == null) 
		{
			if (isChecked() && style.checkboxOn != null)
				checkbox = style.checkboxOn;
			else if (isOver() && style.checkboxOver != null && !isDisabled())
				checkbox = style.checkboxOver;
			else
				checkbox = style.checkboxOff;
		}
		
		image.setDrawable(checkbox);
		super.draw(batch, parentAlpha);
	}

	public Image getImage () 
	{return image;}
}