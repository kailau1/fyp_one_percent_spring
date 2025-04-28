package com.example.one_percent.service.ai;

import com.example.one_percent.service.client.OpenAIClient;
import com.example.one_percent.model.JournalEntry;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SummaryService {

    private final OpenAIClient openAIClient;

    public String getJournalSummary(JournalEntry entry) throws IOException {
        ObjectNode systemMessage = openAIClient.createSingleMessage("system",
                "You are a summarisation assistant. Given a user's journal entry, generate a short, thoughtful summary of their key reflections. Highlight any challenges, emotions, or insights they've shared. Be neutral and clear. Word limit: 40 words.");

        ObjectNode userMessage = openAIClient.createSingleMessage("user", entry.getContent());
        ArrayNode messages = openAIClient.createMessages(List.of(systemMessage, userMessage));

        return openAIClient.sendChatCompletion(messages, "gpt-4o-mini", 100, 0.6);
    }

    public String getFeedbackSummary(String feedbackText) throws IOException {
        ObjectNode systemMessage = openAIClient.createSingleMessage("system",
                "You are a summarisation assistant. Summarise this AI feedback in 1–2 sentences, focusing on the  advice given, and write the specific self-development theories / techniques metnioned. Be concise and clear. Max words: 25");

        ObjectNode userMessage = openAIClient.createSingleMessage("user", feedbackText);
        ArrayNode messages = openAIClient.createMessages(List.of(systemMessage, userMessage));

        return openAIClient.sendChatCompletion(messages, "gpt-4o-mini", 50, 0.6);
    }

}
