package com.example.one_percent.controller.reflection;

import com.example.one_percent.dto.PageDTO;
import com.example.one_percent.dto.WeeklyReflectionDTO;
import com.example.one_percent.service.reflection.ReflectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** The logged-in user's weekly reflections. */
@RestController
@RequestMapping("/api/reflections")
@RequiredArgsConstructor
public class ReflectionController {

    private final ReflectionService reflectionService;

    /**
     * Last finished week's reflection, written on first request (one AI call; can take a few
     * seconds), or status "notEnoughEntries". 503 if the AI couldn't write it.
     */
    @GetMapping("/latest")
    public ResponseEntity<WeeklyReflectionDTO> latest() {
        return ResponseEntity.ok(reflectionService.latest());
    }

    /** Reflections already written, newest week first. */
    @GetMapping
    public ResponseEntity<PageDTO<WeeklyReflectionDTO>> history(@RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(reflectionService.history(page, size));
    }

    /** One week (by its Monday, e.g. 2026-11-09), or 404. */
    @GetMapping("/{weekStart}")
    public ResponseEntity<WeeklyReflectionDTO> week(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        return ResponseEntity.ok(reflectionService.forWeek(weekStart));
    }
}
