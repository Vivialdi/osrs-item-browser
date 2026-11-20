package com.osr.jei.requirements;

import com.osr.jei.model.RecipeInfo;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Caches real skill levels and the quest states the open item actually names.
 * Levels are read with {@code getRealSkillLevel} on the client thread and refreshed
 * from {@code StatChanged}. Quest states use {@code Quest.getState} on the client
 * thread, only for names that map exactly, and only while one is on screen.
 */
@Singleton
public class PlayerProgress {

    public interface Listener {
        void onSnapshot(ProgressSnapshot snapshot);
    }

    private final Client client;
    private final ClientThread clientThread;
    private final Map<String, Quest> questsByKey;
    private final Map<String, String> officialByKey;

    private volatile ProgressSnapshot snapshot = ProgressSnapshot.LOGGED_OUT;
    private volatile Listener listener;
    private volatile Set<String> watched = Collections.emptySet();
    private static final long QUEST_REFRESH_MS = 2000L;

    private volatile boolean questDirty;
    private final AtomicBoolean questReadQueued = new AtomicBoolean();
    private long lastQuestReadMs;

    @Inject
    public PlayerProgress(Client client, ClientThread clientThread) {
        this.client = client;
        this.clientThread = clientThread;
        this.questsByKey = indexQuests();
        Map<String, String> names = new HashMap<>();
        for (Map.Entry<String, Quest> entry : questsByKey.entrySet()) {
            names.put(entry.getKey(), entry.getValue().getName());
        }
        this.officialByKey = names;
    }

    public ProgressSnapshot snapshot() {
        return snapshot;
    }

    public Mark skill(String name, int level, boolean judge) {
        return RequirementJudge.skill(name, level, snapshot, judge);
    }

    public Mark quest(String name, boolean judge) {
        return RequirementJudge.quest(name, officialByKey, snapshot, judge);
    }

    public Mark recipe(RecipeInfo recipe, boolean judge) {
        return RequirementJudge.recipe(recipe, snapshot, judge);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Names as written on the wiki. Unknown names are not queried and stay neutral. */
    public void watch(Collection<String> wikiNames) {
        Set<String> next = new LinkedHashSet<>();
        if (wikiNames != null) {
            for (String name : wikiNames) {
                String official = QuestNames.canonical(officialByKey, name);
                if (official != null) {
                    next.add(QuestNames.normalize(official));
                }
            }
        }
        boolean changed = !next.equals(watched);
        watched = Collections.unmodifiableSet(next);
        if (changed) {
            questDirty = true;
        }
        if (questDirty && !watched.isEmpty()) {
            scheduleQuestRead();
        }
    }

    /** Client-thread entry from {@code StatChanged} and login changes. */
    public void refreshSkills() {
        if (!client.isClientThread()) {
            clientThread.invokeLater(this::refreshSkills);
            return;
        }
        boolean loggedIn = loggedIn();
        Map<String, Integer> skills = loggedIn ? readLevels() : Collections.<String, Integer>emptyMap();
        Map<String, QuestProgress> quests = loggedIn ? snapshot.quests : Collections.<String, QuestProgress>emptyMap();
        publish(new ProgressSnapshot(loggedIn, skills, quests));
        if (loggedIn) {
            readQuestsIfDirty();
        }
    }

    /**
     * A varbit changed. The flag is drained on the next game tick so
     * {@code Quest.getState} is not called from inside the varbit event.
     */
    public void markQuestsDirty() {
        if (watched.isEmpty()) {
            return;
        }
        questDirty = true;
    }

    /**
     * Drained from the game tick. A new watched set still reads immediately via
     * {@link #watch}; this path only picks up later varbit changes, and at most
     * once every two seconds so {@code Quest.getState} is not run every tick.
     */
    public void readQuestsIfDirty() {
        if (!questDirty || watched.isEmpty()) {
            return;
        }
        if (System.currentTimeMillis() - lastQuestReadMs < QUEST_REFRESH_MS) {
            return;
        }
        scheduleQuestRead();
    }

    private void scheduleQuestRead() {
        if (!questReadQueued.compareAndSet(false, true)) {
            return;
        }
        clientThread.invokeLater(this::readQuests);
    }

    private void readQuests() {
        questReadQueued.set(false);
        if (!client.isClientThread()) {
            scheduleQuestRead();
            return;
        }
        if (!questDirty || watched.isEmpty()) {
            return;
        }
        if (!loggedIn()) {
            questDirty = false;
            publish(new ProgressSnapshot(false, Collections.<String, Integer>emptyMap(), Collections.<String, QuestProgress>emptyMap()));
            return;
        }
        questDirty = false;
        lastQuestReadMs = System.currentTimeMillis();
        Map<String, QuestProgress> states = new LinkedHashMap<>();
        for (String key : watched) {
            Quest quest = questsByKey.get(key);
            if (quest == null) {
                continue;
            }
            try {
                states.put(key, fromState(quest.getState(client)));
            } catch (RuntimeException ignored) {
                // Leave it absent so the row stays neutral rather than met.
            }
        }
        Map<String, Integer> skills = snapshot.loggedIn ? snapshot.skills : readLevels();
        publish(new ProgressSnapshot(true, skills, states));
    }

    private void publish(ProgressSnapshot next) {
        if (next.equals(snapshot)) {
            return;
        }
        snapshot = next;
        Listener current = listener;
        if (current != null) {
            current.onSnapshot(next);
        }
    }

    private Map<String, Integer> readLevels() {
        Map<String, Integer> levels = new LinkedHashMap<>();
        for (Skill skill : Skill.values()) {
            if (skill == null || skill.getName() == null) {
                continue;
            }
            try {
                levels.put(skill.getName(), client.getRealSkillLevel(skill));
            } catch (RuntimeException ignored) {
                // One unreadable skill stays neutral.
            }
        }
        return levels;
    }

    private boolean loggedIn() {
        try {
            return client.getGameState() == GameState.LOGGED_IN;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static Map<String, Quest> indexQuests() {
        Map<String, Quest> unique = new HashMap<>();
        Set<String> ambiguous = new java.util.HashSet<>();
        for (Quest quest : Quest.values()) {
            if (quest == null || quest.getName() == null) {
                continue;
            }
            String key = QuestNames.normalize(quest.getName());
            if (key.isEmpty()) {
                continue;
            }
            if (unique.containsKey(key) || ambiguous.contains(key)) {
                unique.remove(key);
                ambiguous.add(key);
            } else {
                unique.put(key, quest);
            }
        }
        return unique;
    }

    private static QuestProgress fromState(QuestState state) {
        if (state == QuestState.FINISHED) {
            return QuestProgress.FINISHED;
        }
        if (state == QuestState.IN_PROGRESS) {
            return QuestProgress.IN_PROGRESS;
        }
        return QuestProgress.NOT_STARTED;
    }
}
