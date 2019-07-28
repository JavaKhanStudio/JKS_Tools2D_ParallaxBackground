package jks.tools2d.filewatch;

import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.WatchEvent.Kind;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public abstract class FileWatchingModel extends TimerTask
{

	private Path folderPath;
    private String watchFile;
	
    public abstract void onModified(Kind<?> kind);
    
	public FileWatchingModel(String watchFile)
    {
        Path filePath = Paths.get(watchFile);

        boolean isRegularFile = Files.isRegularFile(filePath);

        if (!isRegularFile)
        {throw new IllegalArgumentException(watchFile + " is not a regular file");}

        folderPath = filePath.getParent();
        this.watchFile = watchFile.substring(watchFile.lastIndexOf("\\") + 1) ; 
    }
	
	public void watchFile() throws Exception
    {
    	FileSystem fileSystem ; 
        
    	try
        {fileSystem = folderPath.getFileSystem();}
        catch(Exception e)
        {System.out.println("Hick file watch"); return ; }
    	
        // We create the new WatchService using the try-with-resources block
        try (WatchService service = fileSystem.newWatchService())
        {
            // We watch for modification events
            folderPath.register(service, ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
            WatchKey watchKey = service.take();

            for (WatchEvent<?> watchEvent : watchKey.pollEvents())
            {
            	Path watchEventPath = (Path) watchEvent.context();
            	if (watchEventPath.toString().equals(watchFile))
                	onModified(watchEvent.kind());       
            }
        }
    }
	
	@Override
	public void run() 
	{
		try 
    	{watchFile() ;} 
    	catch (Exception e) 
    	{e.printStackTrace() ;} 
	}

}
