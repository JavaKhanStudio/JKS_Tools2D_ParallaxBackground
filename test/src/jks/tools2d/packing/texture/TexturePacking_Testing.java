package jks.tools2d.packing.texture;

import java.io.File;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.tools.texturepacker.TexturePacker;
import com.badlogic.gdx.tools.texturepacker.TexturePacker.Settings; 

public class TexturePacking_Testing 
{
	
	static String pathMe = "D:/Users/Simon/Downloads/Test Animation lanterne-20190617T082213Z-001/Test Animation lanterne" ;
	static String fileName = "testingPackage" ; 
	
	static public void main (String[] args) throws Exception
    {
		Settings settings = new Settings() ; 
		settings.maxWidth = 32768 ;
		settings.maxHeight = 32768 ;
		settings.format = Format.RGBA8888 ; 
		TexturePacker packer = new TexturePacker(settings);
				
		
		File png = new File(pathMe + "/" + fileName + ".png");
		if(png != null)
			png.delete() ; 
		File atlas = new File(pathMe + "/" + fileName + ".atlas");
		if(atlas != null)
			atlas.delete() ; 
				
		
		File filePath = new File(pathMe);
		FileHandle handle = new FileHandle(filePath);
		FileHandle[] fileList = handle.list() ;

		String extension ;
		for(FileHandle file : fileList)
		{
			extension = file.extension() ;
			
			if("png".equals(extension) || "jpg".equals(extension) )
				packer.addImage(file.file());
		}
	
		packer.pack(filePath, fileName);
		System.out.println("Packing -- End");
    }
}
