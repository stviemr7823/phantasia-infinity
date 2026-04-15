// phantasia-core/src/main/java/com/phantasia/core/world/FeatureRegistry.java
package com.phantasia.core.world;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

/**
 * Loads and saves feature metadata from/to features.dat.
 *
 * BINARY FORMAT (little-endian):
 *
 *   Header:
 *     4 bytes  — magic "FEAT"
 *     2 bytes  — version (currently 1)
 *     2 bytes  — record count
 *
 *   Per record:
 *     2 bytes  — id
 *     1 byte   — FeatureType ordinal
 *     2 bytes  — x coordinate
 *     2 bytes  — y coordinate
 *     1 byte   — serviceFlags
 *     2 bytes  — name length (N)
 *     N bytes  — name (UTF-8)
 *     2 bytes  — description length (D)
 *     D bytes  — description (UTF-8)
 *
 * LOOKUP:
 *   Primary key: (FeatureType, id) — matches WorldFeature tile references.
 *   Secondary: (x, y) coordinate — for the map editor.
 *
 * USAGE:
 *   // Load
 *   FeatureRegistry registry = FeatureRegistry.load("data/features.dat");
 *   FeatureRecord pendragon = registry.get(FeatureType.TOWN, 0);
 *
 *   // Save (from WorldMapBaker or editor)
 *   registry.add(FeatureRecord.town(0, 6, 10, "Pendragon", "The capital city."));
 *   registry.save("data/features.dat");
 */
public final class FeatureRegistry {

    private static final Logger LOG =
            Logger.getLogger(FeatureRegistry.class.getName());

    private static final byte[] MAGIC   = {'F', 'E', 'A', 'T'};
    private static final short  VERSION = 1;

    // Primary index: type → (id → record)
    private final Map<FeatureType, Map<Integer, FeatureRecord>> byTypeId =
            new EnumMap<>(FeatureType.class);

    // Secondary index: "x,y" → record (for editor)
    private final Map<String, FeatureRecord> byCoord = new LinkedHashMap<>();

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public FeatureRegistry() {
        for (FeatureType t : FeatureType.values()) {
            byTypeId.put(t, new LinkedHashMap<>());
        }
    }

    // -------------------------------------------------------------------------
    // Mutation
    // -------------------------------------------------------------------------

    public void add(FeatureRecord record) {
        byTypeId.get(record.getType()).put(record.getId(), record);
        byCoord.put(coordKey(record.getX(), record.getY()), record);
    }

    public void remove(FeatureType type, int id) {
        FeatureRecord r = byTypeId.get(type).remove(id);
        if (r != null) byCoord.remove(coordKey(r.getX(), r.getY()));
    }

    public void clear() {
        byTypeId.values().forEach(Map::clear);
        byCoord.clear();
    }

    // -------------------------------------------------------------------------
    // Lookup
    // -------------------------------------------------------------------------

    /** Returns the record for (type, id), or null if not found. */
    public FeatureRecord get(FeatureType type, int id) {
        return byTypeId.get(type).get(id);
    }

    /** Returns the record at map coordinate (x, y), or null if not found. */
    public FeatureRecord getAt(int x, int y) {
        return byCoord.get(coordKey(x, y));
    }

    /** Returns all records of a given type. */
    public Collection<FeatureRecord> getAll(FeatureType type) {
        return Collections.unmodifiableCollection(byTypeId.get(type).values());
    }

    /** Returns all records across all types. */
    public Collection<FeatureRecord> getAll() {
        List<FeatureRecord> all = new ArrayList<>();
        byTypeId.values().forEach(m -> all.addAll(m.values()));
        return Collections.unmodifiableList(all);
    }

    public int size() {
        return byCoord.size();
    }

    // -------------------------------------------------------------------------
    // Save
    // -------------------------------------------------------------------------

    public void save(String path) throws IOException {
        File file = new File(path);
        file.getParentFile().mkdirs();

        Collection<FeatureRecord> all = getAll();

        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(file)))) {

            // Header
            out.write(MAGIC);
            out.writeShort(VERSION);
            out.writeShort(all.size());

            // Records
            for (FeatureRecord r : all) {
                out.writeShort(r.getId());
                out.writeByte(r.getType().ordinal());
                out.writeShort(r.getX());
                out.writeShort(r.getY());
                out.writeByte(r.getServiceFlags());
                writeString(out, r.getName());
                writeString(out, r.getDescription());
            }
        }

        LOG.info("[FeatureRegistry] Saved " + all.size()
                + " records to " + file.getAbsolutePath());
    }

    // -------------------------------------------------------------------------
    // Load
    // -------------------------------------------------------------------------

    public static FeatureRegistry load(String path) throws IOException {
        FeatureRegistry registry = new FeatureRegistry();
        File file = new File(path);

        if (!file.exists()) {
            LOG.info("[FeatureRegistry] No features.dat at "
                    + file.getAbsolutePath() + " — empty registry.");
            return registry;
        }

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {

            // Validate magic
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new IOException("Not a features.dat file — bad magic bytes.");
            }

            short version = in.readShort();
            if (version != VERSION) {
                throw new IOException("Unsupported features.dat version: " + version);
            }

            int count = in.readShort() & 0xFFFF;

            for (int i = 0; i < count; i++) {
                int         id           = in.readShort() & 0xFFFF;
                int         typeOrdinal  = in.readByte()  & 0xFF;
                int         x            = in.readShort() & 0xFFFF;
                int         y            = in.readShort() & 0xFFFF;
                byte        serviceFlags = in.readByte();
                String      name         = readString(in);
                String      description  = readString(in);

                FeatureType type = FeatureType.values()[typeOrdinal];
                registry.add(new FeatureRecord(
                        id, type, x, y, name, description, serviceFlags));
            }

            LOG.info("[FeatureRegistry] Loaded " + count
                    + " records from " + file.getAbsolutePath());
        }

        return registry;
    }

    // -------------------------------------------------------------------------
    // I/O helpers
    // -------------------------------------------------------------------------

    private static void writeString(DataOutputStream out, String s)
            throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in)
            throws IOException {
        int len = in.readShort() & 0xFFFF;
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String coordKey(int x, int y) {
        return x + "," + y;
    }
}