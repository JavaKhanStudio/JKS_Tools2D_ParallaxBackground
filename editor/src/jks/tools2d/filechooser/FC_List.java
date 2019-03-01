package jks.tools2d.filechooser;

import java.io.File;

import com.badlogic.gdx.Files.FileType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Event;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.TouchableAction;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldListener;
import com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.Selection;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.DelayedRemovalArray;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.Json.Serializable;

import jks.tools2d.libgdxutils.Utils_Scene2D;

import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.Pools;

// OLD AUTHOR @author dermetfan
// ACTUAL AUTHOR @author SimonKhan
public class FC_List extends FileChooser
{

	private Style style;

	/**
	 * the directories that have been visited previously, for the
	 * {@link #backButton}
	 */
	private Array<FileHandle> fileHistory = new Array<>();

	private FileHandle directory = Gdx.files.absolute(Gdx.files.getExternalStoragePath());
	{
		fileHistory.add(directory);
	}

	private TextField pathField;

	private List<String> currentlySelected;

	private ScrollPane contentsPane;

	private Button backButton;

	private Button parentButton;

	private Button chooseButton;

	private Button openButton;

	private Button cancelButton;

	/**
	 * if it exists, this open the file at the given {@link FileType#Absolute
	 * absolute} path if it is not a folder, {@link #setDirectory(FileHandle) goes
	 * into} it otherwise,
	 */
	public final TextFieldListener pathFieldListener = new TextFieldListener()
	{
		@Override
		public void keyTyped(TextField textField, char key)
		{
			if (key == '\r' || key == '\n')
			{
				FileHandle loc = Gdx.files.absolute(textField.getText());

				if (loc.exists())
				{
					if (loc.isDirectory())
						setDirectory(loc);
					else
						getListener().choose(loc);

					getStage().setKeyboardFocus(FC_List.this);
				}
			}
		}
	};

	public final ClickListener selectButtonListener = new ClickListener()
	{
		@Override
		public void clicked(InputEvent event, float x, float y)
		{
			Selection<String> selection = currentlySelected.getSelection();
			
			if (!selection.getMultiple())
			{
				FileHandle selected = currentlySelected();
				
				if (!isDirectoriesChoosable() && selected.isDirectory())
					setDirectory(selected);
				else
					getListener().choose(selected);
			} 
			else
			{
				@SuppressWarnings("unchecked")
				Array<FileHandle> files = Pools.obtain(Array.class);
				files.clear();
				
				for (String fileName : selection)
					files.add(directory.child(fileName));
				
				getListener().choose(files);
				Pools.free(files);
			}	
		}
	};

	public final ClickListener openButtonListener = new ClickListener()
	{
		@Override
		public void clicked(InputEvent event, float x, float y)
		{
			FileHandle child = currentlySelected();
			if (child.isDirectory())
				setDirectory(child);
		}
	};

	public final ClickListener cancelButtonListener = new ClickListener()
	{
		@Override
		public void clicked(InputEvent event, float x, float y)
		{getListener().cancel();}
	};

	public final ClickListener backButtonListener = new ClickListener()
	{
		@Override
		public void clicked(InputEvent event, float x, float y)
		{
			if (fileHistory.size > 1)
			{
				fileHistory.removeIndex(fileHistory.size - 1);
				setDirectory(directory = fileHistory.peek(), false);
			}
		}
	};

	public final ClickListener parentButtonListener = new ClickListener()
	{
		@Override
		public void clicked(InputEvent event, float x, float y)
		{setDirectory(directory.parent());}
	};

	public final ChangeListener contentsListener = new ChangeListener()
	{
		@Override
		public void changed(ChangeEvent event, Actor actor)
		{
			openButton.setDisabled(!currentlySelected().isDirectory());
			chooseButton.setDisabled(isDirectoriesChoosable());
		}
	};

	/** key controls of {@link #currentlySelected} */
	public final InputListener keyControlsListener = new InputListener()
	{

		@Override
		public boolean keyTyped(InputEvent event, char c)
		{
			if (event.isHandled())
				return true;

			if (getStage().getKeyboardFocus() != pathField && (c == '\r' || c == 'n'))
			{
				if (currentlySelected().isDirectory())
					openButtonListener.clicked(null, 0, 0); // fake event
				else
					selectButtonListener.clicked(null, 0, 0); // fake event
				
				return true;
			}

			int keyCode = event.getKeyCode();

			if (keyCode == Keys.DEL)
			{
				backButtonListener.clicked(null, 0, 0); // fake event
				return true;
			} else if (keyCode == Keys.LEFT)
			{
				parentButtonListener.clicked(null, 0, 0); // fake event
				return true;
			}

			int direction;
			if (keyCode == Keys.UP)
				direction = -1;
			else if (keyCode == Keys.DOWN)
				direction = 1;
			else
				return false;

			int newIndex = currentlySelected.getSelectedIndex() + direction;
			newIndex = MathUtils.clamp(newIndex, 0, currentlySelected.getItems().size - 1);
			currentlySelected.setSelectedIndex(newIndex);
			return true;
		}

	};

