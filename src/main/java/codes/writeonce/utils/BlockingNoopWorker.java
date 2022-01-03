package codes.writeonce.utils;

import codes.writeonce.disruptor.Disruptor;
import codes.writeonce.disruptor.Processor;
import codes.writeonce.disruptor.Worker;

import javax.annotation.Nonnull;
import java.util.concurrent.locks.LockSupport;

public class BlockingNoopWorker implements Worker {

    @Nonnull
    private final Disruptor disruptor;

    @Nonnull
    private final Processor processor;

    public BlockingNoopWorker(@Nonnull Disruptor disruptor, @Nonnull Processor processor) {
        this.disruptor = disruptor;
        this.processor = processor;
    }

    @Override
    public void run() {

        long sequence = 0;

        while (true) {
            var n = processor.claimBlocking();
            if (disruptor.isTerminate()) {
                break;
            }
            if (n == 0) {
                LockSupport.park();
            } else {
                sequence += n;
                processor.publish(sequence);
            }
        }
    }
}
