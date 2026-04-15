// phantasia-j2d/src/main/java/com/phantasia/j2d/tour/TourSettings.java
package com.phantasia.j2d.tour;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * Centralised settings for the Phantasia Touring Engine.
 *
 * Every mutable setting fires a PropertyChangeEvent so that panels
 * (MapPanel, DungeonPanel, CombatPanel) can react immediately without
 * polling. The event name matches the setter — e.g. "encountersEnabled".
 *
 * Accessed as a singleton via {@code TourSettings.get()}.
 *
 * THREAD SAFETY:
 *   All mutations happen on the EDT (from menu items or the settings
 *   dialog), so no synchronization is needed.
 */
public final class TourSettings {

    private static final TourSettings INSTANCE = new TourSettings();
    public static TourSettings get() { return INSTANCE; }

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    // =====================================================================
    // Encounters
    // =====================================================================

    private boolean encountersEnabled    = true;
    private int     encounterRatePercent = 100;   // 0–300

    public boolean isEncountersEnabled()       { return encountersEnabled; }
    public int     getEncounterRatePercent()    { return encounterRatePercent; }

    public void setEncountersEnabled(boolean v) {
        boolean old = encountersEnabled; encountersEnabled = v;
        pcs.firePropertyChange("encountersEnabled", old, v);
    }
    public void setEncounterRatePercent(int v) {
        int old = encounterRatePercent; encounterRatePercent = clamp(v, 0, 300);
        pcs.firePropertyChange("encounterRatePercent", old, encounterRatePercent);
    }

    // =====================================================================
    // Combat
    // =====================================================================

    private boolean autoWin    = false;
    private boolean godMode    = false;

    public boolean isAutoWin()       { return autoWin; }
    public boolean isGodMode()       { return godMode; }

    public void setAutoWin(boolean v) {
        boolean old = autoWin; autoWin = v;
        pcs.firePropertyChange("autoWin", old, v);
    }
    public void setGodMode(boolean v) {
        boolean old = godMode; godMode = v;
        pcs.firePropertyChange("godMode", old, v);
    }

    // =====================================================================
    // Dungeon
    // =====================================================================

    private boolean fogOfWarEnabled = true;
    private int     torchRadius     = 3;    // 1–10

    public boolean isFogOfWarEnabled()  { return fogOfWarEnabled; }
    public int     getTorchRadius()     { return torchRadius; }

    public void setFogOfWarEnabled(boolean v) {
        boolean old = fogOfWarEnabled; fogOfWarEnabled = v;
        pcs.firePropertyChange("fogOfWarEnabled", old, v);
    }
    public void setTorchRadius(int v) {
        int old = torchRadius; torchRadius = clamp(v, 1, 10);
        pcs.firePropertyChange("torchRadius", old, torchRadius);
    }

    // =====================================================================
    // Display / overlays
    // =====================================================================

    private boolean showGridCoords   = false;
    private boolean showFeatureLabels = false;
    private int     gameSpeedPercent  = 100;   // 50–400

    public boolean isShowGridCoords()    { return showGridCoords; }
    public boolean isShowFeatureLabels() { return showFeatureLabels; }
    public int     getGameSpeedPercent() { return gameSpeedPercent; }

    public void setShowGridCoords(boolean v) {
        boolean old = showGridCoords; showGridCoords = v;
        pcs.firePropertyChange("showGridCoords", old, v);
    }
    public void setShowFeatureLabels(boolean v) {
        boolean old = showFeatureLabels; showFeatureLabels = v;
        pcs.firePropertyChange("showFeatureLabels", old, v);
    }
    public void setGameSpeedPercent(int v) {
        int old = gameSpeedPercent; gameSpeedPercent = clamp(v, 50, 400);
        pcs.firePropertyChange("gameSpeedPercent", old, gameSpeedPercent);
    }

    // =====================================================================
    // PropertyChangeSupport delegation
    // =====================================================================

    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }
    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }
    public void addPropertyChangeListener(String prop, PropertyChangeListener l) {
        pcs.addPropertyChangeListener(prop, l);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    private TourSettings() {}
}