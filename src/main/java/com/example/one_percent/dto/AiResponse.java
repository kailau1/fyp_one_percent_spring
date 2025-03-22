package com.example.one_percent.dto;

public class AiResponse {
    private String feedback;

    public AiResponse(String feedback) {
        this.feedback = feedback;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }
}
