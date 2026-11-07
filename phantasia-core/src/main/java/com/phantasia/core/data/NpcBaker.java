// phantasia-core/src/main/java/com/phantasia/core/data/NpcBaker.java
package com.phantasia.core.data;

import com.phantasia.core.model.DialogueNode;
import com.phantasia.core.model.NpcDefinition;
import com.phantasia.core.model.NpcRole;
import com.phantasia.core.data.QuestFlag;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

/**
 * Serializes and deserializes {@link NpcRegistry} contents to {@code npcs.dat}.
 *
 * <h3>Binary format — version 2 (version 1 reader retained)</h3>
 * <pre>
 *   Header:
 *     4 bytes  magic "NPCS"
 *     2 bytes  version = 2
 *     2 bytes  NPC count
 *
 *   Per NPC:
 *     2 bytes  id
 *     string   name
 *     1 byte   NpcRole ordinal
 *     2 bytes  shopId
 *     v2 nullable string spriteAssetId
 *     v2 nullable string portraitAssetId
 *
 *     v2: boolean animation present; nullable UTF-8 keys, two floats, facing boolean.
 *
 *     Dialogue:
 *       2 bytes  node count
 *       Per node:
 *         2 bytes  line count
 *         Per line: string
 *         1 byte   required-flag count
 *         Per flag: 1 byte bit value (0–63)
 *         1 byte   excluded-flag count
 *         Per flag: 1 byte bit value
 *         1 byte   flagToSet  (0xFF = none, else bit value)
 *         2 bytes  giveItemId (0 = none)
 *
 *   Ordinary strings: u16 UTF-8 byte length + bytes. v2 nullable strings:
 *   i32 byte length (-1 = null) + UTF-8 bytes, limited to 1,000,000 bytes.
 * </pre>
 */
public final class NpcBaker {

    private static final Logger LOG = Logger.getLogger(NpcBaker.class.getName());

    private static final byte[] MAGIC   = {'N', 'P', 'C', 'S'};
    private static final short  VERSION = 2;
    private static final int    NO_FLAG = 0xFF;

    private NpcBaker() {}

    // -------------------------------------------------------------------------
    // Bake (write)
    // -------------------------------------------------------------------------

