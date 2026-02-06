package rails.game.action;

import java.util.HashSet;
import java.util.List;

import net.sf.rails.common.LocalText;
import net.sf.rails.common.ReportBuffer;
import net.sf.rails.game.OperatingRound;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.Train;
import net.sf.rails.game.financial.Bank;
import net.sf.rails.game.state.Purse;

public class DiscardTrainVoluntarily extends DiscardTrain {

    private static final long serialVersionUID = 1L;
    private String customLabel;

    public DiscardTrainVoluntarily(PublicCompany company, List<Train> trains) {
        super(company, new HashSet<>(trains));
    }
    
    // --- FIX 3: Custom Button Label ---
    public void setLabel(String label) {
        this.customLabel = label;
    }

    @Override
    public String toString() {
        return customLabel != null ? customLabel : super.toString();
    }
    // ----------------------------------

    @Override
    public boolean execute(OperatingRound round) {
        Train train = getDiscardedTrain();
        if (train == null) return false;

        // Calculate 50% cost
        int cost = train.getType().getCost() / 2;

        if (cost > 0) {
            Purse companyPurse = company.getPurse();
            Bank bank = Bank.get(round);
            companyPurse.getCurrency().move(company, cost, bank);

            ReportBuffer.add(round, LocalText.getText("PaysToBank", 
                company.getId(), 
                Bank.format(round, cost), 
                "voluntary surrender of " + train.getName()));
        }

        boolean success = super.execute(round);
        return success;
    }
}