package net.enthusia.staff.paper.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class AtomicMessageConfigurationTest {
    @Test
    void concurrentReadersObserveOnlyCompleteSnapshots() throws Exception {
        MessageConfigurationSnapshot first = snapshot("first-status", "first-reload");
        MessageConfigurationSnapshot second = snapshot("second-status", "second-reload");
        AtomicMessageConfiguration active = new AtomicMessageConfiguration(first);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService readers = Executors.newFixedThreadPool(4);

        try {
            List<Future<?>> observations = new ArrayList<>();
            for (int reader = 0; reader < 4; reader++) {
                observations.add(readers.submit(() -> {
                    await(start);
                    for (int index = 0; index < 10_000; index++) {
                        MessageCatalog catalog = active.snapshot().catalog();
                        String status = catalog.text(MessageKey.ESTAFF_STATUS_PERMISSION_DENIED);
                        String reload = catalog.text(MessageKey.ESTAFF_RELOAD_PERMISSION_DENIED);
                        boolean firstPair = status.equals("first-status") && reload.equals("first-reload");
                        boolean secondPair = status.equals("second-status") && reload.equals("second-reload");
                        assertTrue(firstPair || secondPair, () -> status + " / " + reload);
                    }
                }));
            }

            start.countDown();
            assertTrue(active.replace(first, second));
            for (Future<?> observation : observations) {
                observation.get();
            }
        } finally {
            readers.shutdownNow();
        }
    }

    private static MessageConfigurationSnapshot snapshot(String status, String reload) {
        EnumMap<MessageKey, String> templates = new EnumMap<>(MessageKey.class);
        templates.putAll(MessageCatalog.builtIn().templates());
        templates.put(MessageKey.ESTAFF_STATUS_PERMISSION_DENIED, status);
        templates.put(MessageKey.ESTAFF_RELOAD_PERMISSION_DENIED, reload);
        return new MessageConfigurationSnapshot(
                MessageConfigurationSnapshot.CURRENT_SCHEMA_VERSION,
                new MessageCatalog(templates)
        );
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while starting message snapshot reader", exception);
        }
    }
}
