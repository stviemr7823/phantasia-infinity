// phantasia-core/src/main/java/com/phantasia/core/data/InteriorMapBaker.java
package com.phantasia.core.data;

import com.phantasia.core.logic.Direction;
import com.phantasia.core.model.PlacedNpc;
import com.phantasia.core.world.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

/** Writes INTR version 2: header (magic, u16 version, u16 map count),
 * followed by lossless typed records defined in InteriorMapCodec.
 * Existing version 1 files remain readable by InteriorMapLoader.
 */
public final class InteriorMapBaker {

    private static final byte[] MAGIC   = {'I', 'N', 'T', 'R'};
    private static final short  VERSION = 2;

    private InteriorMapBaker() {}

    /**
     * Bakes all maps in the registry to {@code outPath}.
     */
    public static void bake(InteriorMapRegistry registry, String outPath)
            throws IOException {

        File file = DataPaths.ensureParentDirs(outPath);
        Collection<InteriorMap> maps = registry.getAll();

        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(file)))) {

            out.write(MAGIC);
            out.writeShort(VERSION);
            if (maps.size() > 65535) throw new IOException("Too many interior maps");
            out.writeShort(maps.size());

            for (InteriorMap map : maps) {
                InteriorMapCodec.write(out, map);
            }
        }

        System.out.println("[InteriorMapBaker] Baked " + maps.size()
                + " interior maps → " + outPath);
    }

}
