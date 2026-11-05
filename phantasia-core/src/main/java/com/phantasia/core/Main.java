package com.phantasia.core;

import com.phantasia.core.model.Job;
import com.phantasia.core.model.PlayerCharacter;

/** Small headless character-creation smoke entry point. */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        // C5a: the entry point supplies character values, never record offsets.
        PlayerCharacter bonzo = PlayerCharacter.fromLegacyCreation(
                "Bonzo", Job.WIZARD,
                20, 12, 8, 12, 12, 10, 1);
        System.out.println("Character created: " + bonzo.getName());
    }
}
