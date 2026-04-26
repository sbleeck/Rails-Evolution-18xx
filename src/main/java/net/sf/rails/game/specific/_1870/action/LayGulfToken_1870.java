package net.sf.rails.game.specific._1870.action;

import rails.game.action.PossibleAction;
import net.sf.rails.game.RailsRoot;

public class LayGulfToken_1870 extends PossibleAction {
    private static final long serialVersionUID = 1L;
    private final String companyId;
    private String hexId;
    private final boolean isOpen;

    public LayGulfToken_1870(RailsRoot root, String companyId, String hexId, boolean isOpen) {
        super(root);
        this.companyId = companyId;
        this.hexId = hexId;
        this.isOpen = isOpen;
    }

    public String getCompanyId() { return companyId; }
    public String getHexId() { return hexId; }
    public void setHexId(String hexId) { this.hexId = hexId; }
    public boolean isOpen() { return isOpen; }

    @Override
    public String toString() {
        String state = isOpen ? "Open Port" : "Closed Port";
        return "Place Gulf Shipping token (" + state + ")" + (hexId != null ? " on " + hexId : "");
    }
}