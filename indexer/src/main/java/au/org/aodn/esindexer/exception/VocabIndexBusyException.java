package au.org.aodn.esindexer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class VocabIndexBusyException extends RuntimeException {
    public VocabIndexBusyException(String message) {
        super(message);
    }
}
