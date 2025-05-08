package au.org.aodn.metadata.geonetwork.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class MetadataUnpublishedException extends RuntimeException {
    public MetadataUnpublishedException(String message) { super(message); }
}
