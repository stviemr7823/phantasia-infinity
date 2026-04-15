package com.phantasia.core;

import com.phantasia.core.model.Action;
import com.phantasia.core.model.Entity;
import com.phantasia.core.model.PlayerCharacter;
import com.phantasia.core.model.DataCore; // Import your binary core
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        List<Entity> party = new ArrayList<>();

        // 1. Create the binary record (48 bytes)
        DataCore bonzoCore = new DataCore(new byte[48]);
        bonzoCore.setName("Bonzo");
        bonzoCore.setStat(5, 20);  // HP at Offset 5
        bonzoCore.setStat(15, 12); // Speed at Offset 15
        bonzoCore.setStat(20, 1);  // Job (Wizard) at Offset 20

        // 2. Wrap the core in the PlayerCharacter
        // This now matches the 'required: DataCore' constraint
        party.add(new PlayerCharacter(bonzoCore));

        System.out.println("Character created via binary core: " + party.get(0).getName());
    }
}