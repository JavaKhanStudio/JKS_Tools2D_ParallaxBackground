package jks.tools2d.parallax.editor.vue.edition.data;

import com.badlogic.gdx.files.FileHandle;
import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

@DefaultSerializer(ProjectInfos_Serializer.class)
public class Project_Infos
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

class ProjectInfos_Serializer extends Serializer<Project_Infos>
{
	@Override
	public void write(Kryo kryo, Output output, Project_Infos object) 
	{
//		kryo.wr
	}

	@Override
	public Project_Infos read(Kryo kryo, Input input, Class<? extends Project_Infos> type) 
	{
		Project_Infos returning = new Project_Infos();

		
		return returning;
	}
}