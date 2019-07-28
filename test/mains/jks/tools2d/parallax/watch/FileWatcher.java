package jks.tools2d.parallax.watch;

import java.io.File;
import java.nio.file.*;
import java.nio.file.WatchEvent.Kind;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import static java.nio.file.StandardWatchEventKinds.*;

public abstract class FileWatcher extends TimerTask
{
	
	static public void main (String[] args) throws Exception
    {
		FileWatcher watcher = new FileWatcher("C:/Users/Simon/Documents/JKS_Tools2D_ParallaxBackground/test/desktop/Saving.txt")
		{
			@Override
			public void onModified(Kind<?> kind) 
			{
				System.out.println("india");
				System.out.println(kind);
			}
			
		}; 
    }
	
	
    private Path folderPath;
    private String watchFile;

    public FileWatcher(String watchFile)
    {
        Path filePath = Paths.get(watchFile);

        boolean isRegularFile = Files.isRegularFile(filePath);

        if (!isRegularFile)
        {
            // Do not allow this to be a folder since we want to watch files
            throw new IllegalArgumentException(watchFile + " is not a regular file");
        }

        folderPath = filePath.getParent();
        this.watchFile = watchFile.substring(watchFile.lastIndexOf("/") + 1) ; 
        
        Timer timer = new Timer();
        timer.schedule(this , new Date(), 1000 );
        
       
//        this.watchFile = watchFile.replace(folderPath.toString() + File.separator, "");
    }
    
    @Override
    public void run() {
    	try 
    	{
			watchFile() ;
		} 
    	catch (Exception e) 
    	{e.printStackTrace();} 
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

    public abstract void onModified(Kind<?> kind);
}