package com.example.one_percent.service.ai;

import com.example.one_percent.service.client.OpenAIClient;
import com.example.one_percent.model.JournalEntry;
import com.example.one_percent.repository.JournalRepository;
import com.example.one_percent.service.embedding.EmbeddingService;
import com.example.one_percent.service.journal.JournalService;
import com.example.one_percent.util.ContextUtil;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
@Service
public class AiService {

    private final OpenAIClient openAIClient;
    private final JournalService journalService;
    private final ContextUtil contextUtil;
    private final JournalRepository journalRepository;

   public String getAiFeedback(String journalId, String content, String userId) throws IOException {
        JournalEntry journal;

        if (journalId != null && !journalId.isEmpty()) {
            journal = journalRepository.findById(journalId)
                    .orElseThrow(() -> new IllegalArgumentException("Journal not found for ID: " + journalId));
        } else if (content != null && !content.isBlank()) {
            journal = journalService.saveAndProcessJournal(content, userId);
        } else {
            throw new IllegalArgumentException("Either journalId or content must be provided.");
        }

        String journalContent = journal.getContent();
        String userContext = contextUtil.getUserContext(userId);
        String llmContext = contextUtil.getLLMContext(journal);
        String fullUserInput = userContext + "\n\nJournal Entry:\n" + journalContent + "\n\n" + llmContext;

        ObjectNode systemMessage = openAIClient.createSingleMessage("system",
                """
                 You are a thoughtful and emotionally attuned AI coach responding to a user's journal entry.
                                         
                 Your feedback should feel warm and personal — as if you're speaking directly to the user. 
                  Avoid rigid structure or bullet points. Also avoid generic affirmations or scripted positivity. Instead, reflect with insight, care, and clarity.
                                         
                 Pay close attention to what the user is really saying — their frustrations, hesitations, emotional highs and lows. Reflect these moments with honesty and empathy. Don't gloss over struggle with surface-level affirmations or forced positivity.
                                         
                 Where the user expresses difficulty, suggest one practical, psychologically sound strategy. Avoid generic advice or encouragement. Aim to go beyond surface-level observations and offer something that genuinely deepens the user’s reflection or supports meaningful action.
                 If they’ve clearly proposed a strategy themselves, you may deepen it — but don’t overextend casual remarks. 
                 
                 You may bring in the user's habits if they meaningfully support your suggestion — but your advice should stand on its own, offering a new perspective or strategy the user hasn't clearly tried.                 
                 Adapt your tone to their state of mind — calm and grounded if they seem anxious, focused and motivating if they seem determined. 
                                         
                 Word limit: 140.
                """);
        ObjectNode userMessage = openAIClient.createSingleMessage("user", fullUserInput);
        ArrayNode messages = openAIClient.createMessages(List.of(systemMessage, userMessage));

        System.out.println("=== LLM REQUEST PAYLOAD ===");
        System.out.println(messages.toPrettyString());

        return openAIClient.sendChatCompletion(messages, "gpt-4o", 200, 0.7);
    }

    public String getDailyTip() throws IOException {
        ObjectNode systemMessage = openAIClient.createSingleMessage("system",
                "You are a helpful and inspiring assistant that provides concise, motivational self-improvement tips for users working on personal growth, habit-building, and positive psychology.\n\n" +
                        "Your tips should be:\n\n" +
                        "- Original and non-generic (avoid clichés like \"start small\" or \"be consistent\")\n" +
                        "- Focused on actionable strategies, fresh mindsets, or lesser-known psychological principles\n" +
                        "- Friendly, warm, and encouraging in tone\n" +
                        "- 1–2 sentences max\n" +
                        "- Don't include \"Tip of the Day\" or greetings. Just return the tip.\n\n" +
                        "Do NOT repeat previously suggested ideas like:\n" +
                        "- \"Start small and be consistent\"\n" +
                        "- \"Drink water in the morning\"\n" +
                        "- \"Read for five minutes daily\"\n" +
                        "- \"Celebrate small wins\"\n\n" +
                        "Here are examples of the kind of variety and voice we're aiming for:\n\n" +
                        "- \"Make your environment your ally. Leave your journal on your pillow to remind yourself to write before bed.\"\n" +
                        "- \"Don’t break the chain. Use a simple calendar to mark off each day you stick to your habit.\"\n" +
                        "- \"If your mornings feel rushed, prepare your clothes and to-do list the night before. Clarity lowers resistance.\"\n" +
                        "- \"Self-kindness fuels growth. When you slip up, talk to yourself like you would a close friend.\"\n" +
                        "- \"Don’t rely on motivation—build frictionless systems that make the right choice the easiest one.\"\n\n" +
                        "Now, generate a **new, unique self-improvement tip** following the same style.");

        ObjectNode userMessage = openAIClient.createSingleMessage("user",
                "Give me one tip of the day to help with self-development and building better habits.");

        ArrayNode messages = openAIClient.createMessages(List.of(systemMessage, userMessage));

        return openAIClient.sendChatCompletion(messages, "gpt-4o-mini", 60, 0.8);
    }
}