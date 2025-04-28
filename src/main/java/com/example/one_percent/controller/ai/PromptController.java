package com.example.one_percent.controller.ai;

import com.example.one_percent.service.ai.PromptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/ai/prompt")
@CrossOrigin
@RequiredArgsConstructor
public class PromptController {

    private final PromptService promptService;

    @GetMapping("/{userId}")
    public List<String> getPrompts(@PathVariable String userId) throws Exception {
        return promptService.getPromptSuggestions(userId);

    }

}
