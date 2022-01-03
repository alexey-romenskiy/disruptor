package codes.writeonce.disruptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.concurrent.ThreadFactory;

public final class DisruptorThread {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Nonnull
    private final Disruptor disruptor;

    @Nonnull
    private final Thread thread;

    private Worker worker;

    DisruptorThread(@Nonnull Disruptor disruptor, @Nonnull ThreadFactory threadFactory) {
        this.disruptor = disruptor;
        this.thread = threadFactory.newThread(this::run);
    }

    void start(@Nonnull Worker worker) {
        this.worker = worker;
        thread.start();
    }

    @Nonnull
    public Thread getThread() {
        return thread;
    }

    private void run() {
        try {
            worker.run();
            logger.info("Disruptor thread completed: {}", Thread.currentThread().getName());
        } catch (Throwable e) {
            disruptor.terminate();
            logger.error("Disruptor thread failed: {}", Thread.currentThread().getName(), e);
        }
    }
}
