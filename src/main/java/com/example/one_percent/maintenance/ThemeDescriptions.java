package com.example.one_percent.maintenance;

import java.util.Map;

/**
 * The themes the app can spot in a journal, each described as a short first-person passage. An
 * entry is matched to the themes whose passage it most resembles (by embedding similarity), so
 * these passages, not the theme names, decide what counts as e.g. "burnout".
 *
 * Their vectors live in src/main/resources/themes/<embedding model>.json. After editing a
 * passage, regenerate them: scripts/dev-run.sh --maintenance.task=generate-themes
 */
public final class ThemeDescriptions {

    private ThemeDescriptions() {
    }

    public static final Map<String, String> ALL = Map.of(
            "procrastination", "I know I need to start, but something always pulls me away — a video, a scroll, a snack. It’s like my brain finds any excuse to avoid the thing I should be doing. I’m not lazy, but starting feels heavy. Maybe I’m afraid of failing or just overwhelmed. Either way, I push things off and then spiral into guilt.",
            "self-doubt", "Even when I try, there’s a voice in the back of my head that says, “You’re not good enough.” I question every decision, reread messages before sending, and second-guess myself constantly. It’s exhausting. Sometimes I wonder if people can see how unsure I feel inside, even when I pretend to have it all together.",
            "gratitude", "Today, I paused. I noticed the way the sun felt on my face and the way my friend made me laugh. I don’t always stop to be thankful, but when I do, it softens everything. Even small things — a warm drink, a kind word — remind me I’m okay, even just for a moment.",
            "discipline", "I don’t always feel like doing the hard thing, but I try to show up anyway. Discipline, to me, isn’t about being perfect — it’s about building momentum when motivation fades. It’s choosing structure when my mind feels messy and trusting that consistency, not intensity, will carry me through.",
            "anxiety", "There’s a tightness in my chest that shows up before anything goes wrong. My thoughts race ahead — what if I mess up? What if I forget something important? Even when things seem fine, my body feels alert, braced for something. It’s exhausting living on edge, trying to calm a storm that no one else can see.",
            "burnout", "Lately, I’ve felt numb. I’m showing up but not really present. Tasks that used to feel doable now feel overwhelming. I’m tired in a way that rest doesn’t fix. It’s like I’ve been pushing so hard for so long that now, I don’t have anything left to give — not even to myself.",
            "confidence", "There are moments when I really believe in myself — when I take initiative, speak up, or make a tough decision and it pays off. Confidence feels like clarity, like I’m standing on solid ground. But it’s not always there. I’ve had to build it slowly, moment by moment, learning to trust that I can handle more than I think.",
            "emotional regulation", "My emotions don’t always come in soft — sometimes they crash. I’ve been learning to pause, to notice what I feel without letting it take over. Some days, it works. I breathe, journal, or take a walk. Other days, I lose my footing. But I’m trying — to respond, not react.",
            "perfectionism", "I rewrite the same sentence five times. I delay starting because I’m afraid it won’t be perfect. There’s this pressure to get it right the first time, like mistakes aren’t allowed. I know it slows me down — sometimes it stops me altogether. But letting go of control feels just as hard.",
            "motivation", "Some days I wake up with a spark. I feel connected to what I’m doing — like it matters. But other days, it’s gone. I’m still learning how to find my way back on the slow days — to take small steps, create meaning, and remember why I started."
    );
}
