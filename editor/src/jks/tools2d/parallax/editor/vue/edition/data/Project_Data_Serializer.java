//package jks.tools2d.parallax.editor.vue.edition.data;
//
//import java.util.ArrayList;
//
//import com.esotericsoftware.kryo.Kryo;
//import com.esotericsoftware.kryo.Serializer;
//import com.esotericsoftware.kryo.io.Input;
//import com.esotericsoftware.kryo.io.Output;
//
//public class Project_Data_Serializer extends Serializer<Project_Data>
//{
//	@Override
//	public void write(Kryo kryo, Output output, Project_Data object) 
//	{
//		kryo.writeObject(output, object.saving);
//		kryo.writeObject(output, object.outsideInfos);
//		kryo.writeObject(output, object.defaults);
//	}
//
//	@Override
//	public Project_Data read(Kryo kryo, Input input, Class<? extends Project_Data> type) 
//	{
//		Project_Data returning = new Project_Data();
//		
//		returning.saving = kryo.readObject(input, WholePage_Editor.class);
//		returning.outsideInfos = kryo.readObject(input, ArrayList.class);
//		returning.defaults = kryo.readObject(input, ParallaxDefaultValues.class);
//		
//		return returning;
//	}
//}