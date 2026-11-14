package com.example.one_percent.controller.ai;

import com.example.one_percent.exception.ResourceNotFoundException;
import com.example.one_percent.model.User;
import com.example.one_percent.service.ai.PromptService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/ai/prompt")
@RequiredArgsConstructor
public class PromptController {

    private final PromptService promptService;

    /** Journaling prompts personalised for the logged-in user. */
    @GetMapping
    public List<String> getMyPrompts(@AuthenticationPrincipal User user) throws Exception {
        return promptService.getPromptSuggestions(user.getId());
    }

    /**
     * Older route still used by the mobile app. The ID in the path must be your own: prompts are
     * built from a user's habits and journal themes, so this used to leak other users' data.
     */
    @GetMapping("/{userId}")
    public List<String> getPrompts(@PathVariable String userId, @AuthenticationPrincipal User user) throws Exception {
        if (!user.getId().equals(userId)) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }
        return promptService.getPromptSuggestions(user.getId());
    }

}
