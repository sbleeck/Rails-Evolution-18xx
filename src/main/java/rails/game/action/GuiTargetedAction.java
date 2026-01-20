package rails.game.action;

import java.awt.Color;
import net.sf.rails.game.state.Owner;

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
}