package net.sf.rails.game.specific._1870.action;

import rails.game.action.PossibleAction;
import net.sf.rails.game.PrivateCompany;

public class ExchangeMKT_1870 extends PossibleAction {
    private static final long serialVersionUID = 1L;
    private final PrivateCompany mktPrivate;

    public ExchangeMKT_1870(PrivateCompany mktPrivate) {
        super(mktPrivate.getRoot());
        this.mktPrivate = mktPrivate;
        setButtonLabel("Exchange MKT for President's Share");
    }

    public PrivateCompany getMktPrivate() {
        return mktPrivate;
    }
}