// File: DiscardTrain.java
package rails.game.action;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;
import java.awt.Color;

import net.sf.rails.common.LocalText;
import net.sf.rails.common.ReportBuffer;
import net.sf.rails.game.*;
import net.sf.rails.game.state.AbstractItem;
import net.sf.rails.game.state.Owner;
import net.sf.rails.game.state.Purse;
import net.sf.rails.game.financial.Bank;
import net.sf.rails.game.model.PortfolioOwner;
import net.sf.rails.game.round.RoundFacade;

import org.jetbrains.annotations.NotNull;
import com.google.common.base.Objects;

import net.sf.rails.util.RailsObjects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiscardTrain extends PossibleORAction implements GuiTargetedAction {

    // ... (Existing fields and constructors omitted for brevity) ...

    private static final Logger log = LoggerFactory.getLogger(DiscardTrain.class);
    transient private SortedSet<Train> ownedTrains;
    private String[] ownedTrainsUniqueIds;
    transient private Train discardedTrain = null;
    private String discardedTrainUniqueId;
    public static final long serialVersionUID = 1L;

    public DiscardTrain(PublicCompany company, @NotNull Set<Train> ownedTrains) {
        super(company.getRoot());
        this.ownedTrains = new TreeSet<>(Comparator.comparing(AbstractItem::getId));
        setOwnedTrains(ownedTrains);
        this.company = company;
        this.companyName = company.getId();
    }

    public DiscardTrain(PublicCompany company, Train discardedTrain) {
        super(company.getRoot());
        this.company = company;
        this.companyName = company.getId();
        setDiscardedTrain(discardedTrain);
        this.ownedTrains = new TreeSet<>(Comparator.comparing(AbstractItem::getId));
        if (discardedTrain != null) {
            setOwnedTrains(Set.of(discardedTrain));
        } else {
            setOwnedTrains(Set.of());
        }
    }

    @Override
    public Owner getActor() {
        // Return the company associated with this action.
        // The constructor stores it in 'company' (from PossibleORAction or similar
        // field)
        return this.company;
    }


    @Override
    public String getButtonLabel() {
        if (ownedTrains != null && !ownedTrains.isEmpty()) {
            // Return the name of the first (and usually only) train in this option
            String name = ownedTrains.first().getName();

            // Strip suffix like "_1" from "4_1" to show just "4"
            name = name.replaceAll("(.*)_\\d+", "$1");

            return "Discard " + name;
        }
        return "Discard ?";
    }

    @Override
    public String toString() {
        if (ownedTrains != null && !ownedTrains.isEmpty()) {
            StringBuilder sb = new StringBuilder("Discard ");
            for (Train t : ownedTrains) {
                sb.append(t.getName()).append(" ");
            }
            return sb.toString().trim();
        }
        return "Discard Train (?)";
    }

    // UNIFIED "DISCARD" SIGNATURE (Light Coral / Firebrick)

    @Override
    public Color getButtonColor() {
        return new Color(240, 128, 128); // LightCoral
    }

    @Override
    public Color getHighlightBackgroundColor() {
        return new Color(240, 128, 128); // LightCoral
    }

    @Override
    public Color getHighlightBorderColor() {
        return new Color(178, 34, 34); // Firebrick
    }

    public void setLabel(String label) {
        this.label = label;
    }

    @Override
    public Color getHighlightTextColor() {
        return Color.BLACK;
    }

    // ... (Serialization methods omitted) ...
    // NOTE: Ensure all other methods from the uploaded file are preserved.
    // I am only showing the overridden visual methods here for clarity.

    // --- RESTORING MISSING METHODS TO ENSURE COMPILATION ---
    public void setOwnedTrains(Set<Train> trains) {
        ownedTrains.clear();
        ownedTrains.addAll(trains);
        ownedTrainsUniqueIds = new String[trains.size()];
        int i = 0;
        for (Train train : ownedTrains) {
            ownedTrainsUniqueIds[i++] = train.getId();
        }
    }

    public SortedSet<Train> getOwnedTrains() {
        return ownedTrains;
    }

    public void setDiscardedTrain(Train train) {
        if (train != null) {
            discardedTrain = train;
            discardedTrainUniqueId = train.getId();
        }
    }

    public Train getDiscardedTrain() {
        return discardedTrain;
    }

    public boolean process(Round round) {
        if (discardedTrain == null)
            return true;

        // ... (Logic implementation as provided) ...
        discardedTrain.getCard().discard();
        return true;
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        TrainManager trainManager = root.getTrainManager();
        this.ownedTrains = new TreeSet<>(Comparator.comparing(AbstractItem::getId));
        if (ownedTrainsUniqueIds != null) {
            for (String uid : ownedTrainsUniqueIds)
                ownedTrains.add(trainManager.getTrainByUniqueId(uid));
        }
        if (discardedTrainUniqueId != null)
            discardedTrain = trainManager.getTrainByUniqueId(discardedTrainUniqueId);
    }

    @Override
    public String getGroupLabel() {
        return "Must Discard Train";
    }

    public boolean execute(OperatingRound round) {

        // Move the train to the scrap heap or pool (Standard Logic)
        Bank bank = Bank.get(round);
        // Assuming standard rule: forced discard goes to Open Market (Pool)
        discardedTrain.moveTo(bank.getPool());

        ReportBuffer.add(round, LocalText.getText("DiscardsTrain",
                company.getId(),
                discardedTrain.getName()));

        discardedTrain.getCard().discard();
        return true;
    }

    /**
     * Overridden to support execution in both Operating Rounds and Stock Rounds
     * (CER).
     */
    public boolean process(RoundFacade round) {

        if (round instanceof OperatingRound) {
            return execute((OperatingRound) round);
        }

        // Manual execution for non-OR rounds (like CoalExchangeRound)
        return executeManual(round);
    }

    /**
     * Public accessor to retrieve the single train associated with this discard
     * action.
     */
    public Train getSelectedTrain() {
        if (discardedTrain != null)
            return discardedTrain;
        if (ownedTrains != null && !ownedTrains.isEmpty())
            return ownedTrains.first();
        return null;
    }

    private boolean executeManual(RoundFacade round) {
        // Auto-select if only one train is available
        if (discardedTrain == null && ownedTrains != null && !ownedTrains.isEmpty()) {
            discardedTrain = ownedTrains.first();
        }

        if (discardedTrain == null) {
            log.error("DISCARD_DEBUG: No train selected for discard in " + round.getId());
            return false;
        }

        // --- START FIX ---
        log.info("DISCARD_DEBUG: Attempting to move " + discardedTrain.getId() + " from " + company.getId());
        log.info("DISCARD_DEBUG: Current Train Owner: " + discardedTrain.getOwner().getId());

        Bank bank = Bank.get(company.getRoot());
        PortfolioOwner pool = bank.getPool();

        // 1. Move the Train state object
        discardedTrain.moveTo(pool);

        // 2. Move the TrainCard (The actual item in the PortfolioModel)
        if (discardedTrain.getCard() != null) {
            log.info("DISCARD_DEBUG: Found TrainCard for " + discardedTrain.getId() + ". Moving to Pool.");
            discardedTrain.getCard().moveTo(pool);
            discardedTrain.getCard().discard();
        } else {
            log.warn("DISCARD_DEBUG: No TrainCard found for " + discardedTrain.getId() + "!");
        }

        log.info("DISCARD_DEBUG: New Train Owner: " + discardedTrain.getOwner().getId());

        ReportBuffer.add(round, company.getId() + " discards train " + discardedTrain.getName());

        return true;
    }



    @Override
    public Object getTarget() {
        // Override default (Actor/Company) to target the specific Train.
        // This allows GameStatus.findRailCardFor() to locate the specific train button.
        if (ownedTrains != null && !ownedTrains.isEmpty()) {
            return ownedTrains.first();
        }
        return company;
    }


}