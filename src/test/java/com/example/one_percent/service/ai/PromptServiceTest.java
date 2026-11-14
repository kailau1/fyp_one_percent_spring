package com.example.one_percent.service.ai;

import com.example.one_percent.service.client.OpenAIClient;
import com.example.one_percent.util.ContextUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PromptServiceTest {

    @Test
    void parsePrompts_onePerLine() {
        assertThat(PromptService.parsePrompts("What drained you today?\nWhat are you proud of?"))
                .containsExactly("What drained you today?", "What are you proud of?");
    }

    @Test
    void parsePrompts_blankLinesBetweenPrompts() {
        assertThat(PromptService.parsePrompts("First prompt?\n\nSecond prompt?\r\n\r\nThird prompt?\n"))
                .containsExactly("First prompt?", "Second prompt?", "Third prompt?");
    }

    @Test
    void parsePrompts_stripsNumberingBulletsAndQuotes() {
        String reply = "1. What went well?\n2) What didn't?\n- What surprised you?\n• What next?\n\"What matters most?\"";

        assertThat(PromptService.parsePrompts(reply)).containsExactly(
                "What went well?", "What didn't?", "What surprised you?", "What next?", "What matters most?");
    }

    @Test
    void parsePrompts_keepsNumbersAndDashesInsideAPrompt() {
        assertThat(PromptService.parsePrompts("Name 3 things you're grateful for - big or small."))
                .containsExactly("Name 3 things you're grateful for - big or small.");
    }

    @Test
    void parsePrompts_emptyOrMissingReply() {
        assertThat(PromptService.parsePrompts("")).isEmpty();
        assertThat(PromptService.parsePrompts("  \n \n")).isEmpty();
        assertThat(PromptService.parsePrompts(null)).isEmpty();
    }

    @Test
    void getPromptSuggestions_buildsOnTheUsersContext() throws IOException {
        OpenAIClient openAIClient = spy(new OpenAIClient());
        ContextUtil contextUtil = mock(ContextUtil.class);
        when(contextUtil.getUserContext("alice")).thenReturn("The user often writes about: burnout.");
        doReturn("One?\nTwo?").when(openAIClient).sendChatCompletion(
                argThat(messages -> messages.toString().contains("burnout")), eq("gpt-4o"), anyInt(), anyDouble());

        List<String> prompts = new PromptService(openAIClient, contextUtil).getPromptSuggestions("alice");

        assertThat(prompts).containsExactly("One?", "Two?");
    }
}
