package codes.writeonce.utils;

import codes.writeonce.disruptor.Disruptor;
import codes.writeonce.disruptor.Processor;
import codes.writeonce.disruptor.Worker;
import codes.writeonce.disruptor.WorkerFactory;

import javax.annotation.Nonnull;

public class NoopWorkerFactory implements WorkerFactory {

    @Nonnull
    @Override
    public Worker newWorker(@Nonnull Disruptor disruptor, @Nonnull Processor processor) {
        return new BlockingNoopWorker(disruptor, processor);
    }
}
