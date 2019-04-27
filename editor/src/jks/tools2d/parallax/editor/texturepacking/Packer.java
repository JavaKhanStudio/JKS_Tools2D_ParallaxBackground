package jks.tools2d.parallax.editor.texturepacking;

import com.badlogic.gdx.utils.Array;

public interface Packer 
{
	public Array<Page> pack (Array<Rect> inputRects);
	
}
