package cn.popcraft.villagerpro.managers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FollowManagerTest {
    @Test
    void modesCycleDeterministicallyAndRecoverInvalidValues() {
        assertEquals("FOLLOW", FollowManager.nextMode("FREE"));
        assertEquals("STAY", FollowManager.nextMode("FOLLOW"));
        assertEquals("FREE", FollowManager.nextMode("STAY"));
        assertEquals("FREE", FollowManager.nextMode("UNKNOWN"));
        assertEquals("FOLLOW", FollowManager.nextMode(null));
    }
}
