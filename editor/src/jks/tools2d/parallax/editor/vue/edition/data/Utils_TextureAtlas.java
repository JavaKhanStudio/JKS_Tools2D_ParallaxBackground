package jks.tools2d.parallax.editor.vue.edition.data;

import static jks.tools2d.parallax.editor.vue.edition.Vue_Edition.parallax_Heart;
import static jks.tools2d.parallax.editor.vue.edition.data.GVars_Vue_Edition.projectDatas;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.commons.lang3.StringUtils;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Pixmap.Filter;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.PixmapPackerIO;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import static jks.tools2d.parallax.editor.gvars.FVars_Extensions.atlasMaxSize ; 

import jks.tools2d.parallax.ParallaxLayer;

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
			info = GVars_Vue_Edition.imageRef.get(layer.getTexRegion()) ; 
			pageName = info.getPageName() ;
			
			
			if(currentValue.contains(pageName))
				continue ;
			else
				currentValue.add(pageName) ; 
			
		
			Pixmap pixels = extractRegion(layer.getTexRegion()) ;
	
			pixmap.pack(pageName,pixels) ;
			info.url = pageName ; 
			info.fromAtlas = true ; 
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
		System.out.println("seetting atlas path at " + atlasName);
	}
	
	static final int paddingSize = 10 ; 

	public static PixmapPacker findAtlasRequiredSize(ArrayList<ParallaxLayer> parrallaxLayers)
	{
		int largestWidth = 0 ; 
		int largestHeight = 0 ; 
		
		for(ParallaxLayer layer: parrallaxLayers)
		{
			if(layer.getTexRegion().getRegionWidth() > largestWidth)  
				largestWidth = layer.getTexRegion().getRegionWidth() ; 
			
			if(layer.getTexRegion().getRegionHeight() > largestHeight)  
				largestHeight = layer.getTexRegion().getRegionHeight() ; 
		}
		
		largestWidth *= 3 ; 
		largestHeight *= 3 ; 
		
		
		if(largestWidth > atlasMaxSize)
			largestWidth = atlasMaxSize ; 
		
		if(largestHeight > atlasMaxSize)
			largestHeight = atlasMaxSize ; 
		
		PixmapPacker pixmap = new PixmapPacker(largestWidth, largestHeight, Format.RGBA8888, paddingSize, false); 
		return pixmap ; 
		
	}
	
	public static Texture textureSave= null ; 
	public static Pixmap pixmapSave = null ; 
	
	public static Pixmap extractRegion(TextureRegion textureRegion)
	{
		Texture texture = textureRegion.getTexture();
		
		if(textureSave == null || textureSave != texture)
		{
			textureSave = texture ; 
			if (!texture.getTextureData().isPrepared()) 
			    texture.getTextureData().prepare();
			
			pixmapSave = texture.getTextureData().consumePixmap();
		}
		
		Pixmap exportingPixmap = new Pixmap(textureRegion.getRegionWidth(), textureRegion.getRegionHeight(), Format.RGBA8888); 
		exportingPixmap.setFilter(Filter.BiLinear);
		exportingPixmap.drawPixmap(pixmapSave, textureRegion.getRegionX(), textureRegion.getRegionY(), textureRegion.getRegionWidth(), textureRegion.getRegionHeight(), 0, 0, textureRegion.getRegionWidth(), textureRegion.getRegionHeight());
		
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
				newFilePath = filePath.substring(0, lastIndex - currentValue[1]) + currentValue[0] + ".atlas" ; 		
			}
			else
			{
				newFilePath = filePath.substring(0, lastIndex) + ".atlas" ; 
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
		if(StringUtils.isNumeric(lookingAt.substring(from - at, from)))
			return new int[] {Integer.parseInt(lookingAt.substring(from - at - 1, from)),from} ; 
		else
			return recurciveNumber(lookingAt,from,at + 1) ; 
	}
	
}