package codes.writeonce.utils;

import codes.writeonce.disruptor.Disruptor;
import codes.writeonce.disruptor.Processor;
import codes.writeonce.disruptor.Worker;

import javax.annotation.Nonnull;

public class NoopWorker implements Worker {

    @Nonnull
    private final Disruptor disruptor;

    @Nonnull
    private final Processor processor;

    public NoopWorker(@Nonnull Disruptor disruptor, @Nonnull Processor processor) {
        this.disruptor = disruptor;
        this.processor = processor;
    }

    @Override
    public void run() {

        long sequence = 0;

        while (!disruptor.isTerminate()) {
            var n = processor.claim();
            if (n != 0) {
                sequence += n;
                processor.publish(sequence);
            }
        }
    }
}
