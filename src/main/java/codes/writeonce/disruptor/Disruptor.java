package codes.writeonce.disruptor;

import codes.writeonce.concurrency.WaitableHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class Disruptor implements AutoCloseable {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ArrayList<AtomicReference<WaitClient>> waitLists = new ArrayList<>();

    private final ArrayList<WaitableHost> waitableHosts = new ArrayList<>();

    @Nonnull
    private final ArrayList<WorkerInfo> workers = new ArrayList<>();

    @Nonnull
    private final List<Connector> connectors = new ArrayList<>();

    @Nonnull
    private final AtomicBoolean starting = new AtomicBoolean();

    @Nonnull
    private final AtomicBoolean started = new AtomicBoolean();

    @Nonnull
    private final AtomicBoolean closing = new AtomicBoolean();

    @Nonnull
    private final AtomicBoolean terminating = new AtomicBoolean();

    @Nonnull
    private final CompletableFuture<Void> closedFuture = new CompletableFuture<>();

    @Nonnull
    private final CompletableFuture<Void> terminateFuture = new CompletableFuture<>();

    private volatile boolean terminate;

    public void addWaitList(@Nonnull AtomicReference<WaitClient> waitListHead) {
        if (starting.get()) {
            throw new IllegalStateException();
        }
        waitLists.add(waitListHead);
    }

    public void addWaitableHost(@Nonnull WaitableHost waitableHost) {
        if (starting.get()) {
            throw new IllegalStateException();
        }
        waitableHosts.add(waitableHost);
    }

    @Nonnull
    public <R> RingBuffer<R> newRingBuffer(int capacityBits, @Nonnull RingBufferEntryFactory<R> entryFactory) {
        if (starting.get()) {
            throw new IllegalStateException();
        }
        return new RingBuffer<>(this, capacityBits, entryFactory);
    }

    @Nonnull
    public DisruptorThread newThread(@Nonnull ThreadFactory threadFactory) {
        if (starting.get()) {
            throw new IllegalStateException();
        }
        return new DisruptorThread(this, threadFactory);
    }

    public void addWorker(@Nonnull DisruptorThread thread, @Nonnull Worker worker) {
        if (starting.get()) {
            throw new IllegalStateException();
        }
        workers.add(new WorkerInfo(thread, worker));
    }

    public void addConnector(@Nonnull Connector connector) {
        if (starting.get()) {
            throw new IllegalStateException();
        }
        connectors.add(connector);
    }

    public boolean isTerminate() {
        return terminate;
    }

    public void start() {

        if (starting.getAndSet(true)) {
            throw new IllegalStateException();
        }

        for (final var workerInfo : workers) {
            workerInfo.start();
        }

        started.set(true);
    }

    /**
     * It sends ShutdownEvent. This event MUST be the last processed by every handler. Further events processing
     * MUST NOT generate any new events nor generate any other changes or outgoing messages. Only event persister is
     * allowed to persist events after that point. Those events hence are guaranteed to be generated NOT AFTER
     * ShutdownEvent.
     * Once ShutdownEvent is processed by all handlers, the main worker will drain the accumulated unprocessed events,
     * so the event persister could store them.
     * Once ring buffer become empty, no more events exists for persisting, so we terminate all threads and next
     * initiate shutdown for all connectors.
     */
    @Override
    public void close() throws Exception {

        if (closing.getAndSet(true)) {
            closedFuture.get();
            return;
        }

        if (started.get()) {
            logger.info("Waiting for graceful termination");
            terminateFuture.get();
        } else {
            terminate();
        }

        logger.info("Waiting for threads cleanup");
        for (final var workerInfo : workers) {
            workerInfo.thread.getThread().join();
        }

        logger.info("Initiating server Shutdown");

        if (!connectors.isEmpty()) {
            final var remained = new CountDownLatch(connectors.size());
            for (final var c : connectors) {
                logger.info("Stopping {}", c);
                c.shutdown().whenComplete((v, e) -> {
                    if (e == null) {
                        logger.info("Stopped {}", c);
                    } else {
                        logger.error("Failed to stop {}", c, e);
                    }
                    remained.countDown();
                });
            }
            remained.await();
        }

        logger.info("Application closed");
        closedFuture.complete(null);
    }

    public void terminate() {

        if (terminating.getAndSet(true)) {
            return;
        }

        logger.info("Application termination");

        terminate = true;

        for (final var waitList : waitLists) {
            WaitClient.wakeupAll(waitList);
        }

        for (final var waitableHost : waitableHosts) {
            waitableHost.wakeup();
        }

        terminateFuture.complete(null);
    }

    @Nonnull
    public CompletableFuture<Void> getTerminateFuture() {
        return terminateFuture;
    }

    private static class WorkerInfo {

        @Nonnull
        private final DisruptorThread thread;

        @Nonnull
        private final Worker worker;

        public WorkerInfo(@Nonnull DisruptorThread thread, @Nonnull Worker worker) {
            this.thread = thread;
            this.worker = worker;
        }

        public void start() {
            thread.start(worker);
        }
    }
}
