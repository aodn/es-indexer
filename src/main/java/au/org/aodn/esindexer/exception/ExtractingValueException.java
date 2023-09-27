package au.org.aodn.esindexer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class ExtractingValueException extends RuntimeException {
    public ExtractingValueException(String message) { super(message); }
}
