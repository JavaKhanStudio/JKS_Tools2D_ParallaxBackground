package jks.tools2d.libgdxutils;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.scenes.scene2d.utils.ArraySelection;
import com.badlogic.gdx.scenes.scene2d.utils.Cullable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.UIUtils;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;

public class JksTextureList extends Widget implements Cullable
{
	ListStyle style;
	final Array<TextureRegion> selected = new Array<TextureRegion>();
	ArraySelection<TextureRegion> selection ;
	private Rectangle cullingArea;
	
	int touchDown = -1, overIndex = -1;

	public float itemHeight;
	public float itemWidth ; 
	
	float decalX ; 
	final float decalXPower = 20 ;
	
	float decalY ; 
	final float decalYPower = 10 ;

	public float getPrefWidth () {
		return this.getWidth();
	}

	public float getPrefHeight () {
		return selected.size * itemHeight ;
	}
	
	public JksTextureList(Skin skin,float itemWidth, float itemHeight)
	{
		this(skin.get(ListStyle.class));
		this.itemWidth = itemWidth ; 
		this.itemHeight = itemHeight ;
		decalX = itemWidth/decalXPower ; 
		decalY = itemHeight/decalYPower ;  	
	}


	private JksTextureList(ListStyle style)
	{
		selection = new ArraySelection<TextureRegion>(selected) 
		{
			@Override
			public void choose (TextureRegion item)
			{
				super.choose(item);
				choiceAction(item) ; 
			}
		} ;
		selection.setActor(this);
		selection.setRequired(true);
		this.selection.setMultiple(false);
		
		setStyle(style);
		setSize(getPrefWidth(), getPrefHeight());

		addListener(new InputListener()
		{
			public boolean keyDown(InputEvent event, int keycode)
			{	
				if(keycode == Keys.UP)
				{
					touchDown -- ;
					if(touchDown < 0)
						touchDown = selected.size - 1 ; 
						
					selection.choose(selected.get(touchDown));
				}
				
				if(keycode == Keys.DOWN)
				{
					touchDown ++ ; 
					
					if(touchDown >= selected.size)
						touchDown = 0 ; 
					
					selection.choose(selected.get(touchDown));
				}
				
				return false;
			}

			public boolean touchDown(InputEvent event, float x, float y, int pointer, int button)
			{
				if (pointer != 0 || button != 0)
					return true;
				
				if (selection.isDisabled())
					return true;
				
				getStage().setKeyboardFocus(JksTextureList.this);
				
				if (selected.size == 0)
					return true;
				
				int index = getItemIndexAt(y);

				if (index == -1)
					return true;
				
				selection.choose(selected.get(index));
				touchDown = index;
				
				return false;
			}

			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{
			
			}

			public void touchDragged(InputEvent event, float x, float y, int pointer)
			{
				overIndex = getItemIndexAt(y);
			}

			public boolean mouseMoved(InputEvent event, float x, float y)
			{
				overIndex = getItemIndexAt(y);
				return false;
			}

			public void exit(InputEvent event, float x, float y, int pointer, Actor toActor)
			{}
		});
	}
	
	public void choiceAction(TextureRegion item)
	{
		System.out.println("public void choiceAction(TextureRegion item) should be implemented");
	}
	
	public void drawOnSelected(Batch batch, float x, float f, float width, float itemHeight2)
	{
		
	}

	public void setStyle(ListStyle style)
	{
		if (style == null)
			throw new IllegalArgumentException("style cannot be null.");
		this.style = style;
		invalidateHierarchy();
		
		font = style.font;
		selectedDrawable = style.selection;
		fontColorSelected = style.fontColorSelected;
		fontColorUnselected = style.fontColorUnselected;
	}

	/**
	 * Returns the list's style. Modifying the returned style may not have an effect
	 * until {@link #setStyle(ListStyle)} is called.
	 */
	public ListStyle getStyle()
	{
		return style;
	}

	BitmapFont font ; 
	Drawable selectedDrawable ; 
	Color fontColorSelected ; 
	Color fontColorUnselected ;
	
