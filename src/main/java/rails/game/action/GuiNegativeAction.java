package rails.game.action;

import java.awt.Color;
import net.sf.rails.game.state.Owner;

/**
 * A wrapper that turns a generic action (like NullAction/Pass) into a 
 * GuiTargetedAction with explicit "Negative" UI semantics (No/Done/Skip).
 */
public class GuiNegativeAction extends PossibleAction implements GuiTargetedAction {

    private static final long serialVersionUID = 1L;
    
    private final PossibleAction wrappedAction;
    private final Owner actor;
    private final String groupLabel;
    private final String buttonLabel;
    
    public GuiNegativeAction(PossibleAction actionToWrap, Owner actor, String groupLabel, String buttonLabel) {
        super(actionToWrap.getRoot()); // Bind to same root
        this.wrappedAction = actionToWrap;
        this.actor = actor;
        this.groupLabel = groupLabel;
        this.buttonLabel = buttonLabel;
    }

    public PossibleAction getWrappedAction() {
        return wrappedAction;
    }

    // --- Delegate Standard PossibleAction Methods ---
    // We want the engine to execute the *wrapped* action, not this wrapper,
    // but often the UI sends the wrapper. The UI Manager must handle unwrapping 
    // or this class must behave like the original.
    // Ideally, ORUIManager should call getWrappedAction() before processing.

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
    public Color getButtonColor() {
        // Standard "No" color (Light Gray or White)
        return Color.WHITE;
    }

    @Override
    public boolean isNegativeAction() {
        return true;
    }
    
    // Ensure equality checks delegation if needed
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