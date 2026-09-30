package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HelperObserverProtectionWiringTest {
    @Test
    void paperRuntimeRegistersHelperObserverProtectionListener() throws IOException {
        String runtime = Files.readString(paperModule().resolve(
                "src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java"
        ));

        assertTrue(
                runtime.contains("new HelperObserverProtectionListener(staffMode)"),
                "Helper observer protections must remain registered in the Paper runtime"
        );
    }

    private static Path paperModule() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.exists(current.resolve("src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java"))) {
            return current;
        }
        Path paper = current.resolve("paper");
        if (Files.exists(paper.resolve("src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java"))) {
            return paper;
        }
        throw new IllegalStateException("Could not locate the Paper module from " + current);
    }
}
