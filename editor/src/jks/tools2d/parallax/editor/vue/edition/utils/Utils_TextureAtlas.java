package jks.tools2d.parallax.editor.vue.edition.utils;

import static jks.tools2d.parallax.editor.gvars.FVars_Extensions.atlasMaxSize ;
import static jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition.projectDatas;
import static jks.tools2d.parallax.editor.vue.Vue_Edition.parallax_Heart;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.commons.lang3.StringUtils;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Blending;
import com.badlogic.gdx.graphics.Pixmap.Filter;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.editor.gvars.GVars_Vue_Edition;
import jks.tools2d.parallax.editor.vue.edition.data.Position_Infos;
import jks.tools2d.parallax.editor.vue.edition.pixmap.PixmapPacker;
import jks.tools2d.parallax.editor.vue.edition.pixmap.PixmapPackerIO;

public class Utils_TextureAtlas 
{
	
	public static void flattenProject(String path,String name)
	{			
		FileHandle atlasPath = getFileHandle(path + "/" + name + ".atlas"); 	
		String atlasName = atlasPath.name() ; 
			
		ArrayList<ParallaxLayer> parrallaxLayers = parallax_Heart.parallaxReader.layers ; 
		PixmapPacker pixmap = findAtlasRequiredSize(parrallaxLayers) ; 
	
		Position_Infos info ;
		
		ArrayList<String> currentValue = new ArrayList<>() ; 
		String pageName ; 
		
		for(ParallaxLayer layer: parrallaxLayers)
		{
			for(TextureRegion region : layer.getTexRegion())
			{			
				info = GVars_Vue_Edition.imageRef.get(region) ; 
				pageName = info.getPageName() ;
				
				if(currentValue.contains(pageName))
					continue ;
				else
					currentValue.add(pageName) ; 
			
				Pixmap pixels = extractRegion(region) ;
				
				pixmap.pack(pageName,pixels) ;
				info.url = pageName ; 
				info.fromAtlas = true ; 
			}
		
		}

		try 
		{
			new PixmapPackerIO().save(atlasPath, pixmap);
		} 
		catch (IOException e) 
		{e.printStackTrace();}
		
		if(projectDatas.outsideInfos != null)
			projectDatas.outsideInfos.clear();
		
		parallax_Heart.currentPage.pageModel.atlasName =  atlasName ; 
	}
	
	static final int paddingSize = 50 ; 

	public static PixmapPacker findAtlasRequiredSize(ArrayList<ParallaxLayer> parrallaxLayers)
	{
		int largestWidth = 0 ; 
		int largestHeight = 0 ; 
		
		for(ParallaxLayer layer: parrallaxLayers)
		{
			if(layer.getTexRegion().get(0).getRegionWidth() > largestWidth)  
				largestWidth = layer.getTexRegion().get(0).getRegionWidth() ; 
			
			if(layer.getTexRegion().get(0).getRegionHeight() > largestHeight)  
				largestHeight = layer.getTexRegion().get(0).getRegionHeight() ; 
		}
		
		largestWidth *= 3 ; 
		largestHeight *= 3 ; 
		
		if(largestWidth > atlasMaxSize)
			largestWidth = atlasMaxSize ; 
		
		if(largestHeight > atlasMaxSize)
			largestHeight = atlasMaxSize ; 
		
		PixmapPacker pixmap = new PixmapPacker(largestWidth, largestHeight, 
				Format.RGBA8888, paddingSize, true); 
		
		return pixmap ; 	
	}
	
	public static Texture atlasTextureSave= null ; 
	public static Pixmap atlasPixmapSave = null ; 
	
		
	public static Pixmap extractRegion(TextureRegion textureRegion)
	{
		Texture texture = textureRegion.getTexture();
		
		if(atlasTextureSave == null || atlasTextureSave != texture)
		{
			atlasTextureSave = texture ; 
			if (!texture.getTextureData().isPrepared()) 
			    texture.getTextureData().prepare();
			
			atlasTextureSave.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
			
			atlasPixmapSave = atlasTextureSave.getTextureData().consumePixmap();
			atlasPixmapSave.setFilter(Pixmap.Filter.NearestNeighbour);	
			atlasPixmapSave.setBlending(Blending.None);
		}
		
		Pixmap exportingPixmap = new Pixmap(textureRegion.getRegionWidth(), textureRegion.getRegionHeight(), Format.RGBA8888); 
		exportingPixmap.setFilter(Filter.NearestNeighbour);
		exportingPixmap.setBlending(Blending.None);
		exportingPixmap.drawPixmap(atlasPixmapSave, textureRegion.getRegionX(), textureRegion.getRegionY(), textureRegion.getRegionWidth(), textureRegion.getRegionHeight(), 0, 0, textureRegion.getRegionWidth(), textureRegion.getRegionHeight());
		
		return exportingPixmap ; 
	}
	
	public static FileHandle getFileHandle(String filePath)
	{
		FileHandle atlasPath = new FileHandle(filePath); 	
		if(atlasPath.exists()) 
		{
			int lastIndex = filePath.lastIndexOf(".atlas") ; 
			String newFilePath ; 
			if(StringUtils.isNumeric(filePath.substring(lastIndex - 1, lastIndex)))
			{
				int[] currentValue = recurciveNumber(filePath,lastIndex,1) ; 
				newFilePath = filePath.substring(0, lastIndex - currentValue[1]) + (currentValue[0] + 1) + ".atlas" ; 		
			}
			else
			{
				newFilePath = filePath.substring(0, lastIndex) + 1 + ".atlas" ; 
			}
			
			return new FileHandle(newFilePath);
		}
		else
		{
			return atlasPath ; 
		}
	}
	
	public static int[] recurciveNumber(String lookingAt, int from, int at)
	{
		if(!StringUtils.isNumeric(lookingAt.substring(from - at, from)))
			return new int[] {Integer.parseInt(lookingAt.substring(from - at + 1, from)),from} ; 
		else
			return recurciveNumber(lookingAt,from,at - 1) ; 
	}
	
	public static void changeAtlas(TextureAtlas atlas) 
	{
		GVars_Vue_Edition.atlas = atlas ; 
		GVars_Vue_Edition.allImage.clear(); 
	}
	
}