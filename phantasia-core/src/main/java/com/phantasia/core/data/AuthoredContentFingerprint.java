package com.phantasia.core.data;

import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Hash the exact sorted runtime exports, including map, NPC/dialogue and shop definitions. */
public final class AuthoredContentFingerprint {
    private AuthoredContentFingerprint() {}
    public static String compute(InteriorMapRegistry maps, NpcRegistry npcs, ShopRegistry shops) throws IOException {
        var sortedMaps = new InteriorMapRegistry(); maps.getAll().stream().sorted(Comparator.comparingInt(m -> m.getId())).forEach(sortedMaps::register);
        var sortedNpcs = new NpcRegistry(); npcs.getAll().stream().sorted(Comparator.comparingInt(n -> n.getId())).forEach(sortedNpcs::register);
        var sortedShops = new ShopRegistry(); shops.getAll().stream().sorted(Comparator.comparingInt(s -> s.getShopId())).forEach(sortedShops::register);
        Path directory = Files.createTempDirectory("native-content-id-");
        try {
            InteriorMapBaker.bake(sortedMaps, directory.resolve("interiors.dat").toString());
            NpcBaker.bake(sortedNpcs, directory.resolve("npcs.dat").toString()); ShopBaker.bake(sortedShops, directory.resolve("shops.dat").toString());
            MessageDigest digest;
            try { digest = MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
            for (var name : List.of("interiors.dat", "npcs.dat", "shops.dat")) {
                var file = directory.resolve(name);
                digest.update(java.nio.ByteBuffer.allocate(8).putLong(Files.size(file)).array());
                try (var in = Files.newInputStream(file)) { byte[] buffer = new byte[8192]; int n; while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n); }
            }
            return HexFormat.of().formatHex(digest.digest());
        } finally { try (var files = Files.walk(directory)) { for (var file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file); } }
    }
}
