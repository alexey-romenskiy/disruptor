package codes.writeonce.disruptor;

import sun.misc.Unsafe;

import javax.annotation.Nonnull;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;

final class Utils {

    private static final Unsafe THE_UNSAFE;

    static {
        try {
            final PrivilegedExceptionAction<Unsafe> action = () -> {
                final var theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (Unsafe) theUnsafe.get(null);
            };

            THE_UNSAFE = AccessController.doPrivileged(action);
        } catch (Exception e) {
            throw new RuntimeException("Unable to load unsafe", e);
        }
    }

    @Nonnull
    public static Unsafe getUnsafe() {
        return THE_UNSAFE;
    }

    private Utils() {
        // empty
    }
}
