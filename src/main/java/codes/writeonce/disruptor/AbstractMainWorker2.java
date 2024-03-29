package codes.writeonce.disruptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

public abstract class AbstractMainWorker2<T, E, X extends DisruptorEntry2<T>> implements Worker {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Nonnull
    protected final Disruptor disruptor;

    @Nonnull
    private final Thread thread;

    @Nonnull
    protected final Processor processor;

    @Nonnull
    protected final RingBuffer<X> queue1;

    @Nonnull
    private final RingBuffer<EventHolder2<E>> queue2;

    @Nonnull
    private final PostMultiProcessor externalProcessor;

    @Nonnull
    private final ConcurrentLinkedQueue<E> queuedEvents;

    @Nullable
    private volatile Instant fireTime;

    private volatile boolean triggered;

    private volatile boolean shutdown;

    private volatile int remainingCapacity1;

    private volatile int remainingCapacity2;

    public AbstractMainWorker2(
            @Nonnull Disruptor disruptor,
            @Nonnull DisruptorThread thread,
            @Nonnull Processor innerProcessor,
            @Nonnull PostMultiProcessor externalProcessor,
            @Nonnull RingBuffer<X> queue1,
            @Nonnull RingBuffer<EventHolder2<E>> queue2,
            @Nonnull ConcurrentLinkedQueue<E> queuedEvents
    ) {
        this.disruptor = disruptor;
        this.thread = thread.getThread();
        this.processor = innerProcessor;
        this.queue1 = queue1;
        this.queue2 = queue2;
        this.externalProcessor = externalProcessor;
        this.queuedEvents = queuedEvents;
    }

    private void shutdown() {

        shutdown = true;
        LockSupport.unpark(thread);
    }

