package com.stsaiadvisor.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.stsaiadvisor.util.Constants;

/**
 * Handles keyboard input for the mod.
 * Uses InputProcessor for reliable key detection.
 */
public class KeyInputListener implements InputProcessor {

    private Runnable onTogglePanel;
    private Runnable onRequestAdvice;
    private boolean registered = false;

    // Track key states for polling fallback
    private boolean f3WasPressed = false;
    private boolean f4WasPressed = false;

    public KeyInputListener() {
        System.out.println("[AI Advisor] KeyInputListener created");
    }

    /**
     * Try to register as InputProcessor. Can be called multiple times.
     */
    public void tryRegister() {
        if (registered) return;

        try {
            InputProcessor currentProcessor = Gdx.input.getInputProcessor();
            if (currentProcessor instanceof InputMultiplexer) {
                ((InputMultiplexer) currentProcessor).addProcessor(this);
                System.out.println("[AI Advisor] Added to existing InputMultiplexer");
                registered = true;
            } else if (currentProcessor != null) {
                InputMultiplexer multiplexer = new InputMultiplexer();
                multiplexer.addProcessor(currentProcessor);
                multiplexer.addProcessor(this);
                Gdx.input.setInputProcessor(multiplexer);
                System.out.println("[AI Advisor] Created new InputMultiplexer");
                registered = true;
            } else {
                System.out.println("[AI Advisor] No InputProcessor yet, will retry");
            }
        } catch (Exception e) {
            System.err.println("[AI Advisor] Failed to register InputProcessor: " + e.getMessage());
        }
    }

    /**
     * Set callbacks for key actions.
     */
    public void setCallbacks(Runnable onTogglePanel, Runnable onRequestAdvice) {
        this.onTogglePanel = onTogglePanel;
        this.onRequestAdvice = onRequestAdvice;
    }

    /**
     * Polling method - call this each frame.
     * Uses isKeyPressed with state tracking to emulate isKeyJustPressed.
     */
    public void pollInput() {
        // Try to register if not already
        if (!registered) {
            tryRegister();
        }

        // Use polling with state tracking
        // Note: We use scan codes (246, 247) which match what the game reports
        boolean f4IsPressed = Gdx.input.isKeyPressed(Constants.HOTKEY_TOGGLE_PANEL);
        boolean f3IsPressed = Gdx.input.isKeyPressed(Constants.HOTKEY_REQUEST_ADVICE);

        // Detect "just pressed" - pressed now but wasn't last frame
        if (f4IsPressed && !f4WasPressed) {
            System.out.println("[AI Advisor] F4 just pressed via polling");
            if (onTogglePanel != null) {
                onTogglePanel.run();
            }
        }

        if (f3IsPressed && !f3WasPressed) {
            System.out.println("[AI Advisor] F3 just pressed via polling");
            if (onRequestAdvice != null) {
                onRequestAdvice.run();
            }
        }

        // Update state for next frame
        f4WasPressed = f4IsPressed;
        f3WasPressed = f3IsPressed;
    }

    @Override
    public boolean keyDown(int keycode) {
        // Handle via InputProcessor
        System.out.println("[AI Advisor] keyDown: keycode=" + keycode + " (F3=" + Constants.HOTKEY_REQUEST_ADVICE + ", F4=" + Constants.HOTKEY_TOGGLE_PANEL + ")");

        if (keycode == Constants.HOTKEY_TOGGLE_PANEL) {
            System.out.println("[AI Advisor] F4 pressed - toggle panel");
            if (onTogglePanel != null) {
                onTogglePanel.run();
            }
            return true;
        }

        if (keycode == Constants.HOTKEY_REQUEST_ADVICE) {
            System.out.println("[AI Advisor] F3 pressed - request advice");
            if (onRequestAdvice != null) {
                onRequestAdvice.run();
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }

    @Override
    public boolean scrolled(int amount) {
        return false;
    }
}