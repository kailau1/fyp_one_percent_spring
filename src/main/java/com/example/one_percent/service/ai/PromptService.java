package com.example.one_percent.service.ai;

import com.example.one_percent.service.client.OpenAIClient;
import com.example.one_percent.util.ContextUtil;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class PromptService {

    private static final Logger logger = LoggerFactory.getLogger(PromptService.class);

    private final OpenAIClient openAIClient;
    private final ContextUtil contextUtil;

    public List<String> getPromptSuggestions(String userId) throws IOException {
        String context = contextUtil.getUserContext(userId);

        String promptInstruction = context.isEmpty()
                ? "Generate 6–8 unique journaling prompts to help users reflect on their self-development."
                : "Generate 6–8 unique journaling prompts to help users reflect on their self-development, specifically around: " + context;

        ObjectNode systemMessage = openAIClient.createSingleMessage("system",
                "You are a journaling prompt generator. Given context or themes, generate 6–8 unique, non-repetitive prompts for self-reflection. Do not include numbering. Just return the prompts, each on a new line. Make them open-ended, thought-provoking, and varied.");
        ObjectNode userMessage = openAIClient.createSingleMessage("user", promptInstruction);

        ArrayNode messages = openAIClient.createMessages(List.of(systemMessage, userMessage));
        logger.debug("Prompt suggestion request: {}", messages);

        String result = openAIClient.sendChatCompletion(messages, "gpt-4o", 300, 0.8);
        List<String> prompts = parsePrompts(result);
        logger.debug("Generated {} prompts", prompts.size());

        return prompts;
    }

    /**
     * Turns the model's reply into one prompt per list item. The model is asked for one prompt per
     * line, but sometimes separates them with blank lines, numbers them or adds bullets anyway.
     * (Splitting only on blank lines used to return the whole reply as a single prompt.)
     */
    static List<String> parsePrompts(String llmReply) {
        if (llmReply == null) return List.of();

        return Arrays.stream(llmReply.split("\\R+"))
                .map(line -> line.replaceFirst("^\\s*(?:[-*•]+|\\d+[.)])\\s*", ""))  // "1. ", "2) ", "- ", "• "
                .map(line -> line.replaceAll("^[\"“]|[\"”]$", ""))                   // wrapping quotes
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toList());
    }

}
