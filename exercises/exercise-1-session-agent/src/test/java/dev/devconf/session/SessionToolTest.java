package dev.devconf.session;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SessionToolTest {

    @Test
    void loadsCatalogEntriesWithLevelField() throws IOException {
        String catalog = """
                [{
                  "id": "S1",
                  "title": "Agentic AI in Java",
                  "speaker": "Alex Example",
                  "track": "Agentic Engineering & Tooling",
                  "room": "Room 1",
                  "date": "2026-10-07",
                  "time": "10:30",
                  "duration": 40,
                  "level": "Intermediate",
                  "description": "A session about agents.",
                  "tags": ["agentic"]
                }]
                """;
        Path sessionData = Files.createTempFile("sessions", ".json");
        try {
            Files.writeString(sessionData, catalog);
            SessionTool tool = new SessionTool();
            tool.sessionDataPath = sessionData.toString();

            assertDoesNotThrow(tool::loadSessions);
            assertEquals("Available tracks: Agentic Engineering & Tooling", tool.listTracks());
        } finally {
            Files.deleteIfExists(sessionData);
        }
    }
}