	public FC_List(Skin skin, FileChooser_Listener listener)
	{
		this(skin.get(Style.class), listener);
		setSkin(skin);
	}

	public FC_List(Skin skin, String styleName, FileChooser_Listener listener)
	{
		this(skin.get(styleName, Style.class), listener);
		setSkin(skin);
	}

	public FC_List(Style style, FileChooser_Listener listener)
	{
		super(listener);
		this.style = style;
		buildWidgets();
		build();
		refresh();
	}

	/** Override this if you want to adjust all the Widgets. Be careful though! */
	protected void buildWidgets()
	{
		addListener(keyControlsListener);

		(pathField = new TextField(directory.path(), style.pathFieldStyle)).setTextFieldListener(pathFieldListener);
		currentlySelected = new List<>(style.contentsStyle);
		currentlySelected.setItems(directory.name());
		currentlySelected.addListener(contentsListener);

		(chooseButton = Utils_Scene2D.newButton(style.chooseButtonStyle, "select")).addListener(selectButtonListener);
		(openButton = Utils_Scene2D.newButton(style.openButtonStyle, "open")).addListener(openButtonListener);
		(cancelButton = Utils_Scene2D.newButton(style.cancelButtonStyle, "cancel")).addListener(cancelButtonListener);
		(backButton = Utils_Scene2D.newButton(style.backButtonStyle, "back")).addListener(backButtonListener);
		(parentButton = Utils_Scene2D.newButton(style.parentButtonStyle, "up")).addListener(parentButtonListener);

		contentsPane = style.contentsPaneStyle == null ? new ScrollPane(currentlySelected) : new ScrollPane(currentlySelected, style.contentsPaneStyle);
		contentsPane.setScrollbarsVisible(true);
		EventListener event = new InputListener()
		{	
			@Override
			public boolean touchDown(InputEvent event, float x, float y, int pointer, int button)
			{
				if (pointer != 0 || button != 0) return true;
				
				if(currentlySelected.getSelected() == currentlySelected.getItemAt(y))
				{
					if (currentlySelected().isDirectory())
						openButtonListener.clicked(null, 0, 0); // fake event
					else
						selectButtonListener.clicked(null, 0, 0); // fake event
				
					currentlySelected.getSelection().setDisabled(true);
					return true ; 
				}

				return false;
			}

			// The set disabled is keeping it from craching 
			@Override
			public void touchUp(InputEvent event, float x, float y, int pointer, int button)
			{currentlySelected.getSelection().setDisabled(false);}
				
			
		};

		currentlySelected.addCaptureListener(event) ; 
		
		setBackground(style.background);
	}

	/**
	 * Override this if you want to adjust the {@link Table layout}. Clears this
	 * {@link FC_List}'s children and adds {@link #backButton},
	 * {@link #pathField}, {@link #parentButton}, {@link #contentsPane},
	 * {@link #chooseButton}, {@link #cancelButton} and {@link #openButton} if
	 * {@link #isDirectoriesChoosable()} is true.
	 */
	@Override
	protected void build()
	{
		clearChildren();
		Style style = getStyle();
		add(backButton).fill().space(style.space);
		add(pathField).fill().space(style.space);
		add(parentButton).fill().space(style.space).row();
		add(contentsPane).colspan(3).expand().fill().space(style.space).row();
		
		if (isDirectoriesChoosable())
			add(openButton).fill().space(style.space);
		
		add(chooseButton).fill().colspan(isDirectoriesChoosable() ? 1 : 2).space(style.space);
		add(cancelButton).fill().space(style.space);
	}

	/** refreshes the {@link #currentlySelected} */
	public void refresh()
	{
		scan(directory);
	}

	/** populates {@link #currentlySelected} with the children of {@link #directory} */
	protected void scan(FileHandle dir)
	{
		File[] files = dir.file().listFiles(handlingFileFilter);
		String[] names = new String[files.length];
		for (int i = 0; i < files.length; i++)
		{
			String name = files[i].getName();
			if (files[i].isDirectory())
				name += File.separator;
			names[i] = name;
		}
		currentlySelected.setItems(names);
		
	}

	/** @return the file currently selected in {@link #currentlySelected} */
	public FileHandle currentlySelected()
	{
		String selected = currentlySelected.getSelected();
		return selected == null ? directory : directory.child(selected);
	}

