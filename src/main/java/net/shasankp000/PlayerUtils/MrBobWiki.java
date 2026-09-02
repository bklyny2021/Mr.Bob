package net.shasankp000.PlayerUtils;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Mr.Bob's persistent wiki — a plain-text memory file that survives restarts.
 *
 * <p>Every time Mr.Bob does something notable (fights, builds, learns a fact,
 * meets someone), we append a line to the wiki. On every startup, the whole
 * wiki is loaded and injected into the LLM's system prompt, so Mr.Bob
 * "remembers" what he learned in past sessions and never forgets.</p>
 *
 * <p>This is deliberately simple (a markdown file) rather than the vector DB,
 * because the vector DB depends on an embedding model that can be flaky. A
 * plain file always works and is easy to read/edit.</p>
 */
public final class MrBobWiki {
    private static final Logger LOGGER = LoggerFactory.getLogger("ai-player-wiki");

    private static final Path WIKI_PATH = FabricLoader.getInstance()
            .getGameDir().resolve("config/mrbob_wiki.md");

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private MrBobWiki() {}

    /** Ensures the wiki file exists. */
    public static void ensureExists() {
        try {
            if (!Files.exists(WIKI_PATH)) {
                Files.createDirectories(WIKI_PATH.getParent());
                Files.writeString(WIKI_PATH,
                        "# Mr.Bob's Memory Wiki\n\n"
                        + "Everything Mr.Bob has learned. Loaded into his brain on every start.\n\n",
                        StandardCharsets.UTF_8);
                LOGGER.info("📖 Created Mr.Bob wiki at {}", WIKI_PATH);
            }
        } catch (IOException e) {
            LOGGER.error("Could not create wiki: {}", e.getMessage());
        }
    }

    /** Appends a learned fact/experience to the wiki. */
    public static void learn(String entry) {
        ensureExists();
        try {
            String line = "- [" + LocalDateTime.now().format(TS) + "] " + entry + "\n";
            Files.writeString(WIKI_PATH, line, StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            LOGGER.info("🧠 Mr.Bob learned: {}", entry);
        } catch (IOException e) {
            LOGGER.error("Could not write to wiki: {}", e.getMessage());
        }
    }

    /** Loads the entire wiki as a string for injection into the system prompt. */
    public static String load() {
        ensureExists();
        try {
            return Files.readString(WIKI_PATH, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Could not read wiki: {}", e.getMessage());
            return "";
        }
    }

    /** Returns the wiki content wrapped as a system-prompt section, or "" if empty. */
    public static String asSystemPromptSection() {
        String wiki = load();
        if (wiki == null || wiki.isBlank() || wiki.contains("Everything Mr.Bob has learned") && wiki.trim().lines().count() <= 3) {
            return "";
        }
        return "\n\n[Your memory wiki — what you have learned and remember across sessions]\n" + wiki;
    }

    /** Remembers a conversation exchange so Mr.Bob can recall it later. */
    public static void rememberConversation(String speaker, String message) {
        // Keep it short — just the gist, so the wiki doesn't bloat.
        String snippet = message.length() > 200 ? message.substring(0, 200) + "…" : message;
        learn(speaker + " said: \"" + snippet + "\"");
    }
}
