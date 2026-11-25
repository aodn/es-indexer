package au.org.aodn.cloudoptimized.service;

/**
 * The outcome of checking a notebook URL.
 *
 * @param valid  - true if the URL can be used as a link
 * @param reason - Why the URL is rejected, for logging. Null when valid.
 */
public record NotebookUrlCheck(boolean valid, String reason) {

    private static final NotebookUrlCheck VALID = new NotebookUrlCheck(true, null);

    public static NotebookUrlCheck ok() {
        return VALID;
    }

    public static NotebookUrlCheck invalid(String reason) {
        return new NotebookUrlCheck(false, reason);
    }
}
