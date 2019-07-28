package jks.tools2d.filechooser;

import java.io.File;
import java.io.FileFilter;

import com.badlogic.gdx.scenes.scene2d.ui.Table;


public abstract class FileChooser extends Table 
{

	private FileChooser_Listener listener;

	protected final FileFilter handlingFileFilter = new FileFilter() 
	{
		@Override
		public boolean accept(File file) 
		{return (showHidden || !file.isHidden()) && (fileFilter == null || fileFilter.accept(file));}
	};

	/** a personal filter to determine if certain files should be shown */
	private FileFilter fileFilter;

	/** if hidden files should be shown */
	private boolean showHidden = false;

	/** if directories can be chosen */
	private boolean directoriesChoosable;

	/** @param listener the {@link #listener} */
	public FileChooser(FileChooser_Listener listener) 
	{this.listener = listener;}

	/** override this to build widgets in an implementation */
	protected abstract void build();

	public FileChooser_Listener getListener()
	{return listener;}

	public void setListener(FileChooser_Listener listener) 
	{this.listener = listener;}

	public FileFilter getFileFilter() 
	{return fileFilter;}

	public void setFileFilter(FileFilter fileFilter) 
	{this.fileFilter = fileFilter;}

	public boolean isShowHidden() 
	{return showHidden;}

	public void setShowHidden(boolean showHidden) 
	{this.showHidden = showHidden;}

	public boolean isDirectoriesChoosable()
	{return directoriesChoosable;}

	public void setDirectoriesChoosable(boolean directoriesChoosable) 
	{this.directoriesChoosable = directoriesChoosable;}

}