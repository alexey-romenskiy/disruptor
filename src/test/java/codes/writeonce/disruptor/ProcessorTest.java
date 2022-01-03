package codes.writeonce.disruptor;

import org.junit.Test;

import static java.lang.Long.MAX_VALUE;
import static java.lang.Long.MIN_VALUE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ProcessorTest {

    @SuppressWarnings("NumericOverflow")
    @Test
    public void available() {
        final var a = new Disruptor();
        final var t = Thread.currentThread();
        assertEquals(1,
                new Processor(new Barrier(1, a), new Barrier[]{new Barrier(2, a), new Barrier(3, a)}, t).claim());
        assertEquals(0,
                new Processor(new Barrier(1, a), new Barrier[]{new Barrier(1, a), new Barrier(2, a)}, t).claim());
        assertEquals(1024,
                new Processor(new Barrier(MIN_VALUE - 1024, a), new Barrier[]{new Barrier(MIN_VALUE, a)}, t).claim());
        assertEquals(1024,
                new Processor(new Barrier(MAX_VALUE - 1024, a), new Barrier[]{new Barrier(MAX_VALUE, a)}, t).claim());
        assertEquals(1, new Processor(new Barrier(-1, a), new Barrier[]{new Barrier(0, a)}, t).claim());
        assertEquals(2, new Processor(new Barrier(-1, a), new Barrier[]{new Barrier(1, a)}, t).claim());
        assertEquals(1024, new Processor(new Barrier(-1024, a), new Barrier[]{new Barrier(0, a)}, t).claim());
        assertEquals(1,
                new Processor(new Barrier(-1, a), new Barrier[]{new Barrier(0, a), new Barrier(1, a)}, t).claim());
        assertEquals(1, new Processor(new Barrier(MAX_VALUE, a),
                new Barrier[]{new Barrier(MAX_VALUE + 1, a), new Barrier(MAX_VALUE + 2, a)}, t).claim());
        assertEquals(1, new Processor(new Barrier(MAX_VALUE - 1, a),
                new Barrier[]{new Barrier(MAX_VALUE, a), new Barrier(MAX_VALUE + 1, a)}, t).claim());
        assertEquals(1,
                new Processor(new Barrier(-2, a), new Barrier[]{new Barrier(-1, a), new Barrier(0, a)}, t).claim());
    }

    @Test
    public void rignBuffer() {

        final var a = new Disruptor();
        final var t = new DisruptorThread(a, Thread::new);
        final var ringBuffer = new RingBuffer<>(a, 10, () -> "");
        final var barrier1 = ringBuffer.newBarrier(0);
        final var barrier2 = ringBuffer.newBarrier(0);
        final var barrier3 = ringBuffer.newBarrier(0);
        final var barrier4 = ringBuffer.newBarrier(0);
        final var barrier5 = ringBuffer.newBarrier(0);
        final var barrier6 = ringBuffer.newBarrier(0);
        final var processor1 = ringBuffer.newProcessor(t, barrier1, barrier3, barrier4, barrier5, barrier6);
        final var processor2 = ringBuffer.newProcessor(t, barrier2, barrier1);
        final var processor3 = ringBuffer.newProcessor(t, barrier3, barrier1);
        final var processor4 = ringBuffer.newProcessor(t, barrier4, barrier2);
        final var processor5 = ringBuffer.newProcessor(t, barrier5, barrier2);
        final var processor6 = ringBuffer.newProcessor(t, barrier6, barrier2);

        assertEquals(0, processor1.claim());
        assertEquals(0, processor2.claim());
        assertEquals(0, processor3.claim());
        assertEquals(0, processor4.claim());
        assertEquals(0, processor5.claim());
        assertEquals(0, processor6.claim());

        processor1.publish(10);

        assertEquals(0, processor1.claim());
        assertEquals(0, processor4.claim());
        assertEquals(0, processor5.claim());
        assertEquals(0, processor6.claim());

        assertEquals(10, processor2.claim());

        assertEquals(0, processor1.claim());
        assertEquals(0, processor2.claim());
        assertEquals(0, processor4.claim());
        assertEquals(0, processor5.claim());
        assertEquals(0, processor6.claim());

        assertEquals(10, processor3.claim());

        assertEquals(0, processor1.claim());
        assertEquals(0, processor2.claim());
        assertEquals(0, processor3.claim());
        assertEquals(0, processor4.claim());
        assertEquals(0, processor5.claim());
        assertEquals(0, processor6.claim());

        processor3.publish(10);

        assertEquals(0, processor1.claim());
        assertEquals(0, processor2.claim());
        assertEquals(0, processor3.claim());
        assertEquals(0, processor4.claim());
        assertEquals(0, processor5.claim());
        assertEquals(0, processor6.claim());

        processor2.publish(10);

        assertEquals(0, processor1.claim());
        assertEquals(0, processor2.claim());
        assertEquals(0, processor3.claim());

        assertEquals(10, processor4.claim());

        assertEquals(0, processor1.claim());
        assertEquals(0, processor2.claim());
        assertEquals(0, processor3.claim());
        assertEquals(0, processor4.claim());

        assertEquals(10, processor5.claim());

        assertEquals(0, processor1.claim());
        assertEquals(0, processor2.claim());
        assertEquals(0, processor3.claim());
        assertEquals(0, processor4.claim());
        assertEquals(0, processor5.claim());

        assertEquals(10, processor6.claim());

        assertEquals(0, processor1.claim());
        assertEquals(0, processor2.claim());
        assertEquals(0, processor3.claim());
        assertEquals(0, processor4.claim());
        assertEquals(0, processor5.claim());
        assertEquals(0, processor6.claim());

        processor4.publish(10);

        assertEquals(0, processor1.claim());
        assertEquals(0, processor2.claim());
        assertEquals(0, processor3.claim());
        assertEquals(0, processor4.claim());
        assertEquals(0, processor5.claim());
        assertEquals(0, processor6.claim());

        processor5.publish(10);

        assertEquals(0, processor1.claim());
        assertEquals(0, processor2.claim());
        assertEquals(0, processor3.claim());
        assertEquals(0, processor4.claim());
        assertEquals(0, processor5.claim());
        assertEquals(0, processor6.claim());

        processor6.publish(10);

        assertEquals(0, processor2.claim());
        assertEquals(0, processor3.claim());
        assertEquals(0, processor4.claim());
        assertEquals(0, processor5.claim());
        assertEquals(0, processor6.claim());

        assertEquals(10, processor1.claim());
        assertEquals(0, processor1.claim());
    }

    @Test
    public void rignBuffer2() throws Exception {

        final var a = new Disruptor();
        final var t = new DisruptorThread(a, Thread::new);
        final var ringBuffer = new RingBuffer<>(a, 10, () -> "");
        final var barrier1 = ringBuffer.newMultiBarrier(0);
        final var barrier2 = ringBuffer.newBarrier(0);
        final var processor1 = ringBuffer.newMultiProcessor(barrier1, barrier2);
        final var processor2 = ringBuffer.newPostMultiProcessor(t, barrier2, barrier1);

        assertEquals(0, processor1.claim(1024));
        try {
            processor1.claim(1);
            fail();
        } catch (InsufficientCapacityException ignore) {
            // empty
        }
        assertEquals(0, processor2.claim());

        processor1.publish(0);
        assertEquals(1, processor2.claim());
        processor2.publish(1);

        assertEquals(1024, processor1.claim(1));
        try {
            processor1.claim(1);
            fail();
        } catch (InsufficientCapacityException ignore) {
            // empty
        }
    }
}
