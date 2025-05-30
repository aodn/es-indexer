package au.org.aodn.esindexer.utils;

import au.org.aodn.metadata.iso19115_3_2018.*;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.List;
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

    // alternative function for @Retryable annotation when the class is not a spring bean
    public static void persevere(BooleanSupplier action) {
        persevere(10, 2, action);
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

    public static String getUUID(MDMetadataType source) {
        return source
                .getMetadataIdentifier()
                .getMDIdentifier()
                .getCode()
                .getCharacterString()
                .getValue()
                .toString();
    }

    public static String getTitle(MDMetadataType source) {
        List<MDDataIdentificationType> items = MapperUtils.findMDDataIdentificationType(source);
        if(!items.isEmpty()) {
            // Need to assert only 1 block contains our target
            return items.stream()
                    .map(item -> safeGet(() -> item.getCitation().getAbstractCitation().getValue()))
                    // If valid
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(ac -> {
                        // Try to find the title from these places
                        if(ac instanceof CICitationType2 type2) {
                            return type2.getTitle().getCharacterString().getValue().toString();
                        }
                        else if(ac instanceof CICitationType type1) {
                            // Backward compatible
                            return type1.getTitle().getCharacterString().getValue().toString();
                        }
                        else {
                            return "";
                        }
                    })
                    // If blank that means not found in map and need to filter out
                    .filter(s -> !s.isBlank())
                    // Just need to find the first valid one
                    .findFirst()
                    .orElse("");
        }
        return "";
    }

    public static String getDescription(MDMetadataType source) {
        List<MDDataIdentificationType> items = MapperUtils.findMDDataIdentificationType(source);

        if(!items.isEmpty()) {
            // Need to assert only 1 block contains our target
            for(MDDataIdentificationType i : items) {
                // TODO: Null or empty check
                return i.getAbstract().getCharacterString().getValue().toString();
            }
        }
        return "";
    }
}
