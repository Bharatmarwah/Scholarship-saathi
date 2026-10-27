package com.bharat.scholarship_rag_backend.repository;

import com.bharat.scholarship_rag_backend.entity.ScholarshipMetadata;
import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Administrative facts about each scheme, keyed by the {@link SchemeType} the
 * rest of the application already uses.
 */
public interface ScholarshipMetadataRepo extends JpaRepository<ScholarshipMetadata, SchemeType> {
}
