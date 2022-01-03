package codes.writeonce.disruptor;

import javax.annotation.Nonnull;

public interface WorkerFactory {

    @Nonnull
    Worker newWorker(@Nonnull Disruptor disruptor, @Nonnull Processor processor) throws Exception;
}
