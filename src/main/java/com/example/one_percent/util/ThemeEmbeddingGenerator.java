package com.example.one_percent.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.io.IOException;
import java.util.*;



public class ThemeEmbeddingGenerator {


    private static final String OPENAI_EMBEDDING_URL = "https://api.openai.com/v1/embeddings";

    private static final OkHttpClient client = new OkHttpClient();

    private static final Map<String, String> themeDescriptions = Map.of(
            "procrastination", "I know I need to start, but something always pulls me away — a video, a scroll, a snack. It’s like my brain finds any excuse to avoid the thing I should be doing. I’m not lazy, but starting feels heavy. Maybe I’m afraid of failing or just overwhelmed. Either way, I push things off and then spiral into guilt.",
            "self-doubt", "Even when I try, there’s a voice in the back of my head that says, “You’re not good enough.” I question every decision, reread messages before sending, and second-guess myself constantly. It’s exhausting. Sometimes I wonder if people can see how unsure I feel inside, even when I pretend to have it all together.",
            "gratitude", "Today, I paused. I noticed the way the sun felt on my face and the way my friend made me laugh. I don’t always stop to be thankful, but when I do, it softens everything. Even small things — a warm drink, a kind word — remind me I’m okay, even just for a moment.",
            "discipline", "I don’t always feel like doing the hard thing, but I try to show up anyway. Discipline, to me, isn’t about being perfect — it’s about building momentum when motivation fades. It’s choosing structure when my mind feels messy and trusting that consistency, not intensity, will carry me through.",
            "anxiety", "There’s a tightness in my chest that shows up before anything goes wrong. My thoughts race ahead — what if I mess up? What if I forget something important? Even when things seem fine, my body feels alert, braced for something. It’s exhausting living on edge, trying to calm a storm that no one else can see.",
            "burnout", "Lately, I’ve felt numb. I’m showing up but not really present. Tasks that used to feel doable now feel overwhelming. I’m tired in a way that rest doesn’t fix. It’s like I’ve been pushing so hard for so long that now, I don’t have anything left to give — not even to myself.",
            "confidence", "There are moments when I really believe in myself — when I take initiative, speak up, or make a tough decision and it pays off. Confidence feels like clarity, like I’m standing on solid ground. But it’s not always there. I’ve had to build it slowly, moment by moment, learning to trust that I can handle more than I think.",
            "emotional regulation", "My emotions don’t always come in soft — sometimes they crash. I’ve been learning to pause, to notice what I feel without letting it take over. Some days, it works. I breathe, journal, or take a walk. Other days, I lose my footing. But I’m trying — to respond, not react.",
            "perfectionism", "I rewrite the same sentence five times. I delay starting because I’m afraid it won’t be perfect. There’s this pressure to get it right the first time, like mistakes aren’t allowed. I know it slows me down — sometimes it stops me altogether. But letting go of control feels just as hard.",
            "motivation", "Some days I wake up with a spark. I feel connected to what I’m doing — like it matters. But other days, it’s gone. I’m still learning how to find my way back on the slow days — to take small steps, create meaning, and remember why I started."
    );

    public static void main(String[] args) throws IOException {


        ObjectMapper mapper = new ObjectMapper();
        Map<String, List<Double>> embeddingsMap = new HashMap<>();

        for (Map.Entry<String, String> entry : themeDescriptions.entrySet()) {
            String theme = entry.getKey();
            String description = entry.getValue();
            List<Double> embedding = generateEmbedding(description);
            embeddingsMap.put(theme, embedding);
            System.out.println("[INFO] Generated embedding for: " + theme);
        }

        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(new File("themes.json"), embeddingsMap);

        System.out.println("themes.json generated successfully.");
    }

    private static List<Double> generateEmbedding(String inputText) throws IOException {
        String requestBody = String.format("""
    {
      "model": "text-embedding-3-small",
      "input": "%s"
    }
    """, inputText.replace("\"", "\\\""));

        Request request = new Request.Builder()
                .url(OPENAI_EMBEDDING_URL)
                .addHeader("Authorization", "Bearer " + "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") // your key
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            if (!response.isSuccessful()) {
                throw new RuntimeException("Embedding API failed: " + responseBody);
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode embeddingNode = mapper.readTree(responseBody)
                    .get("data").get(0).get("embedding");

            List<Double> embedding = new ArrayList<>();
            for (JsonNode val : embeddingNode) {
                embedding.add(val.asDouble());
            }
            return embedding;
        }
    }
}
