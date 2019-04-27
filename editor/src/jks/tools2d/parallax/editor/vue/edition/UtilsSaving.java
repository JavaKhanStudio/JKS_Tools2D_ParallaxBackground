package jks.tools2d.parallax.editor.vue.edition;

import static jks.tools2d.parallax.editor.vue.edition.Vue_Edition.parallax_Heart;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.tools.texturepacker.TexturePacker.Settings;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.kryo.io.Output;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.editor.gvars.FVars_Extensions;
import jks.tools2d.parallax.editor.gvars.GVars_Heart_Editor;
import jks.tools2d.parallax.pages.Page_Model;
import jks.tools2d.parallax.pages.Utils_Page;
import jks.tools2d.parallax.pages.WholePage_Model;

public class UtilsSaving
{

	public static void saving_Parallax_Kryo(String where, String whatName)
	{
		try 
		{
			Output output = new Output(new FileOutputStream(where + "/" + whatName + "." + FVars_Extensions.PARALLAX));
	    	
			Array<ParallaxLayer> parallaxs = parallax_Heart.parallaxPage.layers ; 
			Position_Infos info ;
			
			for(ParallaxLayer layer : parallaxs)
			{
				info = GVars_Vue_Edition.imageRef.get(layer.getTexRegion()) ; 
				if(!info.fromAtlas)
				{
					
				}
			}
			
			WholePage_Model outputFinalModel = new WholePage_Model() ;
			Page_Model outputModel = new Page_Model() ; 
			
			  
			for(ParallaxLayer layer: parallaxs)
			{
				info = GVars_Vue_Edition.imageRef.get(layer.getTexRegion()) ; 
				outputModel.pageList.add(Utils_Page.buildFromPage(layer, info.url, info.position)) ; 
			}
			
			outputModel.atlasPath =  parallax_Heart.currentPage.pageModel.atlasPath ; 
			outputFinalModel.topHalf_top = parallax_Heart.topSquare.topColor ; 
			outputFinalModel.topHalf_bottom = parallax_Heart.topSquare.bottomColor ; 
			
			outputFinalModel.bottomHalf_top = parallax_Heart.bottomSquare.topColor ; 
			outputFinalModel.bottomHalf_bottom = parallax_Heart.bottomSquare.bottomColor ; 
			outputFinalModel.pageModel = outputModel ; 
			GVars_Heart_Editor.kryo.writeObject(output, outputFinalModel);
	    	output.close();
		}
		catch(FileNotFoundException e)
		{
			System.out.println("probleme grave avec le save");
		}
		
	}
	
	public void packTexture(String path,String name,ParallaxLayer layer)
	{
		Settings settings = new Settings() ; 
		settings.maxWidth = 32768 ;
		settings.maxHeight = 32768 ;
		PixmapPacker pixmap = new PixmapPacker(3000, 3000, Format.RGBA8888, 0, false); 
		pixmap.pack("", null) ; 
		pixmap.generateTextureAtlas(TextureFilter.MipMapNearestNearest, TextureFilter.MipMapNearestNearest, true) ; 
//		TexturePacker packer = new TexturePacker(settings);
//		packer.addImage(file.file());
//		File filePath = new File(path + name);
//		FileHandle handle = new FileHandle(filePath);
//		FileHandle[] fileList = handle.list() ;
//		
//		for(FileHandle file : fileList)
//		{
//			
//			packer.addImage(file.file());
//		}
	
//		packer.pack(filePath, "testingPackage");
	}
	
}
