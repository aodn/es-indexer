package au.org.aodn.esindexer.service;


import au.org.aodn.esindexer.BaseTestClass;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;

import static org.mockito.Mockito.times;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class VocabIndexSchedulerIT extends BaseTestClass{

    @Autowired
    VocabService vocabService;

    @Autowired
    VocabIndexScheduler scheduler;
    /**
     * The scheduler should not refresh content if the saved version same as version in ARDC
     */
    @Test
    public void verifyNoRefreshWhenVersionSame() throws IOException {
        // We need to set the spy version to the scheduler, otherwise the verify not work.
        VocabService spyVocabService = Mockito.spy(vocabService);
        scheduler.setVocabService(spyVocabService);

        // Data loaded to elastic so even you call it the times is 0
        scheduler.scheduledRefreshVocabsData();
        Mockito.verify(spyVocabService, times(0)).populateVocabsData();
    }
}
