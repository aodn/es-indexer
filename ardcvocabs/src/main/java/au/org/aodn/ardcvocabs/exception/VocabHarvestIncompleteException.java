package au.org.aodn.ardcvocabs.exception;

/**
 * Raised when a vocabulary harvest cannot safely return a complete tree.
 */
public class VocabHarvestIncompleteException extends RuntimeException {

    public VocabHarvestIncompleteException(
            String url,
            int consecutiveFailures,
            int maxConsecutiveFailures,
            Throwable cause) {
        super(String.format(
                "ARDC vocabulary harvest failed at %s after %d consecutive exhausted-retry failure(s) "
                        + "(item failure threshold %d)",
                url,
                consecutiveFailures,
                maxConsecutiveFailures), cause);
    }
}
