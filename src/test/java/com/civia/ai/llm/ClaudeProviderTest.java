package com.civia.ai.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.anthropic.models.messages.OutputConfig;
import org.junit.jupiter.api.Test;

class ClaudeProviderTest {

    @Test
    void effortDefaultsToHighAndAcceptsKnownLevels() {
        assertEquals(OutputConfig.Effort.HIGH, ClaudeProvider.effort(null));
        assertEquals(OutputConfig.Effort.HIGH, ClaudeProvider.effort(" "));
        assertEquals(OutputConfig.Effort.HIGH, ClaudeProvider.effort("desconocido"));
        assertEquals(OutputConfig.Effort.LOW, ClaudeProvider.effort("low"));
        assertEquals(OutputConfig.Effort.MEDIUM, ClaudeProvider.effort("Medium"));
        assertEquals(OutputConfig.Effort.XHIGH, ClaudeProvider.effort("xhigh"));
        assertEquals(OutputConfig.Effort.MAX, ClaudeProvider.effort(" MAX "));
    }
}
