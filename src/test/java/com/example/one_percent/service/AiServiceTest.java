package com.example.one_percent.service;

import com.example.one_percent.exception.ResourceNotFoundException;
import com.example.one_percent.model.JournalEntry;
import com.example.one_percent.service.ai.AiService;
import com.example.one_percent.service.client.OpenAIClient;
import com.example.one_percent.service.journal.JournalService;
import com.example.one_percent.util.ContextUtil;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock
    private JournalService journalService;
    @Mock
    private ContextUtil contextUtil;

    // A spy on the real client: message building is real, the network call is stubbed.
    private OpenAIClient openAIClient;
    private AiService aiService;

    @BeforeEach
    void setUp() throws IOException {
        openAIClient = spy(new OpenAIClient());
        lenient().doReturn("feedback").when(openAIClient).sendChatCompletion(any(), anyString(), anyInt(), anyDouble());
        aiService = new AiService(openAIClient, journalService, contextUtil);
    }

    private JournalEntry entry(String id, String userId, String content) {
        JournalEntry e = new JournalEntry();
        e.setId(id);
        e.setUserId(userId);
        e.setContent(content);
        return e;
    }

    @Test
    void savedEntry_isLoadedThroughOwnershipCheckAndUsesOwnersContext() throws IOException {
        JournalEntry mine = entry("j1", "alice", "I procrastinated today");
        when(journalService.getOwnedJournalEntry("j1")).thenReturn(mine);
        when(contextUtil.getUserContext("alice")).thenReturn("CTX");
        when(contextUtil.getLLMContext(mine)).thenReturn("PAST");

        String result = aiService.getAiFeedback("j1", "ignored when id given");

        assertThat(result).isEqualTo("feedback");
        ArgumentCaptor<ArrayNode> messages = ArgumentCaptor.forClass(ArrayNode.class);
        verify(openAIClient).sendChatCompletion(messages.capture(), eq("gpt-4o"), anyInt(), anyDouble());
        String userMessage = messages.getValue().get(1).get("content").asText();
        assertThat(userMessage).contains("CTX", "I procrastinated today", "PAST");
        verify(journalService, never()).buildDraftEntry(anyString());
    }

    @Test
    void draft_isNotSavedAndUsesAuthenticatedUsersContext() throws IOException {
        JournalEntry draft = entry(null, "alice", "draft text");
        when(journalService.buildDraftEntry("draft text")).thenReturn(draft);
        when(contextUtil.getUserContext("alice")).thenReturn("");
        when(contextUtil.getLLMContext(draft)).thenReturn("");

        aiService.getAiFeedback("", "draft text");

        verify(journalService).buildDraftEntry("draft text");
        verify(journalService, never()).getOwnedJournalEntry(anyString());
        verify(contextUtil).getUserContext("alice");
    }

    @Test
    void anotherUsersEntry_failsBeforeCallingOpenAI() throws IOException {
        when(journalService.getOwnedJournalEntry("bobs")).thenThrow(new ResourceNotFoundException("nope"));

        assertThatThrownBy(() -> aiService.getAiFeedback("bobs", null))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(openAIClient, never()).sendChatCompletion(any(), anyString(), anyInt(), anyDouble());
    }

    @Test
    void nothingToReview_isRejected() {
        assertThatThrownBy(() -> aiService.getAiFeedback(null, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
