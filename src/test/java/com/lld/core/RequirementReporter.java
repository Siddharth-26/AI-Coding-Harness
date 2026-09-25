package com.lld.core;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Prints a requirement checklist while FR / NFR tests run, so the output is something you can
 * show the interviewer:
 *
 *   == Movie booking: functional requirements
 *     PASS  FR-1: a user can hold up to 10 free seats of a show
 *     FAIL  FR-2: a hold expires after 10 minutes  ->  expected: <true> but was: <false>
 *     1 passed, 1 failed
 *
 * Usage: put @ExtendWith(RequirementReporter.class) and @DisplayName on the test class, and
 * @DisplayName("FR-n: ...") / ("NFR-n: ...") on each test. Call note("p99 58 us") inside a test to
 * print a measured value under its result line.
 */
public class RequirementReporter implements BeforeAllCallback, AfterAllCallback, TestWatcher {

    private static final AtomicReference<String> NOTE = new AtomicReference<>();

    private final AtomicInteger passed = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();
    private final AtomicInteger skipped = new AtomicInteger();

    /** Attach a one-line detail (a measurement, a count) to the current test's result line. */
    public static void note(String detail) {
        NOTE.set(detail);
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        passed.set(0);
        failed.set(0);
        skipped.set(0);
        System.out.println();
        System.out.println("== " + context.getDisplayName());
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
        passed.incrementAndGet();
        print("PASS", context, null);
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        failed.incrementAndGet();
        print("FAIL", context, cause);
    }

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        skipped.incrementAndGet();
        print("SKIP", context, cause);
    }

    @Override
    public void testDisabled(ExtensionContext context, Optional<String> reason) {
        skipped.incrementAndGet();
        System.out.println("  SKIP  " + name(context) + reason.map(r -> "  ->  " + r).orElse(""));
    }

    @Override
    public void afterAll(ExtensionContext context) {
        System.out.println("  " + passed.get() + " passed, " + failed.get() + " failed"
                + (skipped.get() > 0 ? ", " + skipped.get() + " skipped" : ""));
    }

    private static void print(String status, ExtensionContext context, Throwable cause) {
        String detail = cause == null ? "" : "  ->  " + firstLine(cause);
        System.out.println("  " + status + "  " + name(context) + detail);
        String note = NOTE.getAndSet(null);
        if (note != null) {
            System.out.println("          " + note);
        }
    }

    /** Parameterized / repeated invocations get the method's display name as a prefix. */
    private static String name(ExtensionContext context) {
        return context.getParent()
                .filter(parent -> parent.getTestMethod().isPresent())
                .map(parent -> parent.getDisplayName() + " [" + context.getDisplayName() + "]")
                .orElse(context.getDisplayName());
    }

    private static String firstLine(Throwable error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
