package com.stsaiadvisor.capture;

import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for battle state capture logic.
 * Note: These tests require the game runtime environment (AbstractDungeon, etc.)
 * which is provided as compileOnly dependency. Run these tests only when
 * the game classes are available on the classpath.
 */
public class BattleStateCaptureTest {

    @Ignore("Requires game runtime classes (AbstractDungeon) which are compileOnly")
    @Test
    public void testCaptureNotInBattle() {
        // Without the game running, isInBattle should return false
        BattleStateCapture capture = new BattleStateCapture();
        assertFalse("Should not be in battle without game running", capture.isInBattle());
    }

    @Ignore("Requires game runtime classes (AbstractDungeon) which are compileOnly")
    @Test
    public void testCaptureReturnsNullOutsideBattle() {
        BattleStateCapture capture = new BattleStateCapture();
        assertNull("Should return null when not in battle", capture.capture());
    }

    @Ignore("Requires game runtime classes (AbstractDungeon) which are compileOnly")
    @Test
    public void testGetSummary() {
        BattleStateCapture capture = new BattleStateCapture();
        String summary = capture.getSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("Not in battle"));
    }
}