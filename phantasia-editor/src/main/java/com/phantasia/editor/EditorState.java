// phantasia-editor/src/main/java/com/phantasia/editor/EditorState.java
package com.phantasia.editor;

import com.phantasia.core.data.*;
import com.phantasia.core.model.NpcDefinition;
import com.phantasia.core.model.item.ItemDefinition;
import com.phantasia.core.world.*;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central data controller for the Phantasia Editor Suite.
 *
 * <p>
 * All game data lives here while the editor is running. Editor panels
 * never write to files directly — they read from and mutate EditorState
 * through {@link EditCommand}s. The working state is serialized to disk
 * only when the designer explicitly bakes (Ctrl+Shift+B) or saves the
 * project file.
 * </p>
 *
 * <h3>Threading Model</h3>
 * <p>
 * All mutations happen on the EDT. Listeners are notified synchronously
 * on the EDT. The undo/redo stacks, dirty set, and data maps are not
 * thread-safe — this is intentional and correct for a single-designer
 * Swing application.
 * </p>
 *
 * <h3>Dirty Tracking</h3>
 * <p>
 * Each record carries a dirty key (e.g. "monster:3", "interiorMap:5").
 * The dirty set tracks which records have been modified since the last
 * bake. The status bar shows the dirty count. Undo-to-clean restores
 * a record to its last-baked state and clears its dirty flag.
 * </p>
 *
 * <h3>Project File Format</h3>
 * <p>
 * The native save format is a {@code .phantasia} ZIP archive containing
 * all baked {@code .dat} files and an {@code editor.json} metadata file.
 * See Section 11 of the design document.
 * </p>
 *
 * @see EditCommand
 * @see EditorStateListener
 */
public class EditorState {

    // =========================================================================
    // Working data — the authoritative in-memory state
    // =========================================================================

    private WorldMap worldMap;
    private final Map<Integer, TownDefinition> towns = new LinkedHashMap<>();
    private final Map<Integer, DungeonDefinition> dungeons = new LinkedHashMap<>();
    private final Map<Integer, InteriorMap> interiorMaps = new LinkedHashMap<>();
    private final Map<Integer, NpcDefinition> npcs = new LinkedHashMap<>();
    private final Map<Integer, Quest> quests = new LinkedHashMap<>();
    private final Map<Integer, ShopInventory> shops = new LinkedHashMap<>();
    private final List<byte[]> monsters = new ArrayList<>();
    private final List<ItemDefinition> items = new ArrayList<>();
    private final List<byte[]> spells = new ArrayList<>();

    // =========================================================================
    // Dirty tracking
    // =========================================================================

    /** Keys of records modified since last bake. */
    private final Set<String> dirtySet = new LinkedHashSet<>();

    /**
     * Snapshot of the dirty set at the last save/bake point.
     * Used to determine whether an undo restores a record to its clean state.
     * After a bake, this is cleared (everything is clean). When undo removes
     * the last command that dirtied a key, and that key wasn't dirty at the
     * save point, it's clean again.
     */
    private final Set<String> dirtyAtSavePoint = new HashSet<>();

    // =========================================================================
    // Undo / Redo
    // =========================================================================

    private final Deque<EditCommand> undoStack = new ArrayDeque<>();
    private final Deque<EditCommand> redoStack = new ArrayDeque<>();

    /** Maximum undo depth. Oldest commands are discarded when exceeded. */
    private int undoLimit = 100;

    // =========================================================================
    // Observers
    // =========================================================================

    private final List<EditorStateListener> listeners = new CopyOnWriteArrayList<>();

    // =========================================================================
    // Project metadata
    // =========================================================================

    /** Path to the current .phantasia project file, or null if unsaved. */
    private Path projectPath;

    /** Asset directory (sprites, music, etc.) — resolved relative to project. */
    private String assetDirectory = "../assets";

    // =========================================================================
    // Singleton access
    // =========================================================================

    private static EditorState instance;

    /** Returns the singleton EditorState. Created by {@link #initialize()}. */
    public static EditorState get() {
        if (instance == null) {
            throw new IllegalStateException(
                    "EditorState not initialized — call EditorState.initialize() first.");
        }
        return instance;
    }

    /** Creates and returns the singleton EditorState. Call once at startup. */
    public static EditorState initialize() {
        if (instance != null) {
            throw new IllegalStateException("EditorState already initialized.");
        }
        instance = new EditorState();
        return instance;
    }

    /** Package-private constructor. Use {@link #initialize()}. */
    EditorState() {
    }

    // =========================================================================
    // Command execution — the ONLY way to mutate game data
    // =========================================================================

    /**
     * Executes a command, pushes it onto the undo stack, clears the redo
     * stack, marks the affected record dirty, and notifies listeners.
     *
     * <p>
     * This is the single entry point for all data mutations. Panels
     * construct an {@link EditCommand} and pass it here — they never
     * modify data maps directly.
     * </p>
     *
     * @param cmd the command to execute
     */
    public void execute(EditCommand cmd) {
        cmd.execute();

        undoStack.push(cmd);
        redoStack.clear();
        trimUndoStack();

        markDirty(cmd.dirtyKey());
        fireDataChanged(cmd.dirtyKey());
        fireUndoRedoChanged();
    }