    @SuppressWarnings("SameParameterValue")
    protected void mainLoop(long claimSequence1, long publishSequence1, int available1, @Nonnull Instant now) {

        long sequence2 = 0;
        int available2 = 0;

        var claim2 = -1;

        while (!disruptor.isTerminate()) {

            final var shutdown = this.shutdown;

            final var claim1 = processor.claimBlocking();
            if (claim1 != 0) {
                var n = claim1;
                available1 += n;
                do {
                    var entry = queue1.get(claimSequence1);
                    if (!entry.replay) {
                        if (isShutdownEvent(entry)) {
                            final var connectorShutdowns = entry.connectorShutdowns;
                            if (connectorShutdowns == null || connectorShutdowns.isEmpty()) {
                                logger.info("No connectors to complete");
                                shutdown();
                            } else {
                                final var count = connectorShutdowns.size();
                                final var remained = new AtomicInteger(count);
                                logger.info("Connectors to complete: {}", count);
                                for (final var connectorShutdown : connectorShutdowns) {
                                    logger.info("Stopping {}", connectorShutdown.connector);
                                    connectorShutdown.future.whenComplete((v, e) -> {
                                        if (e == null) {
                                            logger.info("Stopped {}", connectorShutdown.connector);
                                        } else {
                                            logger.error("Failed to stop {}", connectorShutdown.connector, e);
                                        }
                                        if (remained.decrementAndGet() == 0) {
                                            logger.info("All connectors completed");
                                            shutdown();
                                        }
                                    });
                                }
                            }
                        }
                    }
                    entry.clean();
                    claimSequence1++;
                } while (--n != 0);
            }

            final long nanos;
            final boolean noQueuedEvents;

            if (available1 == 0) {
                nanos = Long.MAX_VALUE;
                noQueuedEvents = true;
            } else {
                boolean timeUpdated = false;

                final var event = queuedEvents.poll();
                if (event == null) {
                    noQueuedEvents = true;
                } else {
                    noQueuedEvents = false;
                    final var incomingNanos = System.nanoTime();
                    available1--;
                    now = updateTime(now);
                    timeUpdated = true;
                    final var entry = queue1.get(publishSequence1++);
                    entry.timestamp = now;
                    entry.incomingNanos = incomingNanos;
                    entry.event = convert(event, entry);

                    while (available1 != 0) {
                        final var event2 = queuedEvents.poll();
                        if (event2 == null) {
                            break;
                        }
                        available1--;
                        final var entry2 = queue1.get(publishSequence1++);
                        entry2.timestamp = now;
                        entry2.incomingNanos = incomingNanos;
                        entry2.event = convert(event2, entry2);
                    }
                }

                if (available1 != 0 &&
                    (available2 != 0 || (available2 = claim2 = externalProcessor.claimBlocking()) != 0)) {

                    if (!timeUpdated) {
                        now = updateTime(now);
                        timeUpdated = true;
                    }
                    {
                        final var holder = queue2.get(sequence2);
                        final var entry = queue1.get(publishSequence1++);
                        entry.timestamp = now;
                        entry.incomingNanos = holder.incomingNanos;
                        entry.event = convert(holder.event, entry);
                        holder.clean();
                        available1--;
                        available2--;
                        sequence2++;
                    }
                    while (available1 != 0) {
                        if (available2 == 0) {
                            available2 = claim2 = externalProcessor.claimBlocking();
                            if (available2 == 0) {
                                break;
                            }
                        }
                        final var holder = queue2.get(sequence2);
                        final var entry = queue1.get(publishSequence1++);
                        entry.timestamp = now;
                        entry.incomingNanos = holder.incomingNanos;
                        entry.event = convert(holder.event, entry);
                        holder.clean();
                        available1--;
                        available2--;
                        sequence2++;
                    }
                    externalProcessor.publish(sequence2);
                }

                if (available1 != 0 && !triggered) {
                    final var nowFireTime = fireTime;
                    if (nowFireTime != null) {
                        if (timeUpdated) {
                            if (!now.isBefore(nowFireTime)) {
                                triggered = true;
                                available1--;
                                final var entry = queue1.get(publishSequence1++);
                                entry.timestamp = now;
                                entry.incomingNanos = System.nanoTime();
                                entry.event = newTimerEvent(entry);
                                nanos = Long.MAX_VALUE;
                            } else {
                                nanos = getNanos(now, nowFireTime);
                            }
                        } else {
                            // try stack allocation:
                            final var justNow = Instant.now();
                            if (justNow.isAfter(now)) {
                                if (!justNow.isBefore(nowFireTime)) {
                                    // heap allocation:
                                    now = Instant.ofEpochSecond(justNow.getEpochSecond(), justNow.getNano());
                                    timeUpdated = true;
                                    triggered = true;
                                    available1--;
                                    final var entry = queue1.get(publishSequence1++);
                                    entry.timestamp = now;
                                    entry.incomingNanos = System.nanoTime();
                                    entry.event = newTimerEvent(entry);
                                    nanos = Long.MAX_VALUE;
                                } else {
                                    nanos = getNanos(justNow, nowFireTime);
                                }
                            } else {
                                if (!now.isBefore(nowFireTime)) {
                                    timeUpdated = true;
                                    triggered = true;
                                    available1--;
                                    final var entry = queue1.get(publishSequence1++);
                                    entry.timestamp = now;
                                    entry.incomingNanos = System.nanoTime();
                                    entry.event = newTimerEvent(entry);
                                    nanos = Long.MAX_VALUE;
                                } else {
                                    nanos = getNanos(now, nowFireTime);
                                }
                            }
                        }
                    } else {
                        nanos = Long.MAX_VALUE;
                    }
                } else {
                    nanos = Long.MAX_VALUE;
                }

                if (timeUpdated) {
                    processor.publish(publishSequence1);
                } else if (shutdown && available1 == queue1.capacity()) {
                    disruptor.terminate();
                }
            }

            if (noQueuedEvents && claim1 == 0 && claim2 == 0 && !disruptor.isTerminate()) {
                LockSupport.parkNanos(nanos);
            }

            remainingCapacity1 = available1;
            remainingCapacity2 = available2;
        }
    }

    private long getNanos(@Nonnull Instant now, @Nonnull Instant fireTime) {
        try {
            return now.until(fireTime, ChronoUnit.NANOS);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    @Nonnull
    private Instant updateTime(@Nonnull Instant now) {

        final var t = Instant.now();
        if (t.isAfter(now)) {
            return t;
        }
        return now;
    }

    /**
     * Try to avoid excessive calling this method
     */
    public void setFireTime(@Nullable Instant fireTime) {
        this.fireTime = fireTime;
        if (fireTime != null) {
            LockSupport.unpark(thread);
        }
    }

    /**
     * Try to avoid excessive calling this method
     */
    public void untrigger() {
        triggered = false;
        LockSupport.unpark(thread);
    }

    public int getRemainingCapacity1() {
        return remainingCapacity1;
    }

    public int getRemainingCapacity2() {
        return remainingCapacity2;
    }

    @Nonnull
    protected abstract T convert(@Nonnull E source, @Nonnull X entry);

    @Nonnull
    protected abstract T newTimerEvent(@Nonnull X entry);

    protected abstract boolean isShutdownEvent(@Nonnull X entry);
}
