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
			public void onModified() 
			{
				System.out.println("india");	
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

        Timer timer = new Timer();
        timer.schedule(this , new Date(), 1000 );
        
        folderPath = filePath.getParent();

        this.watchFile = watchFile.replace(folderPath.toString() + File.separator, "");
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
        // We obtain the file system of the Path
        FileSystem fileSystem = folderPath.getFileSystem();

        // We create the new WatchService using the try-with-resources block
        try (WatchService service = fileSystem.newWatchService())
        {
            // We watch for modification events
            folderPath.register(service, ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
            // Start the infinite polling loop
            // Wait for the next event
                WatchKey watchKey = service.take();

                for (WatchEvent<?> watchEvent : watchKey.pollEvents())
                {
                    // Get the type of the event
                    Kind<?> kind = watchEvent.kind();
                    System.out.println(kind);
                    if (kind == ENTRY_MODIFY)
                    {
                        Path watchEventPath = (Path) watchEvent.context();
                        // Call this if the right file is involved
                        if (watchEventPath.toString().equals(watchFile))
                        {
                            onModified();
                        }
                    }
                }
        }
    }

    public abstract void onModified();
}