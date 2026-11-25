package au.org.aodn.cloudoptimized.service;

/**
 * Decide whether a notebook URL, normally given by the data access service from the cloud optimized config is usable as a link in the index.
 */
public interface NotebookUrlValidator {
    /**
     * @param url - The notebook url to check
     * @return Never null. Invalid if the url is not an http(s) url, or if it does not respond with 2xx
     */
    NotebookUrlCheck check(String url);
}
