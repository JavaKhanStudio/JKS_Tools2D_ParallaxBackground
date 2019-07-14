package jks.tools2d.filechooser;

import java.io.File;
import java.io.FileFilter;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Tree;
import com.badlogic.gdx.scenes.scene2d.ui.Tree.Node;
import com.badlogic.gdx.scenes.scene2d.ui.Tree.TreeStyle;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.Selection;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.Json.Serializable;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.Pools;

import jks.tools2d.libgdxutils.Accessor;
import jks.tools2d.libgdxutils.Utils_Scene2D;


public class FC_Tree extends FileChooser
{

	public static Node fileNode(FileHandle file, LabelStyle labelStyle)
	{return fileNode(file, labelStyle, null);}

	public static Node fileNode(FileHandle file, LabelStyle labelStyle, Accessor<Void, Node> nodeConsumer)
	{return fileNode(file, null, labelStyle, nodeConsumer);}

	/** @see #fileNode(FileHandle, java.io.FileFilter, LabelStyle, Accessor) */
	public static Node fileNode(FileHandle file, FileFilter filter, final LabelStyle labelStyle)
	{
		return fileNode(file, filter, labelStyle, null);
	}


	public static Node fileNode(FileHandle file, FileFilter filter, final LabelStyle labelStyle, Accessor<Void, Node> nodeConsumer)
	{
		return fileNode(file, filter, new Accessor<Label, FileHandle>()
		{
			@Override
			public Label access(FileHandle file)
			{
				String name = file.name();
				if (file.isDirectory())
					name += File.separator;
				return new Label(name, labelStyle);
			}
		}, nodeConsumer);
	}

	/** @see #fileNode(FileHandle, FileFilter, Accessor, Accessor) */
	public static Node fileNode(FileHandle file, FileFilter filter, Accessor<Label, FileHandle> labelSupplier)
	{
		return fileNode(file, filter, labelSupplier, null);
	}

	/**
	 * creates an anonymous subclass of {@link Node} that recursively adds the
	 * children of the given file to it when being {@link Node#setExpanded(boolean)
	 * expanded} for the first time
	 * 
	 * @param file          the file to put in {@link Node#setObject(Object)}
	 * @param filter        Filters children from being added. May be null to accept
	 *                      all files.
	 * @param labelSupplier supplies labels to use
	 * @param nodeConsumer  Does something with nodes after they were created. May
	 *                      be null.
	 * @return the created Node
	 */
	public static Node fileNode(final FileHandle file, final FileFilter filter,
			final Accessor<Label, FileHandle> labelSupplier, final Accessor<Void, Node> nodeConsumer)
	{
		Label label = labelSupplier.access(file);

		Node node;
		if (file.isDirectory())
		{
			final Node dummy = new Node(new Actor());

			node = new Node(label)
			{
				private boolean childrenAdded;

				@Override
				public void setExpanded(boolean expanded)
				{
					if (expanded == isExpanded())
						return;

					if (expanded && !childrenAdded)
					{
						if (filter != null)
							for (File child : file.file().listFiles(filter))
								add(fileNode(file.child(child.getName()), filter, labelSupplier, nodeConsumer));
						else
							for (FileHandle child : file.list())
								add(fileNode(child, filter, labelSupplier, nodeConsumer));
						childrenAdded = true;
						remove(dummy);
					}

					super.setExpanded(expanded);
				}
			};
			node.add(dummy);

			if (nodeConsumer != null)
				nodeConsumer.access(dummy);
		} else
			node = new Node(label);
		node.setObject(file);

		if (nodeConsumer != null)
			nodeConsumer.access(node);

		return node;
	}

	private Style style;

	private Tree tree;

	private ScrollPane treePane;

	private Button chooseButton, cancelButton;


	public final ClickListener treeListener = new ClickListener()
	{
		@Override
		public void clicked(InputEvent event, float x, float y)
		{
			Selection<Node> selection = tree.getSelection();
			if (selection.size() < 1)
			{
				chooseButton.setDisabled(true);
				return;
			}
			if (!isDirectoriesChoosable())
			{
				Object lastObj = selection.getLastSelected().getObject();
				if (lastObj instanceof FileHandle)
				{
					FileHandle file = (FileHandle) lastObj;
					if (file.isDirectory())
					{
						chooseButton.setDisabled(true);
						return;
					}
				}
			}
			chooseButton.setDisabled(false);
		}
	};

