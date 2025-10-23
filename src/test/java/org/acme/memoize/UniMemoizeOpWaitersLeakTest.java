package org.acme.memoize;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.operators.uni.UniMemoizeOp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

import static java.time.Duration.ofSeconds;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UniMemoizeOpWaitersLeakTest {

    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    RandomGenerator random = RandomGenerator.getDefault();
    AtomicInteger counter = new AtomicInteger();

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void memoize_sequential() throws Exception {
        var memo = Uni.createFrom().deferred(this::fetchSomething)
                .memoize().forFixedDuration(ofSeconds(1));
        assertEquals(0, awaiters(memo).size());

        for (int i = 0; i < 1000; i++) {
            memo.await().indefinitely();
        }
        assertEquals(0, awaiters(memo).size());
    }

    @Test
    void memoize_concurrent() throws Exception {
        var memo = Uni.createFrom().deferred(this::fetchSomething)
                .memoize().forFixedDuration(ofSeconds(1));

        assertEquals(0, awaiters(memo).size());

        long start = System.currentTimeMillis();
        int taskCount = startTasks(memo);
        System.out.printf("Ran %d tasks in %d ms%n", taskCount, System.currentTimeMillis() - start);
        assertEquals(0, awaiters(memo).size());
    }

    private static Collection<?> awaiters(Uni<Integer> memo) throws Exception {
        var awaitersField = UniMemoizeOp.class.getDeclaredField("awaiters");
        awaitersField.setAccessible(true);
        return (Collection<?>) awaitersField.get(memo);
    }

    private int startTasks(Uni<Integer> memo) {
        int taskCount;
        try (var pool = Executors.newFixedThreadPool(50)) {
            taskCount = IntStream.generate(() -> {
                pool.submit(() -> {
                    // allocate a big object
                    memo.map(i -> new byte[5 * 1024 * 1024]).await().indefinitely();

                });
                return 1;
            }).limit(10000).sum();
        }
        return taskCount;
    }

    private Uni<Integer> fetchSomething() {
        return Uni.createFrom().completionStage(supplyAsync(() -> {
            sleep((long) random.nextGaussian(50, 0.1));
            return counter.incrementAndGet();
        }, executor));
    }

    private void sleep(long delay) {
        try {
            Thread.sleep(Duration.ofMillis(delay));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
