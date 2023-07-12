package codes.writeonce.concurrency;

import org.junit.Test;

import java.io.IOException;
import java.nio.channels.Selector;

public class NioHiveTest {

    @Test
    public void test1() throws IOException {

        final var bloom1 = new Bloom();
        final var bloom2 = new Bloom();

        try (var selector = Selector.open();
             var hive = new NioHive<String>(selector);
             var bee1 = hive.bee(bloom1, "alice");
             var bee2 = hive.bee(bloom2, "bob")) {

            bee1.ready();
            bee2.ready();

            bloom1.ready();

            m:
            while (true) {
                hive.select();

                for (var bee = hive.nextBee(); bee != null; bee = hive.nextBee()) {
                    switch (bee.value()) {
                        case "alice" -> bloom2.ready();
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
