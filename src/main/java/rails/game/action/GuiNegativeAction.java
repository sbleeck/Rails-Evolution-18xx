// File: GuiNegativeAction.java
package rails.game.action;

import java.awt.Color;
import net.sf.rails.game.state.Owner;

public class GuiNegativeAction extends PossibleAction implements GuiTargetedAction {

    private static final long serialVersionUID = 1L;
    
    private final PossibleAction wrappedAction;
    private final Owner actor;
    private final String groupLabel;
    private final String buttonLabel;
    
    public GuiNegativeAction(PossibleAction actionToWrap, Owner actor, String groupLabel, String buttonLabel) {
        super(actionToWrap.getRoot()); 
        this.wrappedAction = actionToWrap;
        this.actor = actor;
        this.groupLabel = groupLabel;
        this.buttonLabel = buttonLabel;
    }

    public PossibleAction getWrappedAction() {
        return wrappedAction;
    }

    @Override
    public Owner getActor() {
        return actor;
    }

    @Override
    public String getGroupLabel() {
        return groupLabel;
    }

    @Override
    public String getButtonLabel() {
        return buttonLabel;
    }

    @Override
    public boolean isNegativeAction() {
        return true;
    }
    
    // --- START FIX ---
    // UNIFIED "NEGATIVE" SIGNATURE (White / SlateBlue)
    // Used for "Pass", "Done", "Skip" to match standard UI buttons

    @Override
    public Color getButtonColor() {
        return Color.WHITE;
    }

    @Override
    public Color getHighlightBackgroundColor() {
        return Color.WHITE;
    }

    @Override
    public Color getHighlightBorderColor() {
        return new Color(100, 149, 237); // Cornflower/Slate Blue
    }
    
    @Override
    public Color getHighlightTextColor() {
        return Color.BLACK;
    }
    // --- END FIX ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GuiNegativeAction that = (GuiNegativeAction) o;
        return wrappedAction.equals(that.wrappedAction);
    }

    @Override
    public int hashCode() {
        return wrappedAction.hashCode();
    }
}