package com.example.one_percent.service.client;

/** What a chat request needs from a model; AiSettings maps each to a real model name. */
public enum AiModel {
    /** Work the user reads closely: journal feedback, writing prompts. */
    SMART,
    /** Short, cheap jobs: the tip of the day, feedback summaries. */
    FAST
}
