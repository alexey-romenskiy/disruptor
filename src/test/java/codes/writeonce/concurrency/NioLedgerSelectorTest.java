package codes.writeonce.concurrency;

import org.junit.Test;

import java.io.IOException;
import java.nio.channels.Selector;

public class NioLedgerSelectorTest {

    @Test
    public void test1() throws IOException {

        final var waitableHost1 = new WaitableHost();
        final var waitableHost2 = new WaitableHost();

        try (var selector = Selector.open();
             var ledgerSelector = new NioLedgerSelector<String>(selector);
             var selectionKey1 = ledgerSelector.selectionKey(waitableHost1, "alice");
             var selectionKey2 = ledgerSelector.selectionKey(waitableHost2, "bob")) {

            selectionKey1.register();
            selectionKey2.register();

            waitableHost1.wakeup();

            m:
            while (true) {
                selector.select();

                while (true) {
                    final var next = ledgerSelector.nextReady();
                    if (next == null) {
                        break;
                    }
                    final var value = next.value();
                    System.out.println("ready: " + value);
                    switch (value) {
                        case "alice" -> waitableHost2.wakeup();
                        case "bob" -> {
                            break m;
                        }
                        default -> throw new IllegalArgumentException();
                    }
                }
            }
        }
    }
}
