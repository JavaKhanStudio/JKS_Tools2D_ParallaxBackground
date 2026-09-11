package jks.tools2d.filewatch;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import com.badlogic.gdx.Gdx;

/**
 * Watches image files for changes with a single daemon thread and runs the callbacks on the render thread.
 * Events are debounced because image editors usually write a file in several steps.
 */
public final class FileWatchService
{
	private static final long DEBOUNCE_MS = 300;

	private static WatchService watchService;
	private static Thread thread;

	private static final Map<Path, WatchKey> watchedDirectories = new HashMap<>();
	private static final Map<Path, List<Runnable>> listeners = new HashMap<>();
	private static final Map<Path, Long> pendingChanges = new HashMap<>();

	private FileWatchService()
	{}

	/** Calls {@code onChange} on the render thread each time {@code file} is modified. */
	public static synchronized Watch watch(Path file, Runnable onChange)
	{
		Path absolute = file.toAbsolutePath().normalize();
		try
		{
			start();
			Path directory = absolute.getParent();
			if (!watchedDirectories.containsKey(directory))
				watchedDirectories.put(directory, directory.register(watchService, ENTRY_MODIFY, ENTRY_CREATE));
		}
		catch (IOException | RuntimeException e)
		{
			Gdx.app.error("FileWatchService", "Cannot watch " + absolute, e);
			return () -> {};
		}

		listeners.computeIfAbsent(absolute, k -> new CopyOnWriteArrayList<>()).add(onChange);
		return () -> unwatch(absolute, onChange);
	}

	private static synchronized void unwatch(Path file, Runnable onChange)
	{
		List<Runnable> fileListeners = listeners.get(file);
		if (fileListeners == null)
			return;

		fileListeners.remove(onChange);
		if (fileListeners.isEmpty())
			listeners.remove(file);
	}

	public static synchronized void shutdown()
	{
		if (watchService == null)
			return;

		try
		{watchService.close();}
		catch (IOException ignored)
		{}

		thread.interrupt();
		watchService = null;
		thread = null;
		watchedDirectories.clear();
		listeners.clear();
		pendingChanges.clear();
	}

	private static void start() throws IOException
	{
		if (watchService != null)
			return;

		watchService = FileSystems.getDefault().newWatchService();
		thread = new Thread(FileWatchService::run, "parallax-file-watch");
		thread.setDaemon(true);
		thread.start();
	}

	private static void run()
	{
		WatchService service = watchService;
		try
		{
			while (!Thread.currentThread().isInterrupted())
			{
				WatchKey key = service.poll(100, TimeUnit.MILLISECONDS);
				if (key != null)
				{
					Path directory = (Path) key.watchable();
					for (WatchEvent<?> event : key.pollEvents())
						if (event.context() instanceof Path)
							markChanged(directory.resolve((Path) event.context()));
					key.reset();
				}
				dispatchSettledChanges();
			}
		}
		catch (InterruptedException | ClosedWatchServiceException e)
		{
			// shutdown
		}
	}

	private static synchronized void markChanged(Path file)
	{
		if (listeners.containsKey(file))
			pendingChanges.put(file, System.currentTimeMillis());
	}

	private static synchronized void dispatchSettledChanges()
	{
		long now = System.currentTimeMillis();
		pendingChanges.entrySet().removeIf(change ->
		{
			if (now - change.getValue() < DEBOUNCE_MS)
				return false;

			List<Runnable> fileListeners = listeners.get(change.getKey());
			if (fileListeners != null)
				for (Runnable listener : fileListeners)
					Gdx.app.postRunnable(listener);
			return true;
		});
	}

	/** Handle returned by {@link #watch}, used to stop watching. */
	public interface Watch
	{
		void cancel();
	}
}
