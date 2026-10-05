package net.enthusia.staff.paper.config;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class InvestigationToolConfigurationParserTest {
    @Test void defaultsRemainCompatibleAndExplicitMalformedSettingsAreRejected() throws Exception {
        var parser = new InvestigationToolConfigurationParser();
        var json = new ObjectMapper();
        var errors = new ArrayList<String>();
        assertTrue(parser.parse(json.readTree("{}"), errors).isEmpty());
        assertTrue(errors.isEmpty());
        parser.parse(json.readTree("""
                {"staff-tools":{"slots":{"random-teleport":1,"console-command":0},
                  "random-teleport":{"recent-targets":1001,"skip-idle":"yes","idle-seconds":2}},
                 "investigation":{"flags":{"categories":["watch","watch"]}}}
                """), errors);
        assertTrue(errors.size() >= 6, errors.toString());
    }
    @Test void restartFingerprintIgnoresMappingOrderAndIncludesEachNewSetting() throws Exception {
        var parser = new InvestigationToolConfigurationParser();
        var json = new ObjectMapper();
        var errors = new ArrayList<String>();
        var first = parser.parse(json.readTree("{\"staff-tools\":{\"random-teleport\":{\"skip-idle\":true,\"recent-targets\":3}}}"), errors);
        var reordered = parser.parse(json.readTree("{\"staff-tools\":{\"random-teleport\":{\"recent-targets\":3,\"skip-idle\":true}}}"), errors);
        assertEquals(first, reordered);
        var changed = parser.parse(json.readTree("{\"staff-tools\":{\"random-teleport\":{\"recent-targets\":4,\"skip-idle\":true}}}"), errors);
        assertNotEquals(first, changed);
        assertTrue(errors.isEmpty());
    }
}