	/**
	 * set {@link #directory} and adds it to {@link #fileHistory}
	 * 
	 * @see #setDirectory(FileHandle, boolean)
	 */
	public void setDirectory(FileHandle dir)
	{
		setDirectory(dir, true);
	}

	/** sets {@link #directory} and updates all things that need to be udpated */
	public void setDirectory(FileHandle dir, boolean addToHistory)
	{
		if (dir.file().canRead())
		{
			FileHandle loc = dir.isDirectory() ? dir : dir.parent();
			
			if (addToHistory)
				fileHistory.add(Gdx.files.absolute(dir.path()));
			
			scan(directory = loc);
			pathField.setText(loc.path());
			pathField.setCursorPosition(pathField.getText().length());
		} 
		else
			Gdx.app.error(FC_List.class.getSimpleName(), " cannot read " + dir);
	}

	/** @return the {@link #backButton} */
	public Button getBackButton()
	{
		return backButton;
	}

	/** @param backButton the {@link #backButton} to set */
	public void setBackButton(Button backButton)
	{
		backButton.addListener(backButtonListener);
		getCell(this.backButton).setActor(this.backButton = backButton);
	}

	/** @return the {@link #cancelButton} */
	public Button getCancelButton()
	{
		return cancelButton;
	}

	/** @param cancelButton the {@link #cancelButton} to set */
	public void setCancelButton(Button cancelButton)
	{
		cancelButton.addListener(cancelButtonListener);
		getCell(this.cancelButton).setActor(this.cancelButton = cancelButton);
	}

	/** @return the {@link #chooseButton} */
	public Button getChooseButton()
	{
		return chooseButton;
	}

	/** @param chooseButton the {@link #chooseButton} to set */
	public void setChooseButton(Button chooseButton)
	{
		chooseButton.addListener(selectButtonListener);
		getCell(this.chooseButton).setActor(this.chooseButton = chooseButton);
	}

	/** @return the {@link #currentlySelected} */
	public List<String> getContents()
	{
		return currentlySelected;
	}

	/** @param contents the {@link #currentlySelected} to set */
	public void setContents(List<String> contents)
	{
		getCell(this.currentlySelected).setActor(this.currentlySelected = contents);
	}

	/** @return the {@link #contentsPane} */
	public ScrollPane getContentsPane()
	{
		return contentsPane;
	}

	/** @param contentsPane the {@link #contentsPane} to set */
	public void setContentsPane(ScrollPane contentsPane)
	{
		getCell(this.contentsPane).setActor(this.contentsPane = contentsPane);
	}

	/** @return the {@link #directory} */
	public FileHandle getDirectory()
	{
		return directory;
	}

	/** @return the {@link #fileHistory} */
	public Array<FileHandle> getFileHistory()
	{
		return fileHistory;
	}

	/** @param fileHistory the {@link #fileHistory} to set */
	public void setFileHistory(Array<FileHandle> fileHistory)
	{
		this.fileHistory = fileHistory;
	}

	/** @return the {@link #openButton} */
	public Button getOpenButton()
	{
		return openButton;
	}

	/** @param openButton the {@link #openButton} to set */
	public void setOpenButton(Button openButton)
	{
		openButton.addListener(openButtonListener);
		getCell(this.openButton).setActor(this.openButton = openButton);
	}

	/** @return the {@link #parentButton} */
	public Button getParentButton()
	{
		return parentButton;
	}

	/** @param parentButton the {@link #parentButton} to set */
	public void setParentButton(Button parentButton)
	{
		parentButton.addListener(parentButtonListener);
		getCell(this.parentButton).setActor(this.parentButton = parentButton);
	}

	/** @return the {@link #pathField} */
	public TextField getPathField()
	{
		return pathField;
	}

	/** @param pathField the {@link #pathField} to set */
	public void setPathField(TextField pathField)
	{
		pathField.setTextFieldListener(pathFieldListener);
		getCell(this.pathField).setActor(this.pathField = pathField);
	}

	/** {@link #build() builds} if necessary */
	@Override
	public void setDirectoriesChoosable(boolean directoriesChoosable)
	{
		if (isDirectoriesChoosable() != directoriesChoosable)
		{
			super.setDirectoriesChoosable(directoriesChoosable);
			build();
		}
	}

	/** @return the {@link #style} */
	public Style getStyle()
	{
		return style;
	}

	/** @param style the {@link #style} to set and use for all widgets */
	public void setStyle(Style style)
	{
		this.style = style;
		backButton.setStyle(style.backButtonStyle);
		cancelButton.setStyle(style.cancelButtonStyle);
		chooseButton.setStyle(style.chooseButtonStyle);
		currentlySelected.setStyle(style.contentsStyle);
		contentsPane.setStyle(style.contentsPaneStyle);
		openButton.setStyle(style.openButtonStyle);
		parentButton.setStyle(style.parentButtonStyle);
		pathField.setStyle(style.pathFieldStyle);
	}