	public final ClickListener chooseButtonListener = new ClickListener()
	{
		@Override
		public void clicked(InputEvent event, float x, float y)
		{
			if (chooseButton.isDisabled())
				return;
			Selection<Node> selection = tree.getSelection();
			if (selection.size() < 1)
				return;
			if (selection.getMultiple())
			{
				@SuppressWarnings("unchecked")
				Array<FileHandle> files = Pools.obtain(Array.class);
				for (Node node : selection)
				{
					Object object = node.getObject();
					if (object instanceof FileHandle)
					{
						FileHandle file = (FileHandle) object;
						if (isDirectoriesChoosable() || !file.isDirectory())
							files.add(file);
					}
				}
				getListener().choose(files);
				files.clear();
				Pools.free(files);
			} else
			{
				Object object = selection.getLastSelected().getObject();
				if (object instanceof FileHandle)
				{
					FileHandle file = (FileHandle) object;
					if (isDirectoriesChoosable() || !file.isDirectory())
						getListener().choose(file);
				}
			}
		}
	};


	public final ClickListener cancelButtonListener = new ClickListener()
	{
		@Override
		public void clicked(InputEvent event, float x, float y)
		{getListener().cancel();}
	};

	public FC_Tree(Skin skin, FileChooser_Listener listener)
	{
		this(skin.get(Style.class), listener);
		setSkin(skin);
	}


	public FC_Tree(Skin skin, String styleName, FileChooser_Listener listener)
	{
		this(skin.get(styleName, Style.class), listener);
		setSkin(skin);
	}

	public FC_Tree(Style style, FileChooser_Listener listener)
	{
		super(listener);
		this.style = style;
		buildWidgets();
		build();
	}


	public Node add(FileHandle file)
	{
		Node node = fileNode(file, handlingFileFilter, style.labelStyle);
		tree.add(node);
		return node;
	}

	protected void buildWidgets()
	{
		(tree = new Tree(style.treeStyle)).addListener(treeListener);
		if (style.scrollPaneStyle != null)
			treePane = new ScrollPane(tree, style.scrollPaneStyle);
		else
			treePane = new ScrollPane(tree);
		
		(chooseButton = Utils_Scene2D.newButton(style.selectButtonStyle, "select")).addListener(chooseButtonListener);
		
		chooseButton.setDisabled(true);
		
		(cancelButton = Utils_Scene2D.newButton(style.cancelButtonStyle, "cancel")).addListener(cancelButtonListener);
	}

	@Override
	protected void build()
	{
		clearChildren();
		treePane.setWidget(tree);
		add(treePane).colspan(2).row();
		add(chooseButton).fill();
		add(cancelButton).fill();
	}

	public Tree getTree()
	{return tree;}

	/** @param tree the {@link #tree} to set */
	public void setTree(Tree tree)
	{
		if (tree == null)
			throw new IllegalArgumentException("tree must not be null");
		
		this.tree = tree;
	}

	/** @return the {@link #style} */
	public Style getStyle()
	{
		return style;
	}

	/** @param style the {@link #style} to set */
	public void setStyle(Style style)
	{
		this.style = style;
		setBackground(style.background);
		tree.setStyle(style.treeStyle);
		chooseButton.setStyle(style.selectButtonStyle);
		cancelButton.setStyle(style.cancelButtonStyle);
	}

	/**
	 * defines styles for the widgets of a {@link FC_Tree}
	 * 
	 * @author dermetfan
	 */
	public static class Style implements Serializable
	{

		/** the style for the {@link FC_Tree#tree tree} */
		public TreeStyle treeStyle;

		/** the style for {@link FC_Tree#treePane} */
		public ScrollPaneStyle scrollPaneStyle;

		/** the style for the labels in the tree */
		public LabelStyle labelStyle;

		/** the button styles */
		public ButtonStyle selectButtonStyle, cancelButtonStyle;

		/** optional */
		public Drawable background;

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
			treeStyle = json.readValue("treeStyle", TreeStyle.class, jsonData);
			
			if (jsonData.has("scrollPaneStyle"))
				scrollPaneStyle = json.readValue("scrollPaneStyle", ScrollPaneStyle.class, jsonData);
			
			labelStyle = json.readValue("labelStyle", LabelStyle.class, jsonData);
			selectButtonStyle = Utils_Scene2D.readButtonStyle("selectButtonStyle", json, jsonData);
			cancelButtonStyle = Utils_Scene2D.readButtonStyle("cancelButtonStyle", json, jsonData);
			
			if (jsonData.has("background"))
				background = json.readValue("background", Drawable.class, jsonData);
		}

	}

}
