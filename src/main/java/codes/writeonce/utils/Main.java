package codes.writeonce.utils;

import codes.writeonce.disruptor.Disruptor;
import codes.writeonce.disruptor.DisruptorEntry;
import codes.writeonce.disruptor.Event;
import codes.writeonce.disruptor.EventHolder;
import codes.writeonce.disruptor.MainWorker;
import codes.writeonce.disruptor.QueueSender;
import codes.writeonce.disruptor.Sender;
import codes.writeonce.disruptor.ShutdownEvent;
import codes.writeonce.disruptor.Slot;
import codes.writeonce.disruptor.Slots;
import codes.writeonce.disruptor.TimerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Arrays.asList;

public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private static final AtomicLong SEQUENCE = new AtomicLong();

    public static void main(String[] args) throws Exception {

        final var id = SEQUENCE.incrementAndGet();

        final var slotKeys = new HashSet<>(asList(NettyRequestSlotKey.INSTANCE, NettyWebSocketSlotKey.INSTANCE));

        final var slots = new Slots(slotKeys);

        final var disruptor = new Disruptor();

        final var ringBuffer1 = disruptor.newRingBuffer(3, () -> new DisruptorEntry<Event>(slots));
        final var ringBuffer2 = disruptor.newRingBuffer(3, () -> new EventHolder<Event>(slots));

        final var barrier1 = ringBuffer1.newBarrier(0);
        final var barrier2 = ringBuffer1.newBarrier(0);
        final var barrier3 = ringBuffer1.newBarrier(0);
        final var barrier4 = ringBuffer1.newBarrier(0);
        final var barrier5 = ringBuffer1.newBarrier(0);
        final var barrier6 = ringBuffer1.newBarrier(0);
        final var barrier7 = ringBuffer2.newMultiBarrier(0);
        final var barrier8 = ringBuffer2.newBarrier(0);

        final var thread1 = disruptor.newThread(r -> new Thread(r, "app" + id + ".main"));
        final var thread2 = disruptor.newThread(r -> new Thread(r, "app" + id + ".service"));
        final var thread3 = disruptor.newThread(r -> new Thread(r, "app" + id + ".eventPersister"));
        final var thread4 = disruptor.newThread(r -> new Thread(r, "app" + id + ".clientConnector"));
        final var thread5 = disruptor.newThread(r -> new Thread(r, "app" + id + ".exchangeConnectors"));
        final var thread6 = disruptor.newThread(r -> new Thread(r, "app" + id + ".miscConnectors"));

        final var processor1 = ringBuffer1.newProcessor(thread1, barrier1, barrier3, barrier4, barrier5, barrier6);
        final var processor2 = ringBuffer1.newProcessor(thread2, barrier2, barrier1);
        final var processor3 = ringBuffer1.newProcessor(thread3, barrier3, barrier1);
        final var processor4 = ringBuffer1.newProcessor(thread4, barrier4, barrier2);
        final var processor5 = ringBuffer1.newProcessor(thread5, barrier5, barrier2);
        final var processor6 = ringBuffer1.newProcessor(thread6, barrier6, barrier2);
        final var processor7 = ringBuffer2.newMultiProcessor(barrier7, barrier8);
        final var processor8 = ringBuffer2.newPostMultiProcessor(thread1, barrier8, barrier7);

        final var queuedEvents = new ConcurrentLinkedQueue<Event>();
        final var queueSender = new QueueSender<>(queuedEvents, thread1);

        final var sender = new Sender<>(disruptor, processor7, ringBuffer2);

        final var connector = new NettyConnector(
                new WebsocketMessageFactoryImpl(sender, slots),
                new SimpleMapping().get("/", new SimpleRequestHandlerFactory<>(sender, slots, new AtomicBoolean(true),
                        rc -> new NettyEvent())),
                Path.of(System.getProperty("https.chain")),
                Path.of(System.getProperty("https.key")),
                System.getProperty("backend.bind.addr"),
                Integer.getInteger("backend.port")
        );

        disruptor.addWorker(thread1, new MainWorker<>(
                disruptor,
                thread1,
                slots,
                processor1,
                processor8,
                ringBuffer1,
                ringBuffer2,
                TimerEvent::new,
                queuedEvents
        ));

        disruptor.addWorker(thread2, new BlockingNoopWorker(disruptor, processor2));
        disruptor.addWorker(thread3, new BlockingNoopWorker(disruptor, processor3));
        disruptor.addWorker(thread4, new NettyWorker(disruptor, ringBuffer1, sender, slots, processor4));
        disruptor.addWorker(thread5, new BlockingNoopWorker(disruptor, processor5));
        disruptor.addWorker(thread6, new BlockingNoopWorker(disruptor, processor6));

        disruptor.addConnector(connector);

        disruptor.start();

        connector.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                LOGGER.info("Stopping event sources");
                connector.shutdown().get();
                LOGGER.info("Sending ShutdownEvent");
                sender.send(System.nanoTime(), new ShutdownEvent());
                disruptor.close();
            } catch (Throwable e) {
                LOGGER.info("Failed to shutdown gracefully", e);
            }
        }));
    }

    private static class WebsocketMessageFactoryImpl implements WebsocketMessageFactory {

        @Nonnull
        private final Sender<Event> sender;

        @Nonnull
        private final Slot<WebSocketProtocolHandler> websocketSlot;

        public WebsocketMessageFactoryImpl(@Nonnull Sender<Event> sender, @Nonnull Slots slots) {
            this.sender = sender;
            this.websocketSlot = slots.slot(NettyWebSocketSlotKey.INSTANCE);
        }

        @Override
        public void websocketMessageEvent(@Nonnull WebSocketProtocolHandler webSocketProtocolHandler,
                long websocketId, @Nonnull String text) {
            sender.send(System.nanoTime(), new WebsocketMessageEvent(websocketId, text), websocketSlot,
                    webSocketProtocolHandler);
        }

        @Override
        public void websocketDisconnectedEvent(@Nonnull WebSocketProtocolHandler webSocketProtocolHandler,
                long websocketId) {
            sender.send(System.nanoTime(), new WebsocketDisconnectedEvent(websocketId), websocketSlot,
                    webSocketProtocolHandler);
        }

        @Override
        public void websocketConnectedEvent(@Nonnull WebSocketProtocolHandler webSocketProtocolHandler,
                long websocketId) {
            sender.send(System.nanoTime(), new WebsocketConnectedEvent(websocketId), websocketSlot,
                    webSocketProtocolHandler);
        }
    }
}
