package rails.game.action;

import java.awt.Color;
import net.sf.rails.game.state.Owner;
import java.awt.event.KeyEvent;


public interface GuiTargetedAction {

    Owner getActor();
    String getGroupLabel();
    String getButtonLabel();
    Color getButtonColor();

    default boolean isNegativeAction() {
        return false;
    }

    // --- COMPATIBILITY LAYER (Fixes GameStatus errors) ---
    
    /** Legacy alias for getActor() */
    default Object getTarget() {
        return getActor();
    }

    /** Legacy alias for getButtonColor() */
    default Color getHighlightColor() {
        return getButtonColor();
    }
    
    /** Legacy alias for getGroupLabel() */
    default String getInfoText() {
        return getGroupLabel();
    }

    // Optional addition to GuiTargetedAction.java
default String getToolTip() {
    return null; // ORPanel will ignore null, or fall back to toString()
}

// --- VISUAL SIGNATURE PROTOCOL ---
    
    /** * The fill color for the UI element (Button or Card).
     * Defaults to the existing getButtonColor() for compatibility.
     */
    default Color getHighlightBackgroundColor() {
        return getButtonColor();
    }

    /** * The stroke color for the UI element's border.
     * Defaults to a darker version of the background for contrast.
     */
    default Color getHighlightBorderColor() {
        Color bg = getHighlightBackgroundColor();
        return (bg != null) ? bg.darker() : Color.GRAY;
    }

    /** * The text color. Defaults to Black.
     */
    default Color getHighlightTextColor() {
        return Color.BLACK;
    }

/**
     * Hotkey Management.
     * @return The KeyEvent virtual key code (e.g. KeyEvent.VK_ENTER), or 0 if none.
     */
    default int getHotkey() {
        return 0;
    }

}