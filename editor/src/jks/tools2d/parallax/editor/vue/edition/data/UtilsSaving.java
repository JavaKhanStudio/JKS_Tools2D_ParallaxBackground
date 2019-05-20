package jks.tools2d.parallax.editor.vue.edition.data;

import static jks.tools2d.parallax.editor.vue.edition.Vue_Edition.parallax_Heart;
import static jks.tools2d.parallax.editor.vue.edition.data.GVars_Vue_Edition.datas;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.stream.Collectors;

import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.tools.texturepacker.TexturePacker.Settings;
import com.esotericsoftware.kryo.io.Output;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.editor.gvars.FVars_Extensions;
import jks.tools2d.parallax.editor.gvars.GVars_Kryo;
import jks.tools2d.parallax.pages.Page_Model;
import jks.tools2d.parallax.pages.Utils_Page;
import jks.tools2d.parallax.pages.WholePage_Model;

public class UtilsSaving
{
	private UtilsSaving()
	{}
	
	public static void saving_Parallax_Kryo(String where, String whatName)
	{
		try 
		{
			Output output = new Output(new FileOutputStream(where + "/" + whatName + "." + FVars_Extensions.PARALLAX));
	    	
			ArrayList<ParallaxLayer> parallaxs = parallax_Heart.parallaxPage.layers ; 
			WholePage_Model outputFinalModel = buildSavingWholePageFlat(parallaxs) ; 
			
			GVars_Kryo.kryo.writeObject(output, outputFinalModel);
	    	output.close();
		}
		catch(FileNotFoundException e)
		{
			e.printStackTrace();
		}
		
	}
	
	public static ArrayList<Position_Infos> buildSavingOutsideValues(ArrayList<ParallaxLayer> parallaxs)
	{
		
		
		/* OLD
		Position_Infos info ;
		ArrayList<Position_Infos> returning = new ArrayList<>() ; 
		for(ParallaxLayer layer: parallaxs)
		{
			info = GVars_Vue_Edition.imageRef.get(layer.getTexRegion()) ; 
			if(!info.fromAtlas)
				returning.add(info) ; 
		}
		
		return returning ; 
		*/
		
		return new ArrayList(parallaxs.stream().filter(
				x -> !GVars_Vue_Edition.imageRef.get(x.getTexRegion()).fromAtlas)
					.collect(Collectors.toList())) ; 
		
	}
	
	public static WholePage_Model buildSavingWholePageFlat(ArrayList<ParallaxLayer> parallaxs)
	{
		WholePage_Model outputFinalModel = new WholePage_Model() ;
		Page_Model outputModel = new Page_Model() ; 
		
		Position_Infos info ;
		  
		for(ParallaxLayer layer: parallaxs)
		{
			info = GVars_Vue_Edition.imageRef.get(layer.getTexRegion()) ; 
			if(info.fromAtlas)
				outputModel.pageList.add(Utils_Page.buildFromPage(layer, info.url, info.position)) ; 
//			else
		}
		
		outputModel.atlasName =  parallax_Heart.currentPage.pageModel.atlasName ; 
//		outputModel.atlasName = infos.projectName + "." + FVars_Extensions.ATLAS;
		outputFinalModel.topHalf_top = parallax_Heart.topSquare.topColor ; 
		outputFinalModel.topHalf_bottom = parallax_Heart.topSquare.bottomColor ; 
		
		outputFinalModel.bottomHalf_top = parallax_Heart.bottomSquare.topColor ; 
		outputFinalModel.bottomHalf_bottom = parallax_Heart.bottomSquare.bottomColor ; 
		outputFinalModel.pageModel = outputModel ; 
		
		return outputFinalModel ; 	
	}
	
	public static WholePage_Editor buildSavingWholePageAsProject(ArrayList<ParallaxLayer> parallaxs)
	{
		WholePage_Editor outputFinalModel = new WholePage_Editor() ;
		Page_Model outputModel = new Page_Model() ; 
		
		Position_Infos info ;
		  
		for(ParallaxLayer layer: parallaxs)
		{	
			info = GVars_Vue_Edition.imageRef.get(layer.getTexRegion()) ; 
			outputModel.pageList.add(Utils_Page.buildFromPage(layer, info.url, info.position)) ; 
			outputFinalModel.inside.add(info.fromAtlas) ; 
		}
		
		outputModel.atlasName =  parallax_Heart.currentPage.pageModel.atlasName ; 
//		outputModel.atlasName = infos.projectName + "." + FVars_Extensions.ATLAS;
		outputFinalModel.topHalf_top = parallax_Heart.topSquare.topColor ; 
		outputFinalModel.topHalf_bottom = parallax_Heart.topSquare.bottomColor ; 
		
		outputFinalModel.bottomHalf_top = parallax_Heart.bottomSquare.topColor ; 
		outputFinalModel.bottomHalf_bottom = parallax_Heart.bottomSquare.bottomColor ; 
		outputFinalModel.pageModel = outputModel ; 
		
		return outputFinalModel ; 
		
	}
	
	public static void saving_Parallax_Project(String where, String whatName)
	{
		try 
		{
			Output output = new Output(new FileOutputStream(where + "/" + whatName + "." + FVars_Extensions.PARALLAX_PROJECT));
			ArrayList<ParallaxLayer> parallaxs = parallax_Heart.parallaxPage.layers ; 
			WholePage_Editor outputFinalModel = buildSavingWholePageAsProject(parallaxs) ; 
			datas.prepareForSaving(outputFinalModel);
			
			GVars_Kryo.kryo.writeObject(output, datas);
	    	output.close();
		}
		catch(FileNotFoundException e)
		{
			e.printStackTrace();
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
