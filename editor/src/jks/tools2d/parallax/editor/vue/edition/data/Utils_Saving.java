package jks.tools2d.parallax.editor.vue.edition.data;

import static jks.tools2d.parallax.editor.vue.edition.Vue_Edition.parallax_Heart;
import static jks.tools2d.parallax.editor.vue.edition.data.GVars_Vue_Edition.datas;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.stream.Collectors;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.tools.texturepacker.TexturePacker.Settings;
import com.esotericsoftware.kryo.io.Output;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.editor.gvars.FVars_Extensions;
import jks.tools2d.parallax.editor.gvars.GVars_Kryo;
import jks.tools2d.parallax.editor.vue.edition.VE_Options;
import jks.tools2d.parallax.pages.Page_Model;
import jks.tools2d.parallax.pages.Utils_Page;
import jks.tools2d.parallax.pages.WholePage_Model;

public class Utils_Saving
{
	private Utils_Saving()
	{}
	
	public static void saving_Parallax_Kryo(String where, String whatName)
	{
		try 
		{
			ArrayList<ParallaxLayer> parallaxs = parallax_Heart.parallaxPage.layers ; 
			WholePage_Model outputFinalModel = buildSavingWholePageFlat(parallaxs) ; 
			
			Output output = new Output(new FileOutputStream(where + "/" + whatName + "." + FVars_Extensions.PARALLAX));
			GVars_Kryo.kryo.writeObject(output, outputFinalModel);
	    	output.close();
		}
		catch(Exception e)
		{
			// TODO GIVE SIGNAL
			e.printStackTrace();
		}
		
	}
	
	@SuppressWarnings("unchecked")
	public static ArrayList<Position_Infos> buildSavingOutsideValues(ArrayList<ParallaxLayer> parallaxs)
	{	
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
		}
		
		outputModel.atlasName =  parallax_Heart.currentPage.pageModel.atlasName ; 
		
		if(parallax_Heart.topSquare != null)
		{
			outputFinalModel.topHalf_top = parallax_Heart.topSquare.topColor ; 
			outputFinalModel.topHalf_bottom = parallax_Heart.topSquare.bottomColor ; 
		}
		
		if(parallax_Heart.bottomSquare != null)
		{
			outputFinalModel.bottomHalf_top = parallax_Heart.bottomSquare.topColor ; 
			outputFinalModel.bottomHalf_bottom = parallax_Heart.bottomSquare.bottomColor ; 
		}
		
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
		if(parallax_Heart.topSquare != null)
		{
			outputFinalModel.topHalf_top = parallax_Heart.topSquare.topColor ; 
			outputFinalModel.topHalf_bottom = parallax_Heart.topSquare.bottomColor ; 
		}
		
		if(parallax_Heart.bottomSquare != null)
		{
			outputFinalModel.bottomHalf_top = parallax_Heart.bottomSquare.topColor ; 
			outputFinalModel.bottomHalf_bottom = parallax_Heart.bottomSquare.bottomColor ; 
		}
		
		outputFinalModel.pageModel = outputModel ; 
		
		return outputFinalModel ; 
		
	}
	
	public static void saving_Parallax_Project(String where, String whatName)
	{
		try 
		{
			ArrayList<ParallaxLayer> parallaxs = parallax_Heart.parallaxPage.layers ; 
			WholePage_Editor outputFinalModel = buildSavingWholePageAsProject(parallaxs) ; 
			datas.prepareForSaving(outputFinalModel);
			
			Output output = new Output(new FileOutputStream(where + "/" + whatName + "." + FVars_Extensions.PARALLAX_PROJECT));
			GVars_Kryo.kryo.writeObject(output, datas);
	    	output.close();
		}
		catch(Exception e)
		{
			// TODO GIVE SIGNAL
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
//			packer.addImage(file.file());
//		}
	
//		packer.pack(filePath, "testingPackage");
	}

	
	
	public static void autoSave() 
	{
		try 
		{
			String filePath = new File("").getAbsolutePath();
			String relativePath = filePath + "/Files/AutoSave" ;
			DateFormat dateFormat = new SimpleDateFormat("yyyy'MM'dd_HH'mm'ss");
			Date date = new Date();
			String saveName = "AUTO_" + VE_Options.parallaxName.getText() + "_" + dateFormat.format(date) ; 
			saving_Parallax_Project(relativePath, saveName) ; 
		}
		catch(Exception e)
		{
			
		}
	}
	
}