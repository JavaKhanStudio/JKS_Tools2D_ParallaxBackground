package jks.tools2d.packing.texture;

import static jks.tools2d.parallax.fvars.FVars_Path.images;
import static jks.tools2d.parallax.fvars.FVars_Path.path;

import java.io.File;
import java.io.FileOutputStream;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.tools.texturepacker.TexturePacker;
import com.badlogic.gdx.tools.texturepacker.TexturePacker.Settings;
import com.esotericsoftware.kryo.io.Output;

import jks.tools2d.parallax.fvars.FVars_Path; 

public class TexturePacking_Testing 
{
	static public void main (String[] args) throws Exception
    {
		Settings settings = new Settings() ; 
		settings.maxWidth = 32768 ;
		settings.maxHeight = 32768 ;
		TexturePacker packer = new TexturePacker(settings);
		
		File filePath = new File(path + images);
		FileHandle handle = new FileHandle(filePath);
		FileHandle[] fileList = handle.list() ;

		String extension ;
		for(FileHandle file : fileList)
		{
			extension = file.extension() ;
			
			if("png".equals(extension) || "jpg".equals(extension) )
				packer.addImage(file.file());
		}
	
		packer.pack(filePath, "testingPackage");
    }
}
