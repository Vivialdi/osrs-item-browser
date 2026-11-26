package com.osr.jei;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Plugin Hub rejects a plugin that acts for the player. This scans production
 * sources for the calls reviewers treat as automation or input synthesis.
 */
public class PluginHubRulesTest {

    private static final String[] FORBIDDEN = {
        "menu" + "Action",
        "invoke" + "MenuAction",
        "java.awt.Robot",
        "Runtime.getRuntime(",
        "ProcessBuilder",
        "Thread.sleep",
        "printStackTrace(",
        "System.out",
        "System.err",
        "setAccessible("
    };

    @Test
    public void mainSourcesDoNotActForThePlayer() throws IOException {
        Path root = Paths.get("src/main/java");
        assertTrue(Files.isDirectory(root));
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> scan(path, hits));
        }
        if (!hits.isEmpty()) {
            fail(String.join("\n", hits));
        }
    }

    private static void scan(Path path, List<String> hits) {
        String text;
        try {
            text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            hits.add(path + ": " + e.getMessage());
            return;
        }
        for (String token : FORBIDDEN) {
            if (text.contains(token)) {
                hits.add(path + " contains " + token);
            }
        }
    }
}
