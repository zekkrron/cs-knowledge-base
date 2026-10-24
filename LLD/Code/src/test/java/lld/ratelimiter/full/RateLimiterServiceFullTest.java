package lld.ratelimiter.full;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;


class RateLimiterServiceFullTest {

    private final ManualClock clock = new ManualClock(Instant.parse("2026-10-24T10:00:00Z"));
    private final RateLimiterService service = new RateLimiterService(clock);

    private static LimitPolicy policy(Algorithm a, long permits, long windowMs) {
        return new LimitPolicy(a, permits, Duration.ofMillis(windowMs));
    }

    private boolean allowed(String client, String endpoint) {
        return service.evaluate(Request.of(client, endpoint)).allowed();
    }

    private void advance(long ms) {
        clock.advance(Duration.ofMillis(ms));
    }

    // ---- E1: algorithms ------------------------------------------------------------------

    @Test
    void tokenBucketRejectsWithRetryAfterUntilNextToken() {
        service.putRule(new Rule("per-user", Scope.CLIENT, policy(Algorithm.TOKEN_BUCKET, 3, 1000)));
        assertTrue(allowed("alice", "/a"));
        assertTrue(allowed("alice", "/a"));
        assertTrue(allowed("alice", "/a"));
        Decision d = service.evaluate(Request.of("alice", "/a"));
        assertFalse(d.allowed());
        assertEquals(334, d.retryAfter().toMillis(), "one token every 333.3 ms");
        assertEquals(1, d.retryAfterSeconds());
    }

    @Test
    void fixedWindowAllowsBoundaryBurst() {
        service.putRule(new Rule("fw", Scope.CLIENT, policy(Algorithm.FIXED_WINDOW, 2, 1000)));
        advance(999);
        assertTrue(allowed("alice", "/a"));
        assertTrue(allowed("alice", "/a"));
        assertFalse(allowed("alice", "/a"));
        advance(1);
        assertTrue(allowed("alice", "/a"), "new window: the known 2x-at-the-boundary weakness");
        assertTrue(allowed("alice", "/a"));
    }

    @Test
    void slidingWindowLogIsExact() {
        service.putRule(new Rule("log", Scope.CLIENT, policy(Algorithm.SLIDING_WINDOW_LOG, 2, 1000)));
        assertTrue(allowed("alice", "/a"));
        advance(500);
        assertTrue(allowed("alice", "/a"));
        advance(100);
        Decision d = service.evaluate(Request.of("alice", "/a"));
        assertFalse(d.allowed());
        assertEquals(401, d.retryAfter().toMillis(), "the first entry leaves the window at t=1000");
        advance(401);
        assertTrue(allowed("alice", "/a"));
    }

    @Test
    void slidingWindowCounterWeighsThePreviousWindow() {
        service.putRule(new Rule("swc", Scope.CLIENT, policy(Algorithm.SLIDING_WINDOW_COUNTER, 10, 1000)));
        for (int i = 0; i < 10; i++) {
            assertTrue(allowed("alice", "/a"));
        }
        advance(1000);
        assertFalse(allowed("alice", "/a"), "estimate = 10 x (1 - 0) + 0");
        advance(500);
        int passed = 0;
        while (allowed("alice", "/a")) {
            passed++;
        }
        assertEquals(5, passed, "estimate = 10 x 0.5 = 5, so 5 more fit");
    }

    // ---- E2: several rules, all-or-nothing ----------------------------------------------

    @Test
    void laterRuleRejectionRefundsEarlierRules() {
        service.putRule(new Rule("per-user", Scope.CLIENT, policy(Algorithm.TOKEN_BUCKET, 1, 1000)));
        service.putRule(new Rule("per-endpoint", Scope.ENDPOINT, policy(Algorithm.FIXED_WINDOW, 1, 60_000)));
        assertTrue(allowed("alice", "/x"));

        Decision bob = service.evaluate(Request.of("bob", "/x"));
        assertFalse(bob.allowed());
        assertEquals("per-endpoint", bob.rule());

        assertTrue(allowed("bob", "/y"), "bob's per-user token was refunded when /x said no");
        assertEquals(1, service.rejections("per-endpoint"));
        assertEquals(0, service.rejections("per-user"));
    }

    @Test
    void globalRuleCapsAllClientsTogether() {
        service.putRule(new Rule("per-user", Scope.CLIENT, policy(Algorithm.TOKEN_BUCKET, 5, 1000)));
        service.putRule(new Rule("global", Scope.GLOBAL, policy(Algorithm.FIXED_WINDOW, 3, 1000)));
        assertTrue(allowed("a", "/x"));
        assertTrue(allowed("b", "/x"));
        assertTrue(allowed("c", "/x"));
        assertEquals("global", service.evaluate(Request.of("d", "/x")).rule());
    }

    // ---- E3: cost and decision details ---------------------------------------------------

