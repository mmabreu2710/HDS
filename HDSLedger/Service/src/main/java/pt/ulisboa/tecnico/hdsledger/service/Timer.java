package pt.ulisboa.tecnico.hdsledger.service;

import java.util.concurrent.*;

public class Timer {
    private ScheduledExecutorService scheduler;
    private final int baseDelay; // in milliseconds
    private final double exponentBase;
    private int roundNumber;
    private Runnable onExpireCallback;
    private ScheduledFuture<?> futureTask;

    public Timer(int baseDelay, double exponentBase) {
        this.baseDelay = baseDelay;
        this.exponentBase = exponentBase;
        this.scheduler = Executors.newScheduledThreadPool(1);
    }

    public void start(int roundNumber, Runnable onExpireCallback) {
        if ((scheduler == null) || scheduler.isShutdown()) {
            this.scheduler = Executors.newScheduledThreadPool(1);
        }
        this.roundNumber = roundNumber;
        this.onExpireCallback = onExpireCallback;
        reschedule();
    }
    

    private void reschedule() {
        if (futureTask != null && !futureTask.isDone()) {
            futureTask.cancel(false); // Do not interrupt if running
        }
        long delay = (long) (baseDelay * Math.pow(exponentBase, roundNumber));
        futureTask = scheduler.schedule(onExpireCallback, delay, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (futureTask != null) {
            futureTask.cancel(false); // Attempt to cancel the current task
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown(); // Shut down the scheduler
            try {
                // Optional: Wait a while for existing tasks to terminate
                if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow(); // Cancel currently executing tasks
                    // Optional: Wait a while for tasks to respond to being cancelled
                    if (!scheduler.awaitTermination(60, TimeUnit.SECONDS))
                        System.err.println("Scheduler did not terminate");
                }
            } catch (InterruptedException ie) {
                // (Re-)Cancel if current thread also interrupted
                scheduler.shutdownNow();
                // Preserve interrupt status
                Thread.currentThread().interrupt();
            }
        }
    }
    
}

