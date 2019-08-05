package jks.tools2d.parallax.editor.vue.edition.utils;

import static jks.tools2d.parallax.editor.gvars.FVars_Extensions.atlasMaxSize;
import static jks.tools2d.parallax.editor.vue.edition.data.GVars_Vue_Edition.allImage;
import static jks.tools2d.parallax.editor.vue.edition.data.GVars_Vue_Edition.imageRef;
import static jks.tools2d.parallax.editor.vue.edition.data.GVars_Vue_Edition.projectDatas;

import java.util.ArrayList;

import org.apache.commons.lang3.StringUtils;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.kotcrab.vis.ui.util.dialog.Dialogs;

import jks.tools2d.filewatch.FileWatching_Image;
import jks.tools2d.libgdxutils.Utils_Scene2D;
import jks.tools2d.parallax.editor.gvars.GVars_Ui;
import jks.tools2d.parallax.editor.vue.edition.data.GVars_Vue_Edition;
import jks.tools2d.parallax.editor.vue.edition.data.Outside_Source;
import jks.tools2d.parallax.editor.vue.edition.data.Position_Infos;

public class Utils_LoadingImages 
{
	
	// Todo Check pour des fichiers au m�me nom

	public static void fileReception(String[] files)
	{
		TextureRegion textureRegion  ; 
		String errorMessage = "";
		
		if(projectDatas.outsideInfos == null)
		{
			projectDatas.outsideInfos = new ArrayList<Outside_Source>() ; 
		}
		
		try 
		{
			for(String path : files)
			{
				if("png".equals(Utils_Scene2D.getExtension(path)))
				{
					textureRegion = Utils_Texture.getTextureRegionFromPath(path) ; 
					
					if(textureRegion.getRegionWidth() > atlasMaxSize || textureRegion.getRegionWidth()  > atlasMaxSize)
					{
						errorMessage += "\nWarning ! This file is bigger then the limit : " + atlasMaxSize + " pixels \n" ; 
						continue ; 
					}
					
					new FileWatching_Image(path,textureRegion) ; 
					projectDatas.outsideInfos.add(new Outside_Source(path,extractName(path))) ; 
					imageRef.put(textureRegion, new Position_Infos(false,path,0)) ; 
					allImage.add(textureRegion) ; 				
				}
				else
				{
					errorMessage += "\n Impossible to load " + path + " was expecting the .png format" ; 
				}
			}
			
			if(!StringUtils.isEmpty(errorMessage))
			{
				Dialogs.showErrorDialog(GVars_Ui.mainUi,errorMessage) ; 
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		
		GVars_Vue_Edition.setItems();
	}
	
	public static String extractName(String path)
	{
		return path.substring(path.lastIndexOf('\\') + 1, path.lastIndexOf('.')) ; 
	}
}