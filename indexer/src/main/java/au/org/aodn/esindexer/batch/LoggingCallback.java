package au.org.aodn.esindexer.batch;

import au.org.aodn.esindexer.service.IndexService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoggingCallback implements IndexService.Callback {
    @Override
    public void onProgress(Object update) {
        log.info("Progress: {}", update);
    }

    @Override
    public void onComplete(Object result) {
        log.info("Completed: {}", result);
    }

    @Override
    public void onError(Throwable throwable) {
        log.error("Error: {}", throwable.getMessage(), throwable);
    }
}
