package com.example.one_percent.controller.insights;

import com.example.one_percent.dto.ThemeInsightsDTO;
import com.example.one_percent.service.insights.InsightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class InsightsController {

    private final InsightsService insightsService;

    /** The themes the logged-in user writes about most, with how often and recent examples. */
    @GetMapping("/themes")
    public ResponseEntity<ThemeInsightsDTO> themes() {
        return ResponseEntity.ok(insightsService.getThemeInsights());
    }
}
