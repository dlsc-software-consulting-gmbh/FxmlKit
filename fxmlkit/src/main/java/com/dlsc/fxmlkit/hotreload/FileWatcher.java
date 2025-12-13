package com.dlsc.fxmlkit.hotreload;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simplified file watcher for hot reload functionality.
 *
 * <p>Features:
 * <ul>
 *   <li><b>Per-file monitoring</b> - Only monitors files you care about, not entire directory trees</li>
 *   <li><b>Built-in debouncing</b> - 200ms debounce to handle editor save quirks</li>
 *   <li><b>macOS optimization</b> - Uses HIGH sensitivity modifier for faster detection</li>
 *   <li><b>Flexible ordering</b> - {@link #watch(Path, Runnable)} can be called before or after {@link #start()}</li>
 *   <li><b>Thread-safe</b> - All operations are safe to call from any thread</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * FileWatcher watcher = new FileWatcher();
 *
 * // Register files to watch (can be before start())
 * watcher.watch(sourcePath, () -> {
 *     System.out.println("File changed: " + sourcePath);
 * });
 *
 * // Start the watcher
 * watcher.start();
 *
 * // Later: stop watching
 * watcher.stop();
 * }</pre>
 *
 * <h2>Debouncing</h2>
 * <p>Many editors write files in multiple steps (write temp file, then rename).
 * The 200ms debounce window ensures callbacks fire only once per logical save.
 *
 * <h2>macOS Note</h2>
 * <p>On macOS with Java 11~17.0.3, the default WatchService polling interval is
 * 10 seconds. This class uses the {@code SensitivityWatchEventModifier.HIGH}
 * modifier (via reflection) to reduce it to ~2 seconds. Java 17.0.4+ already
 * uses 2-second polling by default (JDK-8285956).
 *
 * @see HotReloadManager
 */
public final class FileWatcher {

    private static final Logger logger = Logger.getLogger(FileWatcher.class.getName());

    /**
     * Debounce window in milliseconds.
     * Allows files to stabilize after editors write them.
     */
    private static final long DEBOUNCE_MILLIS = 200;

    // ==================== WatchService Components ====================

    private WatchService watchService;
    private Thread watchThread;
    private ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    // ==================== State Maps ====================

    /**
     * Maps directories to their WatchKeys.
     */
    private final Map<Path, WatchKey> watchedDirs = new ConcurrentHashMap<>();

    /**
     * Maps file paths to their callbacks.
     */
    private final Map<Path, List<Runnable>> fileCallbacks = new ConcurrentHashMap<>();

    /**
     * Maps file paths to pending debounced tasks.
     */
    private final Map<Path, ScheduledFuture<?>> pendingTasks = new ConcurrentHashMap<>();

    /**
     * Directories registered before start() was called.
     * These are registered when start() is invoked.
     */
    private final Set<Path> pendingDirs = ConcurrentHashMap.newKeySet();

    // ==================== macOS Optimization ====================

    /**
     * HIGH sensitivity modifier for faster file detection on macOS.
     */
    private static final WatchEvent.Modifier[] HIGH_SENSITIVITY = initHighSensitivity();

    public FileWatcher() {
        // Empty constructor
    }

    // ==================== Public API ====================

    /**
     * Registers a file for change monitoring.
     *
     * <p>Can be called before or after {@link #start()}. If called before start(),
     * the file's directory will be registered when start() is called.
     *
     * <p>Multiple callbacks can be registered for the same file.
     *
     * @param sourceFile the file to monitor (must not be null)
     * @param callback   the callback to invoke when the file changes (must not be null)
     */
    public void watch(Path sourceFile, Runnable callback) {
        if (sourceFile == null || callback == null) {
            return;
        }

        Path dir = sourceFile.getParent();
        Path normalizedFile = sourceFile.toAbsolutePath().normalize();

        // Register callback
        fileCallbacks.computeIfAbsent(normalizedFile, k -> new CopyOnWriteArrayList<>())
                .add(callback);

        // Ensure directory is watched
        ensureDirectoryWatched(dir);

        logger.log(Level.FINE, "Watching: {0}", normalizedFile);
    }

    /**
     * Removes a callback for a file.
     *
     * <p>If this was the last callback for the file, the file will no longer
     * trigger any notifications (but the directory may still be watched).
     *
     * @param sourceFile the file to stop watching
     * @param callback   the specific callback to remove
     */
    public void unwatch(Path sourceFile, Runnable callback) {
        if (sourceFile == null) {
            return;
        }

        Path normalizedFile = sourceFile.toAbsolutePath().normalize();
        List<Runnable> callbacks = fileCallbacks.get(normalizedFile);
        if (callbacks != null) {
            callbacks.remove(callback);
        }
    }

    /**
     * Starts the file watcher.
     *
     * <p>This method:
     * <ul>
     *   <li>Creates the WatchService</li>
     *   <li>Registers any directories that were queued before start()</li>
     *   <li>Starts the background watch thread</li>
     * </ul>
     *
     * <p>Calling start() when already running has no effect.
     */
    public synchronized void start() {
        if (running) {
            return;
        }

        try {
            watchService = FileSystems.getDefault().newWatchService();
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "FxmlKit-debounce");
                t.setDaemon(true);
                return t;
            });

            // Register directories that were queued before start()
            for (Path dir : pendingDirs) {
                registerDirectory(dir);
            }
            pendingDirs.clear();

            running = true;
            watchThread = new Thread(this::watchLoop, "FxmlKit-file-watcher");
            watchThread.setDaemon(true);
            watchThread.start();

            logger.log(Level.INFO, "FileWatcher started");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to start FileWatcher", e);
        }
    }

    /**
     * Stops the file watcher and releases all resources.
     *
     * <p>After calling stop(), you can call start() again to restart.
     */
    public synchronized void stop() {
        running = false;

        if (watchThread != null) {
            watchThread.interrupt();
            watchThread = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {
            }
            watchService = null;
        }

        watchedDirs.clear();
        fileCallbacks.clear();
        pendingTasks.clear();
        pendingDirs.clear();

        logger.log(Level.INFO, "FileWatcher stopped");
    }

    /**
     * Returns whether the watcher is currently running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running;
    }

    // ==================== Internal Methods ====================

    /**
     * Ensures a directory is being watched.
     *
     * <p>If the WatchService hasn't started yet, the directory is queued
     * for registration when start() is called.
     */
    private void ensureDirectoryWatched(Path dir) {
        if (dir == null) {
            return;
        }

        Path normalizedDir = dir.toAbsolutePath().normalize();

        // If not started yet, queue for later
        if (watchService == null) {
            pendingDirs.add(normalizedDir);
            return;
        }

        registerDirectory(normalizedDir);
    }

    /**
     * Registers a directory with the WatchService.
     */
    private void registerDirectory(Path dir) {
        watchedDirs.computeIfAbsent(dir, d -> {
            try {
                WatchKey key = d.register(
                        watchService,
                        new WatchEvent.Kind[]{
                                StandardWatchEventKinds.ENTRY_MODIFY,
                                StandardWatchEventKinds.ENTRY_CREATE
                        },
                        HIGH_SENSITIVITY
                );
                logger.log(Level.FINE, "Watching directory: {0}", d);
                return key;
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to watch directory: {0} - {1}",
                        new Object[]{d, e.getMessage()});
                return null;
            }
        });
    }

    /**
     * Main watch loop - runs on background thread.
     */
    private void watchLoop() {
        while (running) {
            try {
                WatchKey key = watchService.take();
                Path dir = (Path) key.watchable();

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path changedFile = dir.resolve(pathEvent.context()).toAbsolutePath().normalize();

                    scheduleCallback(changedFile);
                }

                // Reset the key to continue receiving events
                boolean valid = key.reset();
                if (!valid) {
                    watchedDirs.remove(dir);
                    logger.log(Level.FINE, "Watch key invalidated for: {0}", dir);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (running) {
                    logger.log(Level.WARNING, "Error in watch loop", e);
                }
            }
        }
    }

    /**
     * Schedules a debounced callback for a changed file.
     */
    private void scheduleCallback(Path changedFile) {
        List<Runnable> callbacks = fileCallbacks.get(changedFile);
        if (callbacks == null || callbacks.isEmpty()) {
            return;
        }

        // Cancel previous pending task (debounce)
        ScheduledFuture<?> previous = pendingTasks.get(changedFile);
        if (previous != null) {
            previous.cancel(false);
        }

        // Schedule new task
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            logger.log(Level.FINE, "File changed: {0}", changedFile);

            for (Runnable callback : callbacks) {
                try {
                    callback.run();
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Callback failed for: " + changedFile, e);
                }
            }

            pendingTasks.remove(changedFile);
        }, DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);

        pendingTasks.put(changedFile, future);
    }

    /**
     * Initializes the HIGH sensitivity modifier via reflection.
     *
     * <p>Uses reflection to avoid compile-time dependency on private API.
     * Gracefully degrades if reflection fails.
     */
    private static WatchEvent.Modifier[] initHighSensitivity() {
        try {
            Class<?> modifierClass = Class.forName("com.sun.nio.file.SensitivityWatchEventModifier");
            for (Object constant : modifierClass.getEnumConstants()) {
                if ("HIGH".equals(((Enum<?>) constant).name())) {
                    logger.log(Level.FINE, "Using HIGH sensitivity WatchService modifier");
                    return new WatchEvent.Modifier[]{(WatchEvent.Modifier) constant};
                }
            }
        } catch (ClassNotFoundException e) {
            logger.log(Level.FINE, "SensitivityWatchEventModifier not available");
        } catch (Exception e) {
            logger.log(Level.FINE, "Failed to init HIGH sensitivity: {0}", e.getMessage());
        }
        return new WatchEvent.Modifier[0];
    }
}