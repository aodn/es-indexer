package au.org.aodn.researchvocabs.model;

import lombok.*;

import java.util.List;
import java.util.UUID;

/**
 * This is the model class for http://vocabs.ardc.edu.au/repository/api/lda/aodn/aodn-parameter-category-vocabulary/
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GcmdKeywordModel {
    protected UUID key;
    protected String title;
    protected List<GcmdKeywordModel> children;
}
