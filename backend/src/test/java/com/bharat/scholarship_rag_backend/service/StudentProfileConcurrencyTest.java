package com.bharat.scholarship_rag_backend.service;

import com.bharat.scholarship_rag_backend.dto.StudentProfileDto;
import com.bharat.scholarship_rag_backend.repository.StudentProfileRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class StudentProfileConcurrencyTest {

    @Autowired
    private StudentProfileService studentProfileService;

    @Autowired
    private StudentProfileRepo studentProfileRepo;

    @Test
    void concurrentGetOrCreateCreatesExactlyOneProfile() throws Exception {
        String conversationId = UUID.randomUUID().toString();
        int threads = 8;

        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicLong successes = new AtomicLong();
        AtomicLong failures = new AtomicLong();
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    try {
                        barrier.await();
                    } catch (Exception await) {
                        throw new RuntimeException(await);
                    }
                    try {
                        studentProfileService.getOrCreate(conversationId);
                        successes.incrementAndGet();
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(threads, successes.get(), "every concurrent request must succeed");
        assertEquals(0, failures.get(), "no concurrent request may fail");
        assertEquals(1, studentProfileRepo.countByConversationId(conversationId),
                "exactly one profile row must exist for the conversation");
        assertEquals(conversationId, studentProfileService.getOrCreate(conversationId).getConversationId());
    }

    @Test
    void applyExtractedCreatesProfileAndPreservesPreviouslyCollectedValues() {
        String conversationId = UUID.randomUUID().toString();

        StudentProfileDto first = studentProfileService.applyExtracted(
                conversationId,
                Map.of("EDUCATION_LEVEL", "UG", "ANNUAL_FAMILY_INCOME", "500000"));

        assertEquals("UG", first.getEducationLevel());
        assertNotNull(first.getAnnualFamilyIncome());

        StudentProfileDto second = studentProfileService.applyExtracted(
                conversationId,
                Map.of("COURSE", "B.Tech"));

        assertEquals("UG", second.getEducationLevel(), "education level must not be overwritten");
        assertNotNull(second.getAnnualFamilyIncome(), "family income must not be overwritten");
        assertEquals("B.Tech", second.getCourse());
        assertEquals(1, studentProfileRepo.countByConversationId(conversationId),
                "exactly one profile row must exist for the conversation");
    }
}