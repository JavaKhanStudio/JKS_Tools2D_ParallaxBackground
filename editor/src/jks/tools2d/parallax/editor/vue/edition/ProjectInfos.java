package jks.tools2d.parallax.editor.vue.edition;

import com.badlogic.gdx.files.FileHandle;
import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

@DefaultSerializer(ProjectInfos_Serializer.class)
public class ProjectInfos
{
	public String projectName ; 
	public String projectPath ; 
	
	public ParallaxDefaultValues defaults ;

	public void setPathInfo(FileHandle filehandle)
	{
		projectName = filehandle.name().substring(0,filehandle.name().lastIndexOf(".")) ; 
		projectPath = filehandle.path().substring(0,filehandle.path().lastIndexOf("/")) ; 
	}
	
}

class ProjectInfos_Serializer extends Serializer<ProjectInfos>
{
	@Override
	public void write(Kryo kryo, Output output, ProjectInfos object) 
	{
//		kryo.wr
	}

	@Override
	public ProjectInfos read(Kryo kryo, Input input, Class<? extends ProjectInfos> type) 
	{
		ProjectInfos returning = new ProjectInfos();

		
		return returning;
	}
}