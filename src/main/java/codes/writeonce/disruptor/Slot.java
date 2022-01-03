package codes.writeonce.disruptor;

import javax.annotation.Nonnull;

public class Slot<T> {

    @Nonnull
    private final SlotKey<T> key;

    private final int index;

    Slot(@Nonnull SlotKey<T> key, int index) {
        this.key = key;
        this.index = index;
    }

    @SuppressWarnings("unchecked")
    public T get(@Nonnull Object[] slots) {
        return (T) slots[index];
    }

    public void set(@Nonnull Object[] slots, T value) {
        slots[index] = value;
    }

    public void init(@Nonnull Object[] slots) {
        slots[index] = key.init();
    }

    @SuppressWarnings("unchecked")
    public void clean(@Nonnull Object[] slots) {
        slots[index] = key.clean((T) slots[index]);
    }
}
