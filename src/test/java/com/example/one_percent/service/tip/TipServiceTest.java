package com.example.one_percent.service.tip;

import com.example.one_percent.model.DailyTip;
import com.example.one_percent.model.User;
import com.example.one_percent.repository.DailyTipRepository;
import com.example.one_percent.service.ai.AiService;
import com.example.one_percent.util.UserClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TipServiceTest {

    // 23:30 UTC on 20 Nov is already 21 Nov in Tokyo and still 20 Nov in London
    private static final Instant NOW = Instant.parse("2026-11-20T23:30:00Z");

    @Mock
    private DailyTipRepository repository;
    @Mock
    private AiService aiService;

    private TipService tipService;

    @BeforeEach
    void setUp() {
        UserClock clock = new UserClock(Clock.fixed(NOW, ZoneOffset.UTC));
        tipService = new TipService(repository, aiService, clock);
    }

    private static User userIn(String zone) {
        User user = new User();
        user.setId("u1");
        user.setTimeZone(zone);
        return user;
    }

    @Test
    void reusesTodaysStoredTipWithoutCallingTheAi() throws IOException {
        when(repository.findById("2026-11-21")).thenReturn(Optional.of(new DailyTip("2026-11-21", "stored tip", NOW)));

        assertThat(tipService.getTipFor(LocalDate.of(2026, 11, 21))).isEqualTo("stored tip");
        verifyNoInteractions(aiService);
        verify(repository, never()).insert(any(DailyTip.class));
    }

    @Test
    void generatesAndStoresTheTipTheFirstTimeADayIsAskedFor() throws IOException {
        when(repository.findById("2026-11-21")).thenReturn(Optional.empty());
        when(aiService.getDailyTip()).thenReturn("fresh tip");

        assertThat(tipService.getTipFor(LocalDate.of(2026, 11, 21))).isEqualTo("fresh tip");

        ArgumentCaptor<DailyTip> stored = ArgumentCaptor.forClass(DailyTip.class);
        verify(repository).insert(stored.capture());
        assertThat(stored.getValue().getDay()).isEqualTo("2026-11-21");
        assertThat(stored.getValue().getTip()).isEqualTo("fresh tip");
        assertThat(stored.getValue().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void whenAnotherRequestStoredTheTipFirstEveryoneGetsThatOne() throws IOException {
        when(repository.findById("2026-11-21"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new DailyTip("2026-11-21", "the winner's tip", NOW)));
        when(aiService.getDailyTip()).thenReturn("my tip");
        when(repository.insert(any(DailyTip.class))).thenThrow(new DuplicateKeyException("duplicate _id"));

        assertThat(tipService.getTipFor(LocalDate.of(2026, 11, 21))).isEqualTo("the winner's tip");
    }

    @Test
    void aFailedAiCallStoresNothingSoTheNextRequestRetries() throws IOException {
        when(repository.findById(any())).thenReturn(Optional.empty());
        when(aiService.getDailyTip()).thenThrow(new IOException("OpenAI unreachable"));

        assertThatThrownBy(() -> tipService.getTipFor(LocalDate.of(2026, 11, 21))).isInstanceOf(IOException.class);
        verify(repository, never()).insert(any(DailyTip.class));
    }

    @Test
    void todayIsTheUsersOwnCalendarDay() throws IOException {
        when(repository.findById(any())).thenReturn(Optional.of(new DailyTip("x", "tip", NOW)));

        tipService.getTipOfTheDay(userIn("Asia/Tokyo"));
        tipService.getTipOfTheDay(userIn("Europe/London"));

        verify(repository).findById("2026-11-21");
        verify(repository).findById("2026-11-20");
    }
}
