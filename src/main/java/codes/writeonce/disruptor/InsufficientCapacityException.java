package codes.writeonce.disruptor;

import java.io.Serial;

public final class InsufficientCapacityException extends Exception {

    @Serial
    private static final long serialVersionUID = 7484961699217209044L;

    public static final InsufficientCapacityException INSTANCE = new InsufficientCapacityException();

    private InsufficientCapacityException() {
        // empty
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