	/**
	 * defines styles for the widgets of a {@link FC_List}
	 * 
	 * @author dermetfan
	 */
	public static class Style implements Serializable
	{

		/** the style of {@link #pathField} */
		public TextFieldStyle pathFieldStyle;

		/** the style of {@link #currentlySelected} */
		public ListStyle contentsStyle;

		/** the styles of the buttons */
		public ButtonStyle chooseButtonStyle, openButtonStyle, cancelButtonStyle, backButtonStyle, parentButtonStyle;

		/** the spacing between the Widgets */
		public float space;

		/** optional */
		public ScrollPaneStyle contentsPaneStyle;

		/** optional */
		public Drawable background;

		public Style()
		{
		}

		public Style(Style style)
		{
			set(style);
		}

		public Style(TextFieldStyle textFieldStyle, ListStyle listStyle, ButtonStyle buttonStyles, Drawable background)
		{
			this(textFieldStyle, listStyle, buttonStyles, buttonStyles, buttonStyles, buttonStyles, buttonStyles,
					background);
		}

		public Style(TextFieldStyle pathFieldStyle, ListStyle contentsStyle, ButtonStyle chooseButtonStyle,
				ButtonStyle openButtonStyle, ButtonStyle cancelButtonStyle, ButtonStyle backButtonStyle,
				ButtonStyle parentButtonStyle, Drawable background)
		{
			this.pathFieldStyle = pathFieldStyle;
			this.contentsStyle = contentsStyle;
			this.chooseButtonStyle = chooseButtonStyle;
			this.openButtonStyle = openButtonStyle;
			this.cancelButtonStyle = cancelButtonStyle;
			this.backButtonStyle = backButtonStyle;
			this.parentButtonStyle = parentButtonStyle;
			this.background = background;
		}

		/**
		 * @param style the {@link Style} to set this instance to (giving all fields the
		 *              same value)
		 */
		public void set(Style style)
		{
			pathFieldStyle = style.pathFieldStyle;
			contentsStyle = style.contentsStyle;
			chooseButtonStyle = style.chooseButtonStyle;
			openButtonStyle = style.openButtonStyle;
			cancelButtonStyle = style.cancelButtonStyle;
			backButtonStyle = style.backButtonStyle;
			parentButtonStyle = style.parentButtonStyle;
			contentsPaneStyle = style.contentsPaneStyle;
			background = style.background;
			space = style.space;
		}

		/**
		 * @param style the {@link #backButtonStyle}, {@link #cancelButtonStyle},
		 *              {@link #chooseButtonStyle}, {@link #openButtonStyle} and
		 *              {@link #parentButtonStyle} to set
		 */
		public void setButtonStyles(ButtonStyle style)
		{
			chooseButtonStyle = openButtonStyle = cancelButtonStyle = backButtonStyle = parentButtonStyle = style;
		}

		@Override
		public void write(Json json)
		{
			json.writeObjectStart("");
			json.writeFields(this);
			json.writeObjectEnd();
		}

		@Override
		public void read(Json json, JsonValue jsonData)
		{
			ButtonStyle tmpBS = Utils_Scene2D.readButtonStyle("buttonStyles", json, jsonData);
			setButtonStyles(tmpBS);
			tmpBS = null;

			tmpBS = Utils_Scene2D.readButtonStyle("backButtonStyle", json, jsonData);
			if (tmpBS != null)
				backButtonStyle = tmpBS;
			tmpBS = null;

			tmpBS = Utils_Scene2D.readButtonStyle("cancelButtonStyle", json, jsonData);
			if (tmpBS != null)
				cancelButtonStyle = tmpBS;
			tmpBS = null;

			tmpBS = Utils_Scene2D.readButtonStyle("chooseButtonStyle", json, jsonData);
			if (tmpBS != null)
				chooseButtonStyle = tmpBS;
			tmpBS = null;

			tmpBS = Utils_Scene2D.readButtonStyle("openButtonStyle", json, jsonData);
			if (tmpBS != null)
				openButtonStyle = tmpBS;
			tmpBS = null;

			tmpBS = Utils_Scene2D.readButtonStyle("parentButtonStyle", json, jsonData);
			if (tmpBS != null)
				parentButtonStyle = tmpBS;

			contentsStyle = json.readValue("contentsStyle", ListStyle.class, jsonData);
			pathFieldStyle = json.readValue("pathFieldStyle", TextFieldStyle.class, jsonData);
			if (jsonData.has("contentsPaneStyle"))
				contentsPaneStyle = json.readValue("contentsPaneStyle", ScrollPaneStyle.class, jsonData);
			if (jsonData.has("space"))
				space = json.readValue("space", float.class, jsonData);
		}

	}

}