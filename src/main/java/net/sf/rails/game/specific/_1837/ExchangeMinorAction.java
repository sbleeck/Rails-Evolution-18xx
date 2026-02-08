package net.sf.rails.game.specific._1837;

import java.awt.Color;
import java.io.IOException;
import java.io.ObjectInputStream;
import com.google.common.base.Objects;

import net.sf.rails.game.CompanyManager;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.state.Owner;
import rails.game.action.GuiTargetedAction;
import rails.game.action.PossibleAction;

/**
 * Generic action for exchanging a minor for a share in a major.
 * Used for: Coal Companies (CoalExchangeRound) AND KK Formation (OperatingRound).
 */
public class ExchangeMinorAction extends PossibleAction implements GuiTargetedAction {

    private static final long serialVersionUID = 1L;
    
    private transient PublicCompany minor;
    private transient PublicCompany targetMajor;
    
    private String minorId;
    private String targetMajorId;
    private boolean isFormation; // True if this action forms the major (e.g. KK1)

    public ExchangeMinorAction(PublicCompany minor, PublicCompany targetMajor, boolean isFormation) {
        super(minor.getRoot());
        this.minor = minor;
        this.targetMajor = targetMajor;
        this.minorId = minor.getId();
        this.targetMajorId = targetMajor.getId();
        this.isFormation = isFormation;
    }

    public PublicCompany getMinor() {
        return minor;
    }

    public PublicCompany getTargetMajor() {
        return targetMajor;
    }
    
    public boolean isFormation() {
        return isFormation;
    }

    @Override
    public Owner getActor() {
        // The action is performed by the Minor (via its President)
        return minor;
    }

    @Override
    public String getGroupLabel() {
        return "Exchange " + minor.getType().getId();
    }

    @Override
    public String getButtonLabel() {
        if (isFormation) {
            return "Form " + targetMajorId + " (" + minorId + ")";
        }
        return "Exchange " + minorId + " for " + targetMajorId;
    }

    @Override
    public Color getButtonColor() {
        // Use Green for Formation/Exchange to indicate positive action
        return new Color(152, 251, 152); // PaleGreen
    }

    @Override
    public boolean equalsAs(PossibleAction pa, boolean asOption) {
        if (pa == this) return true;
        if (!super.equalsAs(pa, asOption)) return false;
        ExchangeMinorAction other = (ExchangeMinorAction) pa;
        return Objects.equal(this.minorId, other.minorId)
            && Objects.equal(this.targetMajorId, other.targetMajorId)
            && this.isFormation == other.isFormation;
    }
    
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        CompanyManager cm = getCompanyManager();
        this.minor = cm.getPublicCompany(minorId);
        this.targetMajor = cm.getPublicCompany(targetMajorId);
    }
}