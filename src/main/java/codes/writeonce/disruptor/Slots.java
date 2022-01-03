package codes.writeonce.disruptor;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public final class Slots {

    @Nonnull
    private final Slot<?>[] slots;

    @Nonnull
    private final Map<SlotKey<?>, Slot<?>> slotMap;

    public Slots(@Nonnull Set<SlotKey<?>> slotKeys) {

        final var size = slotKeys.size();
        this.slots = new Slot[size];
        this.slotMap = new HashMap<>(size);
        for (final var slotKey : slotKeys) {
            final var i = slotMap.size();
            final var slot = new Slot<>(slotKey, i);
            this.slots[i] = slot;
            this.slotMap.put(slotKey, slot);
        }
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    public <T> Slot<T> slot(@Nonnull SlotKey<T> slotKey) {
        return requireNonNull((Slot<T>) slotMap.get(slotKey));
    }

    @Nonnull
    public Object[] init() {

        final var values = new Object[slots.length];
        for (final var slot : slots) {
            slot.init(values);
        }
        return values;
    }

    public void clean(@Nonnull Object[] values) {

        for (final var slot : slots) {
            slot.clean(values);
        }
    }
}
