package au.org.aodn.esindexer.utils;

import java.util.Optional;
import java.util.function.Supplier;

public class SafeGetUtils {

    public static <T> Optional<T> safeGet(Supplier<T> supplier) {
        try {
            return Optional.of(supplier.get());
        } catch (NullPointerException ignored) {
            return Optional.empty();
        }
    }
}
