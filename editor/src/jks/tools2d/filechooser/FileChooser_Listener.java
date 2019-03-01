package jks.tools2d.filechooser;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;

public interface FileChooser_Listener 
{
	public void choose(FileHandle file);

	public void choose(Array<FileHandle> files);

	public void cancel();
}
