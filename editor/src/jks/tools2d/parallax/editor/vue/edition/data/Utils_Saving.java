package jks.tools2d.parallax.editor.vue.edition.data;

import static jks.tools2d.parallax.editor.gvars.GVars_Serialization.objectMapper;
import static jks.tools2d.parallax.editor.vue.edition.VE_Options.parallaxName;
import static jks.tools2d.parallax.editor.vue.edition.VE_Options.parallaxPath;
import static jks.tools2d.parallax.editor.vue.edition.Vue_Edition.parallax_Heart;
import static jks.tools2d.parallax.editor.vue.edition.data.GVars_Vue_Edition.projectDatas;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import com.esotericsoftware.kryo.io.Output;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.kotcrab.vis.ui.util.dialog.Dialogs;
import com.kotcrab.vis.ui.util.dialog.Dialogs.OptionDialogType;
import com.kotcrab.vis.ui.util.dialog.OptionDialogAdapter;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.editor.gvars.FVars_Extensions;
import jks.tools2d.parallax.editor.gvars.GVars_Serialization;
import jks.tools2d.parallax.editor.gvars.GVars_Ui;
import jks.tools2d.parallax.editor.vue.edition.VE_Options;
import jks.tools2d.parallax.pages.Page_Model;
import jks.tools2d.parallax.pages.Utils_Page;
import jks.tools2d.parallax.pages.WholePage_Model;

public class Utils_Saving
{
	private Utils_Saving()
	{}
	
	public static void saving_Parallax(String where, String whatName)
	{
		try 
		{
			ArrayList<ParallaxLayer> parallaxs = parallax_Heart.parallaxReader.layers ; 
			
			boolean oneOutside = false; 
			
			for(ParallaxLayer layer: parallaxs)
			{
				oneOutside = !GVars_Vue_Edition.imageRef.get(layer.getTexRegion()).fromAtlas ; 
				if(oneOutside)
					break ; 
			}
			
			if(oneOutside)
				askForFlatening() ; 
			
			WholePage_Model outputFinalModel = buildWholePageForExport(parallaxs) ; 
				
			if(VE_Options.formatLibGDX.isChecked())
				saving_Parallax_Kryo(where,whatName,outputFinalModel) ; 
			
			if(VE_Options.formatJson.isChecked())
				saving_Parallax_JSON(where,whatName,outputFinalModel) ; 
		}
		catch(Exception e)
		{e.printStackTrace();}
		
	}
	
	private static void askForFlatening() 
	{
		final Boolean returningValue ; 
		Dialogs.showOptionDialog(GVars_Ui.mainUi, "option dialog", "Warning ! You have one or more texture from outside the texture atlas. \n Creating the export will flatten the current project, creating a new texture atlas. Would you like to save the project before that?", OptionDialogType.YES_NO_CANCEL, new OptionDialogAdapter() {
			@Override
			public void yes () 
			{
				saving_Parallax_Project(parallaxPath.getText(), parallaxName.getText());
				Utils_TextureAtlas.flattenProject(parallaxPath.getText(), parallaxName.getText()) ;
				
			}

			@Override
			public void no () 
			{
				Utils_TextureAtlas.flattenProject(parallaxPath.getText(), parallaxName.getText()) ; 
			}

			@Override
			public void cancel () 
			{}
		});	
	}

	public static void saving_Parallax_Kryo(String where, String whatName, WholePage_Model outputFinalModel) throws FileNotFoundException
	{
		Output output = new Output(new FileOutputStream(where + "/" + whatName + "." + FVars_Extensions.PARALLAX));
		GVars_Serialization.kryo.writeObject(output, outputFinalModel);
    	output.close();
	}
	
	public static void saving_Parallax_JSON(String where, String whatName, WholePage_Model outputFinalModel) throws JsonGenerationException, JsonMappingException, IOException
	{
		objectMapper.writeValue(new File(where + "/" + whatName + "." + FVars_Extensions.JSON_PARALLAX), outputFinalModel); 
	}
	
	public static void saving_Parallax_Project(String where, String whatName)
	{
		try 
		{
			WholePage_Editor outputFinalModel = buildWholePageAsProjectForSaving() ; 
			projectDatas.prepareForSaving(outputFinalModel);
			objectMapper.writeValue(new File(where + "/" + whatName + "." + FVars_Extensions.PARALLAX_PROJECT), projectDatas); 
		}
		catch(Exception e)
		{e.printStackTrace();}
	}

	
	public static WholePage_Model buildWholePageForExport(ArrayList<ParallaxLayer> parallaxs)
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
		System.out.println("Setting output atlasName at " + outputModel.atlasName);
		
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
		
		outputFinalModel.repeatOnX = parallax_Heart.currentPage.repeatOnX ;
		outputFinalModel.repeatOnY = parallax_Heart.currentPage.repeatOnY ;
		
		return outputFinalModel ; 	
	}
	
	public static WholePage_Editor buildWholePageAsProjectForSaving()
	{
		ArrayList<ParallaxLayer> parallaxs = parallax_Heart.parallaxReader.layers ; 
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
		outputFinalModel.repeatOnX = parallax_Heart.parallaxReader.isRepeatOnX(); 
		outputFinalModel.repeatOnY = parallax_Heart.parallaxReader.isRepeatOnY(); 
		
		return outputFinalModel ; 
	}
	
	public static void autoSave() 
	{
		try 
		{
			String filePath = new File("").getAbsolutePath();
			String relativePath = filePath + "/Files/AutoSave" ;
			DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
			Date date = new Date();
			String saveName = "AUTO_" + VE_Options.parallaxName.getText() + "_" + dateFormat.format(date) ; 
			saving_Parallax_Project(relativePath, saveName) ; 
		}
		catch(Exception e)
		{e.printStackTrace();}
	}
}