	@Override
	public void draw(Batch batch, float parentAlpha)
	{
		validate();

		Color color = getColor();
		batch.setColor(color.r, color.g, color.b, color.a * parentAlpha);

		float x = getX(), y = getY(), width = getWidth(), height = getHeight();
		float itemY = height - 1;


		float textOffsetX = selectedDrawable.getLeftWidth(),
				textWidth = width - textOffsetX - selectedDrawable.getRightWidth();
		float textOffsetY = selectedDrawable.getTopHeight() - font.getDescent();

		font.setColor(fontColorUnselected.r, fontColorUnselected.g, fontColorUnselected.b,
				fontColorUnselected.a * parentAlpha);
		
		for (int i = 0; i < selected.size; i++)
		{
			itemY -= itemHeight;
			if (itemY <= cullingArea.y + cullingArea.height)
			{
				TextureRegion item = selected.get(i);
				boolean selected = selection.contains(item);
				if (selected)
				{
					Drawable drawable = selectedDrawable;
					if (touchDown == i && style.down != null)
						drawable = style.down;
					
					drawable.draw(batch, x, y + itemY, width, itemHeight);
					font.setColor(fontColorSelected.r, fontColorSelected.g, fontColorSelected.b,
							fontColorSelected.a * parentAlpha);
				
					drawOnSelected(batch, x, y + itemY, width, itemHeight) ; 
				} 
				
				drawItem(batch, font, i, item, x + textOffsetX, y + itemY - textOffsetY, textWidth);
				
				if (selected)
				{
					font.setColor(fontColorUnselected.r, fontColorUnselected.g, fontColorUnselected.b,
							fontColorUnselected.a * parentAlpha);
					drawOnSelected(batch, x, y + itemY, width, itemHeight) ; 
				}
			} 
			else if (itemY < cullingArea.y)
			{break;}
		}
	}

	

	public ArraySelection<TextureRegion> getSelection()
	{return selection;}

	/** Returns the first selected item, or null. */
	public TextureRegion getSelected()
	{return selection.first();}

	public TextureRegion getItemAt(float y)
	{
		int index = getItemIndexAt(y);
		if (index == -1)
			return null;
		return selected.get(index);
	}

	/** @return -1 if not over an item. */
	public int getItemIndexAt(float y)
	{
		float height = getHeight();
		int index = (int) ((height - y) / itemHeight);
		
		if (index >= selected.size)
			return -1;

		return index;
	}

	public void setItems(TextureRegion... newItems)
	{
		if (newItems == null)
			throw new IllegalArgumentException("newItems cannot be null.");
		float oldPrefWidth = getPrefWidth(), oldPrefHeight = getPrefHeight();

		selected.clear();
		selected.addAll(newItems);
		selection.validate();

		invalidate();
		if (oldPrefWidth != getPrefWidth() || oldPrefHeight != getPrefHeight())
			invalidateHierarchy();
	}

	/**
	 * Sets the items visible in the list, clearing the selection if it is no longer
	 * valid. If a selection is {@link ArraySelection#getRequired()}, the first item
	 * is selected. This can safely be called with a (modified) array returned from
	 * {@link #getItems()}.
	 */
	public void setItems(Array newItems)
	{
		if (newItems == null)
			throw new IllegalArgumentException("newItems cannot be null.");
		float oldPrefWidth = getPrefWidth(), oldPrefHeight = getPrefHeight();

		if (newItems != selected)
		{
			selected.clear();
			selected.addAll(newItems);
		}
		selection.validate();

		invalidate();
		if (oldPrefWidth != getPrefWidth() || oldPrefHeight != getPrefHeight())
			invalidateHierarchy();
	}

	protected void drawItem(Batch batch, BitmapFont font, int index, TextureRegion item, float x, float y, float width)
	{
		batch.draw(item, 
				x , y + decalY, 
				itemWidth - decalX *2, itemHeight - decalY * 2);
	}

	public void clearItems()
	{
		if (selected.size == 0)
			return;
		selected.clear();
		selection.clear();
		invalidateHierarchy();
	}

	/**
	 * Returns the internal items array. If modified, {@link #setItems(Array)} must
	 * be called to reflect the changes.
	 */
	public Array<TextureRegion> getItems()
	{return selected;}

	public float getItemHeight()
	{return itemHeight;}

	protected String toString(TextureRegion object)
	{
		return object.toString();
	}

	public void setCullingArea(Rectangle cullingArea)
	{
		this.cullingArea = cullingArea;
	}

	static public class ListStyle
	{
		public BitmapFont font;
		public Color fontColorSelected = new Color(1, 1, 1, 1);
		public Color fontColorUnselected = new Color(1, 1, 1, 1);
		public Drawable selection;
		/** Optional. */
		public Drawable down, over;

		public ListStyle()
		{
		}

		public ListStyle(BitmapFont font, Color fontColorSelected, Color fontColorUnselected, Drawable selection)
		{
			this.font = font;
			this.fontColorSelected.set(fontColorSelected);
			this.fontColorUnselected.set(fontColorUnselected);
			this.selection = selection;
		}

		public ListStyle(ListStyle style)
		{
			this.font = style.font;
			this.fontColorSelected.set(style.fontColorSelected);
			this.fontColorUnselected.set(style.fontColorUnselected);
			this.selection = style.selection;
			this.down = style.down;
		}
	}
}