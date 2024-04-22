package au.org.aodn.ardcvocabs.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * This is the model class for http://vocabs.ardc.edu.au/repository/api/lda/aodn/aodn-parameter-category-vocabulary/
 */
@Builder
@Getter
@Setter
public class CategoryVocabModel {

    protected String label;
    protected String definition;
    protected String about;
    protected List<CategoryVocabModel> broader;
    protected List<CategoryVocabModel> narrower;
}
