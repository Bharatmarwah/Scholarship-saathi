package com.bharat.scholarship_rag_backend.repository;

import com.bharat.scholarship_rag_backend.entity.StudentProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface StudentProfileRepo extends JpaRepository<StudentProfile, UUID> {

    /**
     * Locks an existing profile for the duration of the caller's transaction.
     * Profile updates are read-modify-write; without the row lock two concurrent
     * turns for the same conversation can overwrite each other silently.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StudentProfile> findByConversationId(String conversationId);

    long countByConversationId(String conversationId);

    /**
     * Atomic check-and-insert backed by {@code INSERT ... ON CONFLICT DO NOTHING}.
     * Concurrent callers with the same {@code conversation_id} are serialized by
     * PostgreSQL and exactly one row is created; the UNIQUE constraint on
     * {@code conversation_id} stays enforced. Returns the number of inserted
     * rows (1 when created, 0 when the profile already existed).
     */
    @Modifying
    @Query(nativeQuery = true, value =
            "INSERT INTO student_profiles (id, conversation_id, created_at, updated_at) " +
            "VALUES (:id, :conversationId, :now, :now) " +
            "ON CONFLICT (conversation_id) DO NOTHING")
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("conversationId") String conversationId,
                       @Param("now") LocalDateTime now);
}