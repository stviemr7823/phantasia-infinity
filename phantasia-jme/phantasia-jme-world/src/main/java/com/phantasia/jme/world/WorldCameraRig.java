// phantasia-jme/phantasia-jme-world/src/main/java/com/phantasia/jme/world/WorldCameraRig.java
package com.phantasia.jme.world;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

public final class WorldCameraRig {

    // -------------------------------------------------------------------------
    // Tunable parameters
    // -------------------------------------------------------------------------

    public float distance           = 36f;
    public float heightOffset       = 10f;
    public float pitchDegrees       = -22f;  // default follow-cam pitch
    public float focusLerpSpeed     = 8f;
    public float rotationSlerpSpeed = 3f;

    // -------------------------------------------------------------------------
    // Mouse look sensitivity
    // -------------------------------------------------------------------------

    /** Radians of yaw per pixel of mouse movement. */
    public float mouseSensitivityX = 0.8f;

    /** Radians of pitch per pixel of mouse movement. */
    public float mouseSensitivityY = 0.6f;

    /** Tightest downward pitch allowed (degrees) — nearly top-down. */
    public float pitchMinDegrees = -80f;

    /** Tightest upward pitch allowed (degrees) — looking up at sky. */
    public float pitchMaxDegrees = 45f;

    // -------------------------------------------------------------------------
    // Internal state
    // -------------------------------------------------------------------------

    private final Vector3f currentFocus = new Vector3f();
    private final Vector3f targetFocus  = new Vector3f();
    private       float    currentYaw   = 0f;
    private       boolean  initialized  = false;

    // Mouse look state
    private boolean mouseLookActive = false;
    private float   mouseLookYaw    = 0f;
    private float   mouseLookPitch  = 0f;  // degrees, current pitch during mouse look
    private float   currentPitch    = 0f;  // degrees, interpolated pitch

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void update(float tpf, Vector3f playerPos, float playerFacingYaw, Camera camera) {
        if (!initialized) {
            snapTo(playerPos, playerFacingYaw, camera);
        }

        // --- Lerp focal anchor toward player ---
        targetFocus.set(playerPos.x, playerPos.y + heightOffset * 0.3f, playerPos.z);
        float fa = Math.min(tpf * focusLerpSpeed, 1.0f);
        currentFocus.interpolateLocal(targetFocus, fa);

        float activeYaw;
        float activePitch;

        if (mouseLookActive) {
            // Mouse look — use free-orbit values directly, no slerp
            activeYaw   = mouseLookYaw;
            activePitch = mouseLookPitch;
        } else {
            // Normal follow — slerp yaw toward player facing, pitch toward default
            currentYaw = slerpAngle(currentYaw, playerFacingYaw,
                    Math.min(tpf * rotationSlerpSpeed, 1.0f));
            activeYaw = currentYaw;

            // Slerp pitch back to configured default when not in mouse look
            currentPitch += (pitchDegrees - currentPitch)
                    * Math.min(tpf * rotationSlerpSpeed, 1.0f);
            activePitch = currentPitch;
        }

        positionCamera(activeYaw, activePitch, camera);
    }

    public void snapTo(Vector3f playerPos, float playerFacingYaw, Camera camera) {
        currentFocus.set(playerPos.x, playerPos.y + heightOffset * 0.3f, playerPos.z);
        targetFocus.set(currentFocus);
        currentYaw   = playerFacingYaw;
        currentPitch = pitchDegrees;
        // Sync mouse look state to current so activation has no jump
        mouseLookYaw   = currentYaw;
        mouseLookPitch = currentPitch;
        initialized = true;
        positionCamera(currentYaw, currentPitch, camera);
    }

    // -------------------------------------------------------------------------
    // Mouse look control
    // -------------------------------------------------------------------------

    /**
     * Called by WorldState when stationary mouse movement is detected.
     * On first call after movement stops, copies current camera state so
     * there is no positional jump when mouse look kicks in.
     */
    public void activateMouseLook() {
        if (!mouseLookActive) {
            // Sync from current follow-cam state — no jump on activation
            mouseLookYaw   = currentYaw;
            mouseLookPitch = currentPitch;
            mouseLookActive = true;
        }
    }

    /**
     * Called by WorldState the moment any movement key is pressed.
     * The follow-cam slerp takes over from wherever the mouse look left off.
     */
    public void deactivateMouseLook() {
        if (mouseLookActive) {
            // Hand currentYaw back to the follow-cam from mouse look position
            // so the slerp starts from where the camera actually is
            currentYaw   = mouseLookYaw;
            currentPitch = mouseLookPitch;
            mouseLookActive = false;
        }
    }

    /**
     * Applies one frame of mouse delta to the free-orbit angles.
     * dx/dy are in JME analog units (pixels * sensitivity already scaled by JME).
     */
    public void applyMouseDelta(float dx, float dy) {
        mouseLookYaw -= dx * mouseSensitivityX;

        // Wrap yaw
        while (mouseLookYaw >  FastMath.PI) mouseLookYaw -= FastMath.TWO_PI;
        while (mouseLookYaw < -FastMath.PI) mouseLookYaw += FastMath.TWO_PI;

        // Pitch: dy positive = mouse down = camera pitches up (more negative degrees)
        mouseLookPitch -= dy * mouseSensitivityY * FastMath.RAD_TO_DEG;
        mouseLookPitch  = FastMath.clamp(mouseLookPitch, pitchMinDegrees, pitchMaxDegrees);
    }

    public boolean isMouseLookActive() { return mouseLookActive; }

    // -------------------------------------------------------------------------
    // Shared camera positioning
    // -------------------------------------------------------------------------

    private void positionCamera(float yaw, float pitchDeg, Camera camera) {
        float pitch = pitchDeg * FastMath.DEG_TO_RAD;

        float hDist  =  distance * FastMath.cos(-pitch);
        float vDist  =  distance * FastMath.sin(-pitch);

        float offsetX = -FastMath.sin(yaw) * hDist;
        float offsetZ = -FastMath.cos(yaw) * hDist;
        float offsetY =  vDist + heightOffset;

        Vector3f eye = new Vector3f(
                currentFocus.x + offsetX,
                currentFocus.y + offsetY,
                currentFocus.z + offsetZ);

        camera.setLocation(eye);
        camera.lookAt(currentFocus, Vector3f.UNIT_Y);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static float slerpAngle(float current, float target, float alpha) {
        float diff = target - current;
        while (diff >  FastMath.PI) diff -= FastMath.TWO_PI;
        while (diff < -FastMath.PI) diff += FastMath.TWO_PI;
        return current + diff * alpha;
    }
}