package au.org.aodn.esindexer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The setting was changed by someone else between the read and the write, nothing is changed and
 * the caller should retry.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class IndexerSettingConflictException extends RuntimeException {
    public IndexerSettingConflictException(String message) { super(message); }
}
