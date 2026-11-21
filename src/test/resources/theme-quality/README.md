# Theme quality check

Labelled journal entries for `ThemeQualityIT`, which scores theme detection against the real AI service.

- `direct.json`: 50 entries: 40 clearly about one theme, 6 about two, 4 everyday entries with no theme.
- `implied.json`: 20 entries where the theme is implied, never named (e.g. burnout shown as missed
  birthdays and cancelled plans).

None of these may be used as examples in `src/main/resources/themes/definitions.json`, or the check
would be marking its own homework.

Run it (real API calls, so never in CI):

```bash
RUN_INTEGRATION_TESTS=true mvn -Dtest=ThemeQualityIT test
```