    /**
     * Undoes the most recent command. The command is moved from the undo
     * stack to the redo stack. If the undo restores a record to its
     * last-baked state, its dirty flag is cleared.
     *
     * @return true if a command was undone, false if the undo stack was empty
     */
    public boolean undo() {
        if (undoStack.isEmpty())
            return false;

        EditCommand cmd = undoStack.pop();
        cmd.undo();
        redoStack.push(cmd);

        // Re-evaluate dirty state for this key
        recomputeDirty(cmd.dirtyKey());
        fireDataChanged(cmd.dirtyKey());
        fireUndoRedoChanged();
        return true;
    }

    /**
     * Redoes the most recently undone command. The command is moved from
     * the redo stack back onto the undo stack.
     *
     * @return true if a command was redone, false if the redo stack was empty
     */
    public boolean redo() {
        if (redoStack.isEmpty())
            return false;

        EditCommand cmd = redoStack.pop();
        cmd.execute();
        undoStack.push(cmd);

        markDirty(cmd.dirtyKey());
        fireDataChanged(cmd.dirtyKey());
        fireUndoRedoChanged();
        return true;
    }

    /** Returns the description of the next undoable command, or null. */
    public String peekUndo() {
        return undoStack.isEmpty() ? null : undoStack.peek().description();
    }

