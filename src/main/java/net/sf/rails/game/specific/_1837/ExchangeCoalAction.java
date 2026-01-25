// File: ExchangeCoalAction.java
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

public class ExchangeCoalAction extends PossibleAction implements GuiTargetedAction {

    private static final long serialVersionUID = 1L;
    
    private transient PublicCompany coalCompany;
    private transient PublicCompany targetMajor;
    
    private String coalCompanyId;
    private String targetMajorId;

    public ExchangeCoalAction(PublicCompany coalCompany, PublicCompany targetMajor) {
        super(coalCompany.getRoot());
        this.coalCompany = coalCompany;
        this.targetMajor = targetMajor;
        this.coalCompanyId = coalCompany.getId();
        this.targetMajorId = targetMajor.getId();
    }

    public PublicCompany getCoalCompany() {
        return coalCompany;
    }

    public PublicCompany getTargetMajor() {
        return targetMajor;
    }

    @Override
    public Owner getActor() {
        return coalCompany;
    }

    @Override
    public String getGroupLabel() {
        return "Exchange " + coalCompany.getType().getId();
    }

    @Override
    public String getButtonLabel() {
        return "Exchange " + coalCompanyId + " for " + targetMajorId;
    }

    @Override
    public Color getButtonColor() {
        return new Color(100, 149, 237); // Cornflower Blue
    }

    @Override
    public boolean equalsAs(PossibleAction pa, boolean asOption) {
        if (pa == this) return true;
        if (!super.equalsAs(pa, asOption)) return false;
        ExchangeCoalAction other = (ExchangeCoalAction) pa;
        return Objects.equal(this.coalCompanyId, other.coalCompanyId)
            && Objects.equal(this.targetMajorId, other.targetMajorId);
    }
    
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        CompanyManager cm = getCompanyManager();
        this.coalCompany = cm.getPublicCompany(coalCompanyId);
        this.targetMajor = cm.getPublicCompany(targetMajorId);
    }
}