package com.dlp.discovery.policy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PolicyStore {
    
    private static final Logger logger = LoggerFactory.getLogger(PolicyStore.class);
    private static final String DEFAULT_RULES_FILE = "default_rules.json";
    
    private final Path rulesPath;
    private volatile RuleBundle currentRuleBundle;
    private final List<RuleUpdateListener> listeners = new ArrayList<>();
    private WatchService watchService;
    private ExecutorService watchExecutor;
    
    public PolicyStore(Path rulesPath) throws IOException {
        this.rulesPath = rulesPath;
        loadRules();
    }
    
    public void loadRules() throws IOException {
        Path rulesFile = rulesPath.resolve(DEFAULT_RULES_FILE);
        
        RuleBundle newBundle;
        if (Files.exists(rulesFile)) {
            logger.info("Loading rules from file: {}", rulesFile);
            try {
                newBundle = RuleBundle.fromJsonFile(rulesFile);
                logger.info("Successfully loaded {} rules from file (version: {})", 
                           newBundle.getRules().size(), newBundle.getVersion());
            } catch (IOException e) {
                logger.error("Failed to load rules from file: {}", rulesFile, e);
                throw e;
            }
        } else {
            logger.warn("Rules file not found at: {}, loading from classpath", rulesFile);
            newBundle = loadFromClasspath();
        }
        
        RuleBundle oldBundle = currentRuleBundle;
        currentRuleBundle = newBundle;
        
        if (oldBundle != null && !oldBundle.equals(newBundle)) {
            notifyListeners(newBundle);
        }
    }
    
    private RuleBundle loadFromClasspath() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream(DEFAULT_RULES_FILE)) {
            if (inputStream == null) {
                throw new IOException("Could not find " + DEFAULT_RULES_FILE + " in classpath");
            }
            RuleBundle bundle = RuleBundle.fromJson(inputStream);
            logger.info("Successfully loaded {} rules from classpath (version: {})", 
                       bundle.getRules().size(), bundle.getVersion());
            return bundle;
        }
    }
    
    public RuleBundle currentRules() {
        return currentRuleBundle;
    }
    
    public void watchForUpdates() throws IOException {
        if (watchService != null) {
            logger.warn("Watch service already started");
            return;
        }
        
        if (!Files.exists(rulesPath)) {
            logger.warn("Rules directory does not exist: {}, cannot watch for updates", rulesPath);
            return;
        }
        
        watchService = FileSystems.getDefault().newWatchService();
        rulesPath.register(watchService, 
                          StandardWatchEventKinds.ENTRY_CREATE,
                          StandardWatchEventKinds.ENTRY_MODIFY);
        
        watchExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "PolicyStore-Watcher");
            thread.setDaemon(true);
            return thread;
        });
        
        watchExecutor.submit(() -> {
            logger.info("Started watching directory for rule updates: {}", rulesPath);
            
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    WatchKey key = watchService.take();
                    
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        
                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            continue;
                        }
                        
                        @SuppressWarnings("unchecked")
                        WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                        Path fileName = pathEvent.context();
                        
                        if (fileName.toString().equals(DEFAULT_RULES_FILE)) {
                            logger.info("Detected change in rules file: {}", fileName);
                            try {
                                Thread.sleep(100);
                                loadRules();
                            } catch (IOException e) {
                                logger.error("Failed to reload rules after file change", e);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                    
                    boolean valid = key.reset();
                    if (!valid) {
                        logger.warn("Watch key no longer valid, stopping watch service");
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.info("Watch service interrupted, stopping");
                    break;
                } catch (Exception e) {
                    logger.error("Error in watch service", e);
                }
            }
            
            logger.info("Stopped watching directory for rule updates");
        });
    }
    
    public void addListener(RuleUpdateListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }
    
    public void removeListener(RuleUpdateListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }
    
    private void notifyListeners(RuleBundle newBundle) {
        List<RuleUpdateListener> listenersCopy;
        synchronized (listeners) {
            listenersCopy = new ArrayList<>(listeners);
        }
        
        for (RuleUpdateListener listener : listenersCopy) {
            try {
                listener.onRulesUpdated(newBundle);
            } catch (Exception e) {
                logger.error("Error notifying listener about rule update", e);
            }
        }
    }
    
    public void close() {
        if (watchExecutor != null) {
            watchExecutor.shutdownNow();
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                logger.error("Error closing watch service", e);
            }
        }
    }
    
    @FunctionalInterface
    public interface RuleUpdateListener {
        void onRulesUpdated(RuleBundle newBundle);
    }
}