    @Test
    void weightedCostAndRemaining() {
        service.putRule(new Rule("tokens", Scope.CLIENT, policy(Algorithm.TOKEN_BUCKET, 10, 1000)));
        Decision d = service.evaluate(new Request("alice", "/llm", 4));
        assertTrue(d.allowed());
        assertEquals(6, d.remaining());
        assertFalse(service.evaluate(new Request("alice", "/llm", 7)).allowed());
        assertThrows(ValidationException.class, () -> service.evaluate(new Request("alice", "/llm", 11)),
                "a request bigger than the whole limit can never pass, so it is a validation error");
        assertThrows(ConflictException.class, () -> service.acquireOrThrow(new Request("alice", "/llm", 7)));
    }

    @Test
    void validation() {
        assertThrows(ValidationException.class, () -> service.evaluate(Request.of("alice", "/a")), "no rules");
        assertThrows(ValidationException.class, () -> new LimitPolicy(Algorithm.FIXED_WINDOW, 0, Duration.ofSeconds(1)));
        assertThrows(ValidationException.class, () -> new Request("alice", "/a", 0));
        assertThrows(ValidationException.class, () -> Algorithm.parse("LEAKY"));
        service.putRule(new Rule("r", Scope.CLIENT, policy(Algorithm.TOKEN_BUCKET, 1, 1000)));
        assertThrows(ValidationException.class,
                () -> service.override("missing", "alice", policy(Algorithm.TOKEN_BUCKET, 1, 1000)));
    }

    // ---- E4: live overrides and eviction ------------------------------------------------

    @Test
    void overrideTakesEffectOnNextRequestAndCanBeCleared() {
        service.putRule(new Rule("per-user", Scope.CLIENT, policy(Algorithm.TOKEN_BUCKET, 1, 1000)));
        assertTrue(allowed("alice", "/a"));
        assertFalse(allowed("alice", "/a"));

        service.override("per-user", "alice", policy(Algorithm.TOKEN_BUCKET, 3, 1000));
        assertTrue(allowed("alice", "/a"));
        assertTrue(allowed("alice", "/a"));
        assertTrue(allowed("alice", "/a"));
        assertFalse(allowed("alice", "/a"));

        service.clearOverride("per-user", "alice");
        assertTrue(allowed("alice", "/a"), "policy changed, so a fresh state with the default limit");
        assertFalse(allowed("alice", "/a"));
    }

    @Test
    void idleStatesAreEvicted() {
        service.putRule(new Rule("per-user", Scope.CLIENT, policy(Algorithm.TOKEN_BUCKET, 1, 1000)));
        allowed("alice", "/a");
        allowed("bob", "/a");
        advance(500);
        allowed("bob", "/a");
        advance(600);
        assertEquals(1, service.evictIdle(Duration.ofSeconds(1)), "alice idle 1.1 s, bob 0.6 s");
        assertEquals(1, service.trackedKeys());
    }

    @Test
    void scheduledEvictionRunsInTheBackground() throws InterruptedException {
        service.putRule(new Rule("per-user", Scope.CLIENT, policy(Algorithm.TOKEN_BUCKET, 1, 1000)));
        allowed("alice", "/a");
        allowed("bob", "/a");
        advance(5000);                                   // both idle for 5 s on the manual clock
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        try {
            service.scheduleEviction(executor, Duration.ofMillis(10), Duration.ofSeconds(1));
            long deadline = System.currentTimeMillis() + 2000;
            while (service.trackedKeys() > 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertEquals(0, service.trackedKeys());
        } finally {
            executor.shutdownNow();
        }
    }

    // ---- concurrency ---------------------------------------------------------------------

    @Test
    void concurrentRequestsRespectEveryRuleExactly() throws InterruptedException {
        service.putRule(new Rule("per-user", Scope.CLIENT, policy(Algorithm.TOKEN_BUCKET, 100, 1000)));
        service.putRule(new Rule("global", Scope.GLOBAL, policy(Algorithm.SLIDING_WINDOW_COUNTER, 500, 1000)));

        int threads = 20;
        AtomicInteger allowedTotal = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            String client = "c" + (t % 10);   // two threads per client
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < 200; i++) {
                    if (service.evaluate(Request.of(client, "/x")).allowed()) {
                        allowedTotal.incrementAndGet();
                    }
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(500, allowedTotal.get(), "global cap is exact under contention, and refunds keep per-user honest");
    }

    @Test
    void cliDemo() {
        var driver = new RateLimiterApp().driver();
        driver.execute("rule per-user CLIENT TOKEN_BUCKET 2 1000");
        driver.execute("rule per-endpoint ENDPOINT FIXED_WINDOW 3 60000");
        assertEquals("ALLOWED (tightest rule=per-user, remaining=1)", driver.execute("request alice /search"));
        assertEquals("ALLOWED (tightest rule=per-user, remaining=0)", driver.execute("request alice /search"));
        assertEquals("REJECTED by per-user, retry after 500 ms", driver.execute("request alice /search"));
        assertEquals("ALLOWED (tightest rule=per-endpoint, remaining=0)", driver.execute("request bob /search"));
        assertEquals("REJECTED by per-endpoint, retry after 60000 ms", driver.execute("request carol /search"));
        assertEquals("Not a number: x", driver.execute("request alice /search x"));
    }
}
