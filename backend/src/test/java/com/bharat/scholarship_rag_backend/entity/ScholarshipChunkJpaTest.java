package com.bharat.scholarship_rag_backend.entity;

import com.bharat.scholarship_rag_backend.repository.ScholarshipChunkRepo;
import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Proves the {@link ScholarshipChunk} mapping matches the generated schema: the
 * body is stored as {@code text} (chunks far exceed 255 characters) and the
 * generated {@code tsvector} column is usable by keyword retrieval.
 */
@SpringBootTest
@Transactional
class ScholarshipChunkJpaTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ScholarshipChunkRepo scholarshipChunkRepo;

    @Test
    void persistsLongContentAndSearchesGeneratedTsVector() {
        ScholarshipMetadata metadata = entityManager.find(ScholarshipMetadata.class, SchemeType.PM_USP_CSSS);
        if (metadata == null) {
            metadata = ScholarshipMetadata.builder()
                    .scheme(SchemeType.PM_USP_CSSS)
                    .schemeName("PM USP CSSS")
                    .schemeCategory(SchemeCategory.CENTRAL_SECTOR)
                    .educationLevel(EducationLevel.HIGHER_EDUCATION)
                    .build();
            entityManager.persist(metadata);
            entityManager.flush();
        }

        String content = "The annual family income limit is eight lakh rupees. ".repeat(20);

        ScholarshipChunk chunk = ScholarshipChunk.builder()
                .metadata(metadata)
                .scheme(SchemeType.PM_USP_CSSS)
                .content(content)
                .contentType(ContentType.GUIDELINE)
                .sourceDocument("guideline.md")
                .chunkIndex(0)
                .build();
        scholarshipChunkRepo.saveAndFlush(chunk);

        List<Object[]> matches = scholarshipChunkRepo.searchByKeyword("income limit", "{}", false, 5);

        assertFalse(matches.isEmpty(), "generated tsvector should match the inserted content");
    }
}
