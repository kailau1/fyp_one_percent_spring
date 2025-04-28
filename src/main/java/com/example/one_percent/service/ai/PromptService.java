package com.example.one_percent.service.ai;

import com.example.one_percent.service.client.OpenAIClient;
import com.example.one_percent.util.ContextUtil;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class PromptService {

    private final OpenAIClient openAIClient;
    private final ContextUtil contextUtil;

    public List<String> getPromptSuggestions(String userId) throws IOException {
        System.out.println("[getPromptSuggestions] Starting for userId: " + userId);

        String context = contextUtil.getUserContext(userId);
        System.out.println("[getPromptSuggestions] User context: " + context);

        String promptInstruction = context.isEmpty()
                ? "Generate 6–8 unique journaling prompts to help users reflect on their self-development."
                : "Generate 6–8 unique journaling prompts to help users reflect on their self-development, specifically around: " + context;
        System.out.println("[getPromptSuggestions] Prompt instruction: " + promptInstruction);

        ObjectNode systemMessage = openAIClient.createSingleMessage("system",
                "You are a journaling prompt generator. Given context or themes, generate 6–8 unique, non-repetitive prompts for self-reflection. Do not include numbering. Just return the prompts, each on a new line. Make them open-ended, thought-provoking, and varied.");
        ObjectNode userMessage = openAIClient.createSingleMessage("user", promptInstruction);

        ArrayNode messages = openAIClient.createMessages(List.of(systemMessage, userMessage));
        System.out.println("[getPromptSuggestions] === LLM REQUEST PAYLOAD === " + messages);

        String result = openAIClient.sendChatCompletion(messages, "gpt-4o", 300, 0.8);
        System.out.println("[getPromptSuggestions] LLM result raw: " + result);

        List<String> prompts = Arrays.stream(result.split("\\n\\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toList());

        System.out.println("[getPromptSuggestions] Final generated prompts: " + prompts);

        return prompts;
    }

}