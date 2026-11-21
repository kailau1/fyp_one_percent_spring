package com.example.one_percent.service.memory;

import com.example.one_percent.dto.AiContextDTO;
import com.example.one_percent.exception.ResourceNotFoundException;
import com.example.one_percent.model.User;
import com.example.one_percent.repository.UserRepository;
import com.example.one_percent.security.CurrentUser;
import com.example.one_percent.util.ContextUtil;
import com.example.one_percent.util.UserClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * "What One Percent knows about you": shows the logged-in user the context the AI is given, and
 * lets them add or remove facts and switch kinds of context off. Always the current user's own.
 */
@Service
@RequiredArgsConstructor
public class AiMemoryService {

    public static final int MAX_FACTS = 20;
    public static final int MAX_FACT_LENGTH = 200;

    private final ContextUtil contextUtil;
    private final UserRepository userRepository;
    private final UserClock userClock;

    public AiContextDTO getContext() {
        ContextUtil.UserContext ctx = contextUtil.buildUserContext(CurrentUser.get().getId());
        return new AiContextDTO(
                ctx.habits(),
                ctx.themes(),
                ctx.facts().stream().map(f -> new AiContextDTO.Fact(f.getId(), f.getText(), f.getCreatedAt())).toList(),
                new AiContextDTO.Settings(ctx.useHabits(), ctx.useThemes(), ctx.useFacts(), ctx.usePastAdvice()),
                contextUtil.render(ctx),
                MAX_FACTS,
                MAX_FACT_LENGTH);
    }

    public AiContextDTO.Fact addFact(String text) {
        String clean = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("Write something to add.");
        }
        if (clean.length() > MAX_FACT_LENGTH) {
            throw new IllegalArgumentException("Keep it under " + MAX_FACT_LENGTH + " characters.");
        }
        User.AboutMeFact fact = new User.AboutMeFact(UUID.randomUUID().toString(), clean, userClock.now());
        if (!userRepository.addFact(CurrentUser.get().getId(), fact, MAX_FACTS)) {
            throw new IllegalArgumentException("You can keep up to " + MAX_FACTS + " things here. Delete one to add another.");
        }
        return new AiContextDTO.Fact(fact.getId(), fact.getText(), fact.getCreatedAt());
    }

    public void removeFact(String factId) {
        if (!userRepository.removeFact(CurrentUser.get().getId(), factId)) {
            throw new ResourceNotFoundException("No such fact: " + factId);
        }
    }

    /** Sets all four switches at once (a missing one counts as off). Returns the context as it now stands. */
    public AiContextDTO updateSettings(AiContextDTO.Settings settings) {
        if (settings == null) throw new IllegalArgumentException("Send the four switches.");
        userRepository.updateAiContext(CurrentUser.get().getId(), new User.AiContextSettings(
                settings.isUseHabits(), settings.isUseThemes(), settings.isUseFacts(), settings.isUsePastAdvice()));
        return getContext();
    }
}
