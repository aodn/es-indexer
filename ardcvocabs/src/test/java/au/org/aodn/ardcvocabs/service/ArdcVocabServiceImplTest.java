package au.org.aodn.ardcvocabs.service;

import au.org.aodn.ardcvocabs.BaseTestClass;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

@ExtendWith(MockitoExtension.class)
public class ArdcVocabServiceImplTest extends BaseTestClass {

    @Autowired
    protected ArdcVocabService ardcVocabService;

}
