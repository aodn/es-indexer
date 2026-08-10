package au.org.aodn.ardcvocabs.exception;

/**
 * Raised when a vocabulary harvest cannot safely return a complete tree.
 *
 * A harvest either returns every vocabulary it found or fails with this exception. Returning a partial
 * tree is not an option: the indexer cannot tell a genuinely small vocabulary from one whose branches
 * were lost to a rate-limited request, so a partial tree gets indexed as if it were complete.
 */
public class VocabHarvestIncompleteException extends RuntimeException {

    public VocabHarvestIncompleteException(String url, Throwable cause) {
        super(String.format("ARDC vocabulary harvest failed at %s; refusing to index an incomplete tree", url), cause);
    }
}
