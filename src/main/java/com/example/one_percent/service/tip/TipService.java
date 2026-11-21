package com.example.one_percent.service.tip;

import com.example.one_percent.model.DailyTip;
import com.example.one_percent.model.User;
import com.example.one_percent.repository.DailyTipRepository;
import com.example.one_percent.service.ai.AiService;
import com.example.one_percent.util.UserClock;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;

/**
 * The tip of the day. It isn't personalised, so it is generated once per calendar day and shared:
 * one AI call a day in total, instead of one per user per page load. Stored in MongoDB, so a
 * restart doesn't pay for it again.
 *
 * "Today" is the user's own calendar day, so the tip changes at their midnight.
 */
@Service
@RequiredArgsConstructor
public class TipService {

    private static final Logger logger = LoggerFactory.getLogger(TipService.class);

    private final DailyTipRepository dailyTipRepository;
    private final AiService aiService;
    private final UserClock userClock;

    public String getTipOfTheDay(User user) throws IOException {
        return getTipFor(userClock.today(user));
    }

    /**
     * The stored tip for `day`, generating and storing it the first time it's asked for.
     * A failed AI call stores nothing (the error propagates), so the next request tries again.
     */
    public String getTipFor(LocalDate day) throws IOException {
        String key = day.toString();
        var stored = dailyTipRepository.findById(key);
        if (stored.isPresent()) {
            return stored.get().getTip();
        }

        String tip = aiService.getDailyTip();
        try {
            // insert, not save: if another request stored today's tip in the meantime this fails
            // instead of overwriting it, so everyone sees the same tip.
            dailyTipRepository.insert(new DailyTip(key, tip, userClock.now()));
            return tip;
        } catch (DuplicateKeyException alreadyStored) {
            logger.debug("Tip for {} was stored by a concurrent request; using that one", key);
            return dailyTipRepository.findById(key).map(DailyTip::getTip).orElse(tip);
        }
    }
}
