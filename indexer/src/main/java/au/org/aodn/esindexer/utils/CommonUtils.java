package au.org.aodn.esindexer.utils;

import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class CommonUtils {


    public static <T> Optional<T> safeGet(Supplier<T> supplier) {
        try {
            return Optional.of(supplier.get());
        } catch (
                NullPointerException
                | IndexOutOfBoundsException
                | ClassCastException ignored) {
            return Optional.empty();
        }
    }

    /** this method is used to execute a runnable and ignore exceptions
     * @param runnable the runnable function to be executed
     */
    public static void safeExecute(Runnable runnable) {
        try{
            runnable.run();
        } catch (
                NullPointerException
                | IndexOutOfBoundsException
                | ClassCastException ignored) {
        }
    }


    // alternative function for @Retryable annotation when the class is not a spring bean
    public static void persevere(BooleanSupplier action) {
        persevere(10, 1, action);
    }

    public static void persevere(int maxRetries, int delaySecond, BooleanSupplier action) {

        for (int i = 0; i < maxRetries; i++) {
            var isSuccessful = action.getAsBoolean();
            if (isSuccessful) {
                return;
            }
            try {
                Thread.sleep(delaySecond * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