    /** Returns the description of the next redoable command, or null. */
    public String peekRedo() {
        return redoStack.isEmpty() ? null : redoStack.peek().description();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    // =========================================================================
    // Dirty tracking
    // =========================================================================

    /** Marks a record as dirty (modified since last bake). */
    public void markDirty(String key) {
        if (key == null)
            return;
        boolean added = dirtySet.add(key);
        if (added)
            fireDirtyCountChanged();
    }

    /** Returns true if any records have unsaved modifications. */
    public boolean isDirty() {
        return !dirtySet.isEmpty();
    }

    /** Returns the number of dirty records. */
    public int getDirtyCount() {
        return dirtySet.size();
    }

    /** Returns true if a specific record is dirty. */
    public boolean isDirty(String key) {
        return dirtySet.contains(key);
    }

    /** Returns an unmodifiable view of all dirty keys. */
    public Set<String> getDirtyKeys() {
        return Collections.unmodifiableSet(dirtySet);
    }

    /**
     * Clears the dirty flag for a specific key. Called after a
     * successful per-category bake (e.g. "Bake Monsters").
     */
    public void clearDirty(String key) {
        boolean removed = dirtySet.remove(key);
        if (removed)
            fireDirtyCountChanged();
    }

    /**
     * Clears all dirty flags and snapshots the clean state.
     * Called after a full bake or project save.
     */
    public void clearAllDirty() {
        dirtySet.clear();
        dirtyAtSavePoint.clear();
        fireDirtyCountChanged();
    }

    /**
     * Re-evaluates whether a key is still dirty by scanning the undo stack.
     * If no remaining commands in the undo stack affect this key (beyond
     * the save point), the key is clean.
     */
    private void recomputeDirty(String key) {
        if (dirtyAtSavePoint.contains(key)) {
            // Was dirty at save point — stays dirty regardless of undo
            return;
        }
        // Check if any command remaining in the undo stack affects this key
        boolean stillDirty = undoStack.stream()
                .anyMatch(cmd -> key.equals(cmd.dirtyKey()));

        if (stillDirty) {
            dirtySet.add(key);
        } else {
            boolean removed = dirtySet.remove(key);
            if (removed)
                fireDirtyCountChanged();
        }
    }

    // =========================================================================
    // World Map
    // =========================================================================

    public WorldMap getWorldMap() {
        return worldMap;
    }

    public void setWorldMap(WorldMap map) {
        this.worldMap = map;
    }

    // =========================================================================
    // Interior Maps
    // =========================================================================

    public Map<Integer, InteriorMap> getInteriorMaps() {
        return Collections.unmodifiableMap(interiorMaps);
    }

    public InteriorMap getInteriorMap(int id) {
        return interiorMaps.get(id);
    }

    public void putInteriorMap(InteriorMap map) {
        interiorMaps.put(map.getId(), map);
    }

    public void removeInteriorMap(int id) {
        interiorMaps.remove(id);
    }

    // =========================================================================
    // Towns
    // =========================================================================

    public Map<Integer, TownDefinition> getTowns() {
        return Collections.unmodifiableMap(towns);
    }

    public TownDefinition getTown(int id) {
        return towns.get(id);
    }

    public void putTown(TownDefinition town) {
        towns.put(town.getId(), town);
    }

    public void removeTown(int id) {
        towns.remove(id);
    }

    // =========================================================================
    // Dungeons
    // =========================================================================

    public Map<Integer, DungeonDefinition> getDungeons() {
        return Collections.unmodifiableMap(dungeons);
    }

    public DungeonDefinition getDungeon(int id) {
        return dungeons.get(id);
    }

    public void putDungeon(DungeonDefinition dungeon) {
        dungeons.put(dungeon.getId(), dungeon);
    }

    public void removeDungeon(int id) {
        dungeons.remove(id);
    }

    // =========================================================================
    // NPCs
    // =========================================================================

    public Map<Integer, NpcDefinition> getNpcs() {
        return Collections.unmodifiableMap(npcs);
    }

    public NpcDefinition getNpc(int id) {
        return npcs.get(id);
    }

    public void putNpc(NpcDefinition npc) {
        npcs.put(npc.getId(), npc);
    }

    public void removeNpc(int id) {
        npcs.remove(id);
    }

    // =========================================================================
    // Quests
    // =========================================================================

    public Map<Integer, Quest> getQuests() {
        return Collections.unmodifiableMap(quests);
    }

    public Quest getQuest(int id) {
        return quests.get(id);
    }

    public void putQuest(Quest quest) {
        quests.put(quest.getId(), quest);
    }

    public void removeQuest(int id) {
        quests.remove(id);
    }

    // =========================================================================
    // Shops
    // =========================================================================

    public Map<Integer, ShopInventory> getShops() {
        return Collections.unmodifiableMap(shops);
    }

    public ShopInventory getShop(int id) {
        return shops.get(id);
    }

    public void putShop(ShopInventory shop) {
        shops.put(shop.getShopId(), shop);
    }

    public void removeShop(int id) {
        shops.remove(id);
    }

    // =========================================================================
    // Monsters (DataCore byte blocks)
    // =========================================================================

    public List<byte[]> getMonsters() {
        return Collections.unmodifiableList(monsters);
    }

    public byte[] getMonster(int index) {
        return (index >= 0 && index < monsters.size()) ? monsters.get(index) : null;
    }

    public void setMonster(int index, byte[] data) {
        if (index >= 0 && index < monsters.size()) {
            monsters.set(index, data);
        }
    }

    public void addMonster(byte[] data) {
        monsters.add(data);
    }

    public void removeMonster(int index) {
        if (index >= 0 && index < monsters.size()) {
            monsters.remove(index);
        }
    }

    public int getMonsterCount() {
        return monsters.size();
    }

    // =========================================================================
    // Items
    // =========================================================================

    public List<ItemDefinition> getItems() {
        return Collections.unmodifiableList(items);
    }

    public ItemDefinition getItem(int index) {
        return (index >= 0 && index < items.size()) ? items.get(index) : null;
    }

    public void setItem(int index, ItemDefinition item) {
        if (index >= 0 && index < items.size()) {
            items.set(index, item);
        }
    }

    public void addItem(ItemDefinition item) {
        items.add(item);
    }

    public void removeItem(int index) {
        if (index >= 0 && index < items.size()) {
            items.remove(index);
        }
    }

    public int getItemCount() {
        return items.size();
    }

    // =========================================================================
    // Spells (DataCore byte blocks)
    // =========================================================================

    public List<byte[]> getSpells() {
        return Collections.unmodifiableList(spells);
    }

    public byte[] getSpell(int index) {
        return (index >= 0 && index < spells.size()) ? spells.get(index) : null;
    }

    public void setSpell(int index, byte[] data) {
        if (index >= 0 && index < spells.size()) {
            spells.set(index, data);
        }
    }

    public void addSpell(byte[] data) {
        spells.add(data);
    }

    public void removeSpell(int index) {
        if (index >= 0 && index < spells.size()) {
            spells.remove(index);
        }
    }

    public int getSpellCount() {
        return spells.size();
    }

    // =========================================================================
    // ID generation — monotonic IDs for new records
    // =========================================================================

    private int nextId(Map<Integer, ?> map) {
        return map.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
    }

    public int nextInteriorMapId() {
        return nextId(interiorMaps);
    }

    public int nextTownId() {
        return nextId(towns);
    }

    public int nextDungeonId() {
        return nextId(dungeons);
    }

    public int nextNpcId() {
        return nextId(npcs);
    }

    public int nextQuestId() {
        return nextId(quests);
    }

    public int nextShopId() {
        return nextId(shops);
    }

    // =========================================================================
    // Observer management
    // =========================================================================

    public void addListener(EditorStateListener l) {
        if (l != null)
            listeners.add(l);
    }

    public void removeListener(EditorStateListener l) {
        listeners.remove(l);
    }

    private void fireDataChanged(String dirtyKey) {
        for (EditorStateListener l : listeners)
            l.onDataChanged(dirtyKey);
    }

    private void fireDirtyCountChanged() {
        int count = dirtySet.size();
        for (EditorStateListener l : listeners)
            l.onDirtyCountChanged(count);
    }

    private void fireUndoRedoChanged() {
        String undo = peekUndo();
        String redo = peekRedo();
        for (EditorStateListener l : listeners)
            l.onUndoRedoChanged(undo, redo);
    }

    private void fireCollectionChanged(String category) {
        for (EditorStateListener l : listeners)
            l.onCollectionChanged(category);
    }

    private void fireProjectLoaded() {
        for (EditorStateListener l : listeners)
            l.onProjectLoaded();
    }

    // =========================================================================
    // Project file I/O (Section 11: .phantasia ZIP archive)
    // =========================================================================

    /**
     * Creates a new empty project, discarding all current data.
     * Clears undo/redo stacks, dirty set, and all data maps.
     */
    public void newProject() {
        clearAllData();
        projectPath = null;
        fireProjectLoaded();
    }

    /**
     * Opens a {@code .phantasia} archive and loads all data into
     * working memory. The previous state is discarded.
     *
     * @param path the archive file path
     * @throws IOException if the archive can't be read
     */
    public void openProject(Path path) throws IOException {
        clearAllData();
        this.projectPath = path;

        URI zipUri = URI.create("jar:" + path.toUri());
        try (FileSystem zipFs = FileSystems.newFileSystem(zipUri, Map.of())) {
            loadFromArchive(zipFs);
        }

        fireProjectLoaded();
    }

    /**
     * Saves the current working state to the project archive.
     * Creates the archive if it doesn't exist. Updates {@code editor.json}
     * with current session metadata.
     *
     * @param path the archive file path
     * @throws IOException if the archive can't be written
     */
    public void saveProject(Path path) throws IOException {
        Path target = path.toAbsolutePath();
        Path staging = Files.createTempFile(target.getParent(), "phantasia_save_", ".zip");
        try {
            if (Files.exists(target)) Files.copy(target, staging, StandardCopyOption.REPLACE_EXISTING);
            else Files.delete(staging); // ZIP provider creates a fresh archive
            URI zipUri = URI.create("jar:" + staging.toUri());
            try (FileSystem zipFs = FileSystems.newFileSystem(zipUri, Map.of("create", "true"))) {
                saveToArchive(zipFs);
            }
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
            }
            this.projectPath = path;
        } finally {
            Files.deleteIfExists(staging);
        }

        clearAllDirty();
    }