    /**
     * Serializes all NPCs in the registry to {@code outPath}.
     */
    public static void bake(NpcRegistry registry, String outPath) throws IOException {
        File file = DataPaths.ensureParentDirs(outPath);
        Collection<NpcDefinition> npcs = registry.getAll();

        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(file)))) {

            out.write(MAGIC);
            out.writeShort(VERSION);
            ushort(out, npcs.size());

            for (NpcDefinition npc : npcs) {
                writeNpc(out, npc);
            }
        }

        LOG.info("[NpcBaker] Baked " + npcs.size() + " NPCs → " + outPath);
    }

    private static void writeNpc(DataOutputStream out, NpcDefinition npc)
            throws IOException {
        ushort(out, npc.getId());
        writeString(out, npc.getName());
        out.writeByte(npc.getRole().ordinal());
        ushort(out, npc.getShopId());
        InteriorMapCodec.string(out, npc.getSpriteAssetId());
        InteriorMapCodec.string(out, npc.getPortraitAssetId());

        var animation = npc.getAnimationProfile();
        out.writeBoolean(animation != null);
        if (animation != null) {
            InteriorMapCodec.string(out, animation.idleAnimation());
            InteriorMapCodec.string(out, animation.walkAnimation());
            InteriorMapCodec.string(out, animation.talkAnimation());
            out.writeFloat(animation.wanderRadius());
            out.writeFloat(animation.paceSpeed());
            out.writeBoolean(animation.facesPlayer());
        }

        // Dialogue nodes
        var nodes = npc.getDialogue().getNodes();
        ushort(out, nodes.size());
        for (DialogueNode node : nodes) {
            writeNode(out, node);
        }
    }

    private static void writeNode(DataOutputStream out, DialogueNode node)
            throws IOException {
        // Lines
        ushort(out, node.lines().size());
        for (String line : node.lines()) {
            writeString(out, line);
        }

        // Required flags
        writeFlagSet(out, node.requiredFlags());

        // Excluded flags
        writeFlagSet(out, node.excludedFlags());

        // flagToSet (0xFF = none)
        if (node.flagToSet() != null) {
            out.writeByte(node.flagToSet().bit);
        } else {
            out.writeByte(NO_FLAG);
        }

        // giveItemId
        ushort(out, node.giveItemId());
    }

    private static void writeFlagSet(DataOutputStream out, Set<QuestFlag> flags)
            throws IOException {
        out.writeByte(flags.size());
        for (QuestFlag f : flags) {
            out.writeByte(f.bit);
        }
    }

    // -------------------------------------------------------------------------
    // Load (read) — added to NpcRegistry as a static factory
    // -------------------------------------------------------------------------

    /**
     * Deserializes {@code npcs.dat} into an {@link NpcRegistry}.
     * Returns an empty registry (not null) if the file is absent.
     *
     * @throws IOException if the file exists but cannot be parsed
     */
    public static NpcRegistry load(String path) throws IOException {
        NpcRegistry registry = new NpcRegistry();
        File file = new File(path);

        if (!file.exists()) {
            LOG.info("[NpcBaker] No npcs.dat at " + file.getAbsolutePath()
                    + " — empty registry.");
            return registry;
        }

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {

            // Validate magic
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new IOException("Not an npcs.dat file — bad magic.");
            }

            short version = in.readShort();
            if (version != 1 && version != VERSION) {
                throw new IOException("Unsupported npcs.dat version: " + version);
            }

            int count = in.readShort() & 0xFFFF;
            for (int i = 0; i < count; i++) {
                registry.register(readNpc(in, version));
            }
        }

        LOG.info("[NpcBaker] Loaded " + registry.size() + " NPCs from " + path);
        return registry;
    }

    private static NpcDefinition readNpc(DataInputStream in, int version) throws IOException {
        int    id      = in.readShort() & 0xFFFF;
        String name    = readString(in);
        int    roleOrd = in.readByte() & 0xFF;
        int    shopId  = in.readShort() & 0xFFFF;
        String sprite  = version >= 2 ? InteriorMapCodec.string(in) : readString(in);
        String portrait = version >= 2 ? InteriorMapCodec.string(in) : readString(in);

        NpcRole[] roles = NpcRole.values();
        NpcRole role = roleOrd < roles.length ? roles[roleOrd] : NpcRole.INFORMANT;

        NpcDefinition npc = new NpcDefinition(id, name, role,
                version == 1 && sprite.isEmpty() ? null : sprite);
        npc.shopId(shopId);
        if (version >= 2 || !portrait.isEmpty()) npc.portraitAssetId(portrait);

        if (version >= 2) {
            npc.animationProfile(in.readBoolean()
                    ? new com.phantasia.core.model.AnimationProfile(
                        InteriorMapCodec.string(in), InteriorMapCodec.string(in),
                        InteriorMapCodec.string(in), in.readFloat(), in.readFloat(), in.readBoolean())
                    : null);
        }

        // Dialogue nodes
        int nodeCount = in.readShort() & 0xFFFF;
        for (int i = 0; i < nodeCount; i++) {
            npc.getDialogue().addNode(readNode(in));
        }

        return npc;
    }

    private static DialogueNode readNode(DataInputStream in) throws IOException {
        // Lines
        int lineCount = in.readShort() & 0xFFFF;
        List<String> lines = new ArrayList<>(lineCount);
        for (int i = 0; i < lineCount; i++) {
            lines.add(readString(in));
        }

        // Required flags
        Set<QuestFlag> required = readFlagSet(in);

        // Excluded flags
        Set<QuestFlag> excluded = readFlagSet(in);

        // flagToSet
        int flagByte = in.readByte() & 0xFF;
        QuestFlag flagToSet = getFlagByBit(flagByte);

        // giveItemId
        int giveItemId = in.readShort() & 0xFFFF;

        return new DialogueNode(lines, required, excluded, flagToSet, giveItemId);
    }

    private static Set<QuestFlag> readFlagSet(DataInputStream in) throws IOException {
        int count = in.readByte() & 0xFF;
        Set<QuestFlag> flags = new LinkedHashSet<>(count);
        for (int i = 0; i < count; i++) {
            int bit = in.readByte() & 0xFF;
            QuestFlag f = getFlagByBit(bit);
            if (f != null) {
                flags.add(f);
            }
        }
        return flags;
    }

    private static QuestFlag getFlagByBit(int bit) {
        if (bit == NO_FLAG) return null;
        for (QuestFlag f : QuestFlag.values()) {
            if (f.bit == bit) {
                return f;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // I/O helpers
    // -------------------------------------------------------------------------

    private static void ushort(DataOutput out, int value) throws IOException {
        if (value < 0 || value > 65535) throw new IOException("NPC value exceeds u16: " + value);
        out.writeShort(value);
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        if (s == null) s = "";
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        ushort(out, bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int    len   = in.readShort() & 0xFFFF;
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}