    /**
     * Saves to the current project path. Requires a prior
     * {@link #openProject} or {@link #saveProject} call.
     *
     * @throws IOException           if the archive can't be written
     * @throws IllegalStateException if no project path is set
     */
    public void saveProject() throws IOException {
        if (projectPath == null) {
            throw new IllegalStateException("No project path — use saveProject(Path).");
        }
        saveProject(projectPath);
    }

    /** Returns the current project path, or null if unsaved. */
    public Path getProjectPath() {
        return projectPath;
    }

    public String getAssetDirectory() {
        return assetDirectory;
    }

    public void setAssetDirectory(String dir) {
        this.assetDirectory = dir;
    }

    // =========================================================================
    // Loose-file I/O (legacy fallback — loading individual .dat files)
    // =========================================================================

    /**
     * Loads existing {@code .dat} files from the given directory into
     * working memory. Files that don't exist are silently skipped.
     * This is the auto-load-on-startup path for legacy projects.
     *
     * @param datDir the directory containing {@code .dat} files
     */
    public void loadFromDirectory(Path datDir) {
        clearAllData();

        // World map
        Path worldMapPath = datDir.resolve("world.map");
        if (Files.exists(worldMapPath)) {
            try {
                worldMap = WorldMap.loadFromFile(worldMapPath.toString());
            } catch (IOException e) {
                System.err.println("[EditorState] Failed to load world.map: " + e.getMessage());
            }
        }

        // Monsters
        Path monstersPath = datDir.resolve("monsters.dat");
        if (Files.exists(monstersPath)) {
            try {
                loadMonstersDat(monstersPath);
            } catch (IOException e) {
                System.err.println("[EditorState] Failed to load monsters.dat: " + e.getMessage());
            }
        }

        // Spells
        Path spellsPath = datDir.resolve("spells.dat");
        if (Files.exists(spellsPath)) {
            try {
                loadSpellsDat(spellsPath);
            } catch (IOException e) {
                System.err.println("[EditorState] Failed to load spells.dat: " + e.getMessage());
            }
        }

        // Items
        Path itemsPath = datDir.resolve("items.dat");
        if (Files.exists(itemsPath)) {
            try {
                loadItemsDat(itemsPath);
            } catch (IOException e) {
                System.err.println("[EditorState] Failed to load items.dat: " + e.getMessage());
            }
        }

        try {
            loadAuthoredContent(datDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load authored content from " + datDir, e);
        }

        fireProjectLoaded();
    }

    // =========================================================================
    // Built-in data import
    // =========================================================================
    //
    // Seeds the editor's npcs / shops / interiorMaps collections from the
    // programmatic buildDefault() registries in phantasia-core. Each of those
    // registries is the game's "fresh checkout" fallback — what the runtime
    // loads when no baked .dat file exists. Before this import existed, those
    // defaults were authored in code only and invisible to the editor, which
    // meant canonical NPCs like Marta could not be edited.
    //
    // The import is idempotent: entries whose ID already exists in the editor
    // are skipped, so re-running the import will not stomp authored edits.
    // Every newly-imported entry is marked dirty, so the next bakeAll writes
    // the complete canonical set to disk. After one import + one bake, the
    // on-disk .dat files become authoritative and the editor is the single
    // source of truth.

    /** Summary of a single {@link #importBuiltinData()} pass. */
    public record BuiltinImportResult(
            int npcsImported,      int npcsSkipped,
            int shopsImported,     int shopsSkipped,
            int interiorsImported, int interiorsSkipped
    ) {
        public int totalImported() {
            return npcsImported + shopsImported + interiorsImported;
        }

        public int totalSkipped() {
            return npcsSkipped + shopsSkipped + interiorsSkipped;
        }
    }

    /**
     * Imports NPCs, shops, and interior maps from their respective
     * {@code buildDefault()} registries in phantasia-core, skipping any ID
     * already present locally. Each imported record is marked dirty so the
     * next {@link #bakeAll(Path)} writes the full set to disk.
     *
     * @return a {@link BuiltinImportResult} with per-category counts
     */
    public BuiltinImportResult importBuiltinData() {
        int npcIn = 0, npcSkip = 0;
        for (NpcDefinition npc : NpcRegistry.buildDefault().getAll()) {
            if (npcs.containsKey(npc.getId())) { npcSkip++; continue; }
            npcs.put(npc.getId(), npc);
            markDirty("npc:" + npc.getId());
            npcIn++;
        }
        if (npcIn > 0) fireCollectionChanged("npcs");

        int shopIn = 0, shopSkip = 0;
        for (ShopInventory shop : ShopRegistry.buildDefault().getAll()) {
            if (shops.containsKey(shop.getShopId())) { shopSkip++; continue; }
            shops.put(shop.getShopId(), shop);
            markDirty("shop:" + shop.getShopId());
            shopIn++;
        }
        if (shopIn > 0) fireCollectionChanged("shops");

        int intIn = 0, intSkip = 0;
        for (InteriorMap map : InteriorMapRegistry.buildDefault().getAll()) {
            if (interiorMaps.containsKey(map.getId())) { intSkip++; continue; }
            interiorMaps.put(map.getId(), map);
            markDirty("interiorMap:" + map.getId());
            intIn++;
        }
        if (intIn > 0) fireCollectionChanged("interiorMaps");

        return new BuiltinImportResult(
                npcIn, npcSkip, shopIn, shopSkip, intIn, intSkip);
    }

    // =========================================================================
    // Bake workflow (Section 5.4)
    // =========================================================================

    /**
     * Bakes all dirty data to the specified directory.
     * Each data category is written to its corresponding {@code .dat} file.
     * Dirty flags are cleared on success.
     *
     * @param datDir the output directory for {@code .dat} files
     * @throws IOException if any write fails
     */
    public void bakeAll(Path datDir) throws IOException {
        Files.createDirectories(datDir);

        // World map + features
        if (worldMap != null) {
            WorldMapBaker.bake(
                    datDir.resolve("world.map").toString(),
                    worldMap.getWidth(), worldMap.getHeight(),
                    worldMap.getStartPosition().x(),
                    worldMap.getStartPosition().y(),
                    extractTerrainGrid(),
                    extractFeatureGrid());
            clearDirty("worldMap");
        }

        // Monsters
        if (hasDirtyPrefix("monster")) {
            bakeMonstersDat(datDir.resolve("monsters.dat"));
            clearDirtyPrefix("monster");
        }

        // Spells
        if (hasDirtyPrefix("spell")) {
            bakeSpellsDat(datDir.resolve("spells.dat"));
            clearDirtyPrefix("spell");
        }

        // Items
        if (hasDirtyPrefix("item")) {
            bakeItemsDat(datDir.resolve("items.dat"));
            clearDirtyPrefix("item");
        }

        // Always export authored collections, including after save/reopen and deletion.
        bakeNpcsDat(datDir.resolve("npcs.dat"));
        bakeShopsDat(datDir.resolve("shops.dat"));
        bakeInteriorsDat(datDir.resolve("interiors.dat"));
        clearDirtyPrefix("npc");
        clearDirtyPrefix("shop");
        clearDirtyPrefix("interiorMap");

    }

    /**
     * Bakes a single category by dirty-key prefix.
     * Example: {@code bakeCategory(datDir, "monster")} bakes monsters.dat.
     */
    public void bakeCategory(Path datDir, String prefix) throws IOException {
        Files.createDirectories(datDir);
        switch (prefix) {
            case "worldMap" -> {
                if (worldMap != null) {
                    WorldMapBaker.bake(
                            datDir.resolve("world.map").toString(),
                            worldMap.getWidth(), worldMap.getHeight(),
                            worldMap.getStartPosition().x(),
                            worldMap.getStartPosition().y(),
                            extractTerrainGrid(),
                            extractFeatureGrid());
                }
                clearDirty("worldMap");
            }
            case "monster" -> {
                bakeMonstersDat(datDir.resolve("monsters.dat"));
                clearDirtyPrefix("monster");
            }
            case "spell" -> {
                bakeSpellsDat(datDir.resolve("spells.dat"));
                clearDirtyPrefix("spell");
            }
            case "item" -> {
                bakeItemsDat(datDir.resolve("items.dat"));
                clearDirtyPrefix("item");
            }
            case "npc" -> {
                bakeNpcsDat(datDir.resolve("npcs.dat"));
                clearDirtyPrefix("npc");
            }
            case "shop" -> {
                bakeShopsDat(datDir.resolve("shops.dat"));
                clearDirtyPrefix("shop");
            }
            case "interiorMap" -> {
                bakeInteriorsDat(datDir.resolve("interiors.dat"));
                clearDirtyPrefix("interiorMap");
            }
            default -> System.err.println("[EditorState] Unknown bake category: " + prefix);
        }
    }

    // =========================================================================
    // Internal — data clearing
    // =========================================================================

    /** Clears all data, undo/redo, and dirty state for a fresh start. */
    private void clearAllData() {
        worldMap = null;
        towns.clear();
        dungeons.clear();
        interiorMaps.clear();
        npcs.clear();
        quests.clear();
        shops.clear();
        monsters.clear();
        items.clear();
        spells.clear();

        undoStack.clear();
        redoStack.clear();
        dirtySet.clear();
        dirtyAtSavePoint.clear();

        fireUndoRedoChanged();
        fireDirtyCountChanged();
    }

    // =========================================================================
    // Internal — undo stack management
    // =========================================================================

    private void trimUndoStack() {
        while (undoStack.size() > undoLimit) {
            undoStack.removeLast(); // discard oldest
        }
    }

    public int getUndoLimit() {
        return undoLimit;
    }

    public void setUndoLimit(int limit) {
        this.undoLimit = Math.max(1, limit);
    }

    // =========================================================================
    // Internal — dirty prefix helpers
    // =========================================================================

    private boolean hasDirtyPrefix(String prefix) {
        return dirtySet.stream().anyMatch(k -> k.startsWith(prefix));
    }

    private void clearDirtyPrefix(String prefix) {
        dirtySet.removeIf(k -> k.startsWith(prefix));
        fireDirtyCountChanged();
    }

    // =========================================================================
    // Internal — monster .dat I/O (48-byte DataCore blocks, no header)
    // =========================================================================

    private static final int    V4_RECORD_SIZE  = 64;   // matches DataLayout.RECORD_SIZE
    private static final int    V3_RECORD_SIZE  = 48;   // legacy DataCore
    private static final byte[] DAT_MAGIC       = {'P', 'H', 'A', 'N'};
    private static final int    DAT_VERSION     = 4;
    private static final int    DAT_HEADER_SIZE = 8;    // magic + version + count

    private void loadMonstersDat(Path path) throws IOException {
        loadDataCoreDat(path, monsters);
    }


    private void bakeMonstersDat(Path path) throws IOException {
        bakeDataCoreDat(path, monsters);
    }

    // =========================================================================
    // Internal — spell .dat I/O (48-byte DataCore blocks, sparse, no header)
    // =========================================================================

    private void loadSpellsDat(Path path) throws IOException {
        loadDataCoreDat(path, spells);
    }

    private void bakeSpellsDat(Path path) throws IOException {
        bakeDataCoreDat(path, spells);
    }


    // =========================================================================
    // Internal — item .dat I/O
    // =========================================================================

    private void loadItemsDat(Path path) throws IOException {
        // TODO: Delegate to ItemLoader once it supports the full
        // ItemDefinition model (Phase 6). For now, raw byte loading.
        byte[] data = Files.readAllBytes(path);
        // ItemLoader format TBD — placeholder
        System.out.println("[EditorState] items.dat loaded (" + data.length + " bytes) — "
                + "full parse deferred to Phase 6.");
    }

    private void bakeItemsDat(Path path) throws IOException {
        // TODO: Delegate to ItemBaker once it supports the full
        // ItemDefinition model (Phase 6).
        System.out.println("[EditorState] items.dat bake deferred to Phase 6.");
    }

    // =========================================================================
    // Internal — NPC / Shop / Interior .dat I/O (delegates to core bakers)
    // =========================================================================
    //
    // Unlike monsters/spells/items (which the editor manages as raw byte
    // blocks), NPC, shop, and interior data live as typed domain objects.
    // These helpers wrap each category's in-memory map into the registry
    // type the corresponding core Baker expects, then delegate the actual
    // file I/O. The core Baker logs its own [XxxBaker] line per bake.

    private void loadAuthoredContent(Path dir) throws IOException {
        for (InteriorMap map : InteriorMapLoader.load(dir.resolve("interiors.dat").toString()).getAll())
            interiorMaps.put(map.getId(), map);
        for (NpcDefinition npc : NpcBaker.load(dir.resolve("npcs.dat").toString()).getAll())
            npcs.put(npc.getId(), npc);
        for (ShopInventory shop : ShopBaker.load(dir.resolve("shops.dat").toString()).getAll())
            shops.put(shop.getShopId(), shop);
    }

    private static void deleteAuthoredTemp(Path dir) throws IOException {
        for (String file : List.of("interiors.dat", "npcs.dat", "shops.dat"))
            Files.deleteIfExists(dir.resolve(file));
        Files.deleteIfExists(dir);
    }

    private void bakeNpcsDat(Path path) throws IOException {
        NpcRegistry registry = new NpcRegistry();
        for (NpcDefinition npc : npcs.values()) {
            registry.register(npc);
        }
        NpcBaker.bake(registry, path.toString());
    }

    private void bakeShopsDat(Path path) throws IOException {
        ShopRegistry registry = new ShopRegistry();
        for (ShopInventory shop : shops.values()) {
            registry.register(shop);
        }
        ShopBaker.bake(registry, path.toString());
    }

    private void bakeInteriorsDat(Path path) throws IOException {
        InteriorMapRegistry registry = new InteriorMapRegistry();
        for (InteriorMap map : interiorMaps.values()) {
            registry.register(map);
        }
        InteriorMapBaker.bake(registry, path.toString());
    }

    // =========================================================================
    // Internal — world map grid extraction for WorldMapBaker
    // =========================================================================

    private TileType[][] extractTerrainGrid() {
        if (worldMap == null)
            return null;
        int w = worldMap.getWidth(), h = worldMap.getHeight();
        TileType[][] grid = new TileType[w][h];
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                grid[x][y] = worldMap.getTile(x, y).getType();
        return grid;
    }

    private WorldFeature[][] extractFeatureGrid() {
        if (worldMap == null)
            return null;
        int w = worldMap.getWidth(), h = worldMap.getHeight();
        WorldFeature[][] grid = new WorldFeature[w][h];
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                grid[x][y] = worldMap.getTile(x, y).getFeature();
        return grid;
    }

    // =========================================================================
    // Internal — ZIP archive I/O (Section 11)
    // =========================================================================

    private void loadFromArchive(FileSystem zipFs) throws IOException {
        // World map
        Path worldEntry = zipFs.getPath("/world.map");
        if (Files.exists(worldEntry)) {
            // Copy to temp file for WorldMap.loadFromFile (which expects a real path)
            Path tmp = Files.createTempFile("phantasia_world_", ".map");
            Files.copy(worldEntry, tmp, StandardCopyOption.REPLACE_EXISTING);
            worldMap = WorldMap.loadFromFile(tmp.toString());
            Files.deleteIfExists(tmp);
        }

        // Monsters
        Path monstersEntry = zipFs.getPath("/monsters.dat");
        if (Files.exists(monstersEntry)) {
            Path tmp = Files.createTempFile("phantasia_monsters_", ".dat");
            Files.copy(monstersEntry, tmp, StandardCopyOption.REPLACE_EXISTING);
            loadMonstersDat(tmp);
            Files.deleteIfExists(tmp);
        }

        // Spells
        Path spellsEntry = zipFs.getPath("/spells.dat");
        if (Files.exists(spellsEntry)) {
            Path tmp = Files.createTempFile("phantasia_spells_", ".dat");
            Files.copy(spellsEntry, tmp, StandardCopyOption.REPLACE_EXISTING);
            loadSpellsDat(tmp);
            Files.deleteIfExists(tmp);
        }

        // Items
        Path itemsEntry = zipFs.getPath("/items.dat");
        if (Files.exists(itemsEntry)) {
            Path tmp = Files.createTempFile("phantasia_items_", ".dat");
            Files.copy(itemsEntry, tmp, StandardCopyOption.REPLACE_EXISTING);
            loadItemsDat(tmp);
            Files.deleteIfExists(tmp);
        }

        Path contentDir = Files.createTempDirectory("phantasia_open_");
        try {
            for (String file : List.of("interiors.dat", "npcs.dat", "shops.dat")) {
                Path entry = zipFs.getPath("/" + file);
                if (Files.exists(entry)) Files.copy(entry, contentDir.resolve(file));
            }
            loadAuthoredContent(contentDir);
        } finally {
            deleteAuthoredTemp(contentDir);
        }

        // editor.json
        Path editorJson = zipFs.getPath("/editor.json");
        if (Files.exists(editorJson)) {
            loadEditorMetadata(Files.readString(editorJson));
        }
    }

    private void saveToArchive(FileSystem zipFs) throws IOException {
        // Bake each category into the archive
        // World map
        if (worldMap != null) {
            Path tmp = Files.createTempFile("phantasia_world_", ".map");
            WorldMapBaker.bake(
                    tmp.toString(),
                    worldMap.getWidth(), worldMap.getHeight(),
                    worldMap.getStartPosition().x(),
                    worldMap.getStartPosition().y(),
                    extractTerrainGrid(), extractFeatureGrid());
            Files.copy(tmp, zipFs.getPath("/world.map"),
                    StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(tmp);
        }

        // Monsters
        if (!monsters.isEmpty()) {
            Path tmp = Files.createTempFile("phantasia_monsters_", ".dat");
            bakeMonstersDat(tmp);
            Files.copy(tmp, zipFs.getPath("/monsters.dat"),
                    StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(tmp);
        }

        // Spells
        if (!spells.isEmpty()) {
            Path tmp = Files.createTempFile("phantasia_spells_", ".dat");
            bakeSpellsDat(tmp);
            Files.copy(tmp, zipFs.getPath("/spells.dat"),
                    StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(tmp);
        }

        // Always replace these entries, including empty collections after deletion.
        Path contentDir = Files.createTempDirectory("phantasia_save_content_");
        try {
            bakeInteriorsDat(contentDir.resolve("interiors.dat"));
            bakeNpcsDat(contentDir.resolve("npcs.dat"));
            bakeShopsDat(contentDir.resolve("shops.dat"));
            for (String file : List.of("interiors.dat", "npcs.dat", "shops.dat")) {
                Files.copy(contentDir.resolve(file), zipFs.getPath("/" + file),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            deleteAuthoredTemp(contentDir);
        }
        // Other archive categories remain pending their dedicated codecs.

        // editor.json
        String metadata = buildEditorMetadata();
        Files.writeString(zipFs.getPath("/editor.json"), metadata,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    // =========================================================================
    // Internal — editor.json metadata (Section 11.2)
    // =========================================================================

    /**
     * Parses the editor.json metadata string.
     * Minimal hand-parsed JSON — no library dependency needed for this
     * small, flat structure.
     */
    private void loadEditorMetadata(String json) {
        // Extract assetDirectory if present
        int adIdx = json.indexOf("\"assetDirectory\"");
        if (adIdx >= 0) {
            int colon = json.indexOf(':', adIdx);
            int quote1 = json.indexOf('"', colon + 1);
            int quote2 = json.indexOf('"', quote1 + 1);
            if (quote1 >= 0 && quote2 > quote1) {
                assetDirectory = json.substring(quote1 + 1, quote2);
            }
        }
        // Note: lastOpenTabs, windowBounds, splitterPositions are consumed
        // by EditorFrame at startup — EditorState just preserves them.
        // Full metadata integration happens when EditorFrame is built (Phase 2b).
    }

    /**
     * Builds the editor.json metadata string.
     */
    private String buildEditorMetadata() {
        // Minimal JSON — expanded when EditorFrame contributes session state
        return "{\n"
                + "  \"assetDirectory\": \"" + assetDirectory + "\",\n"
                + "  \"lastBakeTimestamp\": \""
                + java.time.Instant.now().toString() + "\"\n"
                + "}\n";
    }

    // =========================================================================
    // Diagnostics
    // =========================================================================

    @Override
    public String toString() {
        return "EditorState["
                + "worldMap=" + (worldMap != null ? worldMap.getWidth() + "x" + worldMap.getHeight() : "null")
                + ", towns=" + towns.size()
                + ", dungeons=" + dungeons.size()
                + ", interiorMaps=" + interiorMaps.size()
                + ", npcs=" + npcs.size()
                + ", quests=" + quests.size()
                + ", shops=" + shops.size()
                + ", monsters=" + monsters.size()
                + ", items=" + items.size()
                + ", spells=" + spells.size()
                + ", dirty=" + dirtySet.size()
                + ", undoDepth=" + undoStack.size()
                + "]";
    }
    /**
     * Loads 64-byte DataCore records from a .dat file into the given list.
     * Transparently migrates legacy 48-byte (v3) files by zero-padding each
     * record to the current 64-byte (v4) layout.
     */
    private void loadDataCoreDat(Path path, List<byte[]> target) throws IOException {
        byte[] data = Files.readAllBytes(path);
        if (data.length == 0) return;

        if (hasDatMagic(data)) {
            // ── v4: header + 64-byte records ─────────────────────────────
            int version = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
            int count   = ((data[6] & 0xFF) << 8) | (data[7] & 0xFF);
            int needed  = DAT_HEADER_SIZE + count * V4_RECORD_SIZE;
            if (data.length < needed) {
                System.err.println("[EditorState] Truncated v" + version + " "
                        + path.getFileName() + ": have " + data.length
                        + " bytes, need " + needed);
                return;
            }
            for (int i = 0; i < count; i++) {
                byte[] block = new byte[V4_RECORD_SIZE];
                System.arraycopy(data, DAT_HEADER_SIZE + i * V4_RECORD_SIZE,
                        block, 0, V4_RECORD_SIZE);
                target.add(block);
            }
        } else {
            // ── v3 legacy: no header, 48-byte records, pad to 64 ─────────
            if (data.length % V3_RECORD_SIZE != 0) {
                System.err.println("[EditorState] Malformed legacy "
                        + path.getFileName() + ": " + data.length
                        + " bytes, not divisible by " + V3_RECORD_SIZE);
                return;
            }
            int count = data.length / V3_RECORD_SIZE;
            for (int i = 0; i < count; i++) {
                byte[] block = new byte[V4_RECORD_SIZE];  // zero-initialised
                System.arraycopy(data, i * V3_RECORD_SIZE,
                        block, 0, V3_RECORD_SIZE);
                // Bytes 48–63 remain 0 — the correct default for the
                // expansion zone (portrait, traits, resonance, extended inv).
                target.add(block);
            }
            System.out.println("[EditorState] Migrated " + count
                    + " v3 record(s) from " + path.getFileName()
                    + " → v4 (will be re-saved with PHAN header on next bake).");
        }
    }

    /**
     * Writes the 64-byte DataCore records in {@code source} as a v4 .dat
     * file with the PHAN + version + count header.
     */
    private void bakeDataCoreDat(Path path, List<byte[]> source) throws IOException {
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(path.toFile())))) {
            out.write(DAT_MAGIC);
            out.writeShort(DAT_VERSION);
            out.writeShort(source.size());
            for (byte[] block : source) {
                if (block.length != V4_RECORD_SIZE) {
                    throw new IOException("Corrupt DataCore record in "
                            + path.getFileName() + ": expected "
                            + V4_RECORD_SIZE + " bytes, got " + block.length);
                }
                out.write(block);
            }
        }
    }

    private static boolean hasDatMagic(byte[] data) {
        if (data.length < DAT_MAGIC.length) return false;
        for (int i = 0; i < DAT_MAGIC.length; i++) {
            if (data[i] != DAT_MAGIC[i]) return false;
        }
        return true;
    }
}

