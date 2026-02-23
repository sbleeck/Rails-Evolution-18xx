package net.sf.rails.game.specific._1837;

import java.util.*;
import net.sf.rails.game.*;
import net.sf.rails.game.financial.*;
import net.sf.rails.game.state.*;
import rails.game.action.*;
import java.awt.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.sf.rails.game.round.RoundFacade;

public class CoalExchangeRound extends Round implements GuiTargetedAction {
    private static final Logger log = LoggerFactory.getLogger(CoalExchangeRound.class);

    protected final IntegerState currentPlayerIndex = IntegerState.create(this, "CER_PlayerIdx", 0);
    protected final IntegerState playersProcessedCount = IntegerState.create(this, "CER_ProcessedCount", 0);
    protected final ArrayListState<String> skippedMinors = new ArrayListState<>(this, "CER_Skipped");
protected final GenericState<PublicCompany> companyOverLimit = new GenericState<>(this, "CER_CompOverLimit", null);

    public CoalExchangeRound(GameManager parent, String id) {
        super(parent, id);
    }

    public void start() {
        currentPlayerIndex.set(0);
        playersProcessedCount.set(0);
        skippedMinors.clear();
        findNextActivePlayer();
    }

    private void findNextActivePlayer() {
        List<Player> players = gameManager.getPlayers();
        int count = playersProcessedCount.value();
        int idx = currentPlayerIndex.value();

        while (count < players.size()) {
            Player p = players.get(idx);
            if (hasExchangeableMinors(p)) {
                // --- START FIX: Proper player manager access ---
                gameManager.getRoot().getPlayerManager().setCurrentPlayer(p);
                // --- END FIX ---
                setPossibleActions();
                return;
            }
            idx = (idx + 1) % players.size();
            count++;
        }
        finishRound();
    }

    @Override
    public boolean setPossibleActions() {
        possibleActions.clear();
        Player p = gameManager.getCurrentPlayer();
        if (p == null) return false;

        if (companyOverLimit.value() != null) {
            PublicCompany comp = companyOverLimit.value();
            
            // Only the President can discard (which should be the current player)
            if (comp.getPresident() != p) {
                log.warn("State mismatch: Current player is not president of over-limit company.");
            }

            // Generate Discard Actions (Standard DiscardTrain)
            for (Train train : comp.getPortfolioModel().getUniqueTrains()) {
                Set<Train> singleTrainSet = new HashSet<>();
                singleTrainSet.add(train);
                // We use standard DiscardTrain here, not the Voluntary one (no cost/refund usually implied in forced merger cleanup)
                // Rule check: In 1837 forced mergers, can they discard usually? Yes.
                possibleActions.add(new DiscardTrain(comp, singleTrainSet));
            }
            return true;
        }

        Set<String> processed = new HashSet<>();
        for (PublicCertificate cert : p.getPortfolioModel().getCertificates()) {
            PublicCompany comp = cert.getCompany();
            if (comp == null || comp.isClosed() || processed.contains(comp.getId())) continue;
            if (skippedMinors.contains(comp.getId())) continue;

            PublicCompany target = Merger1837.getMergeTarget(gameManager, comp);
            if (target != null && target.hasFloated() && comp.getPresident() == p) {
                possibleActions.add(new ExchangeMinorAction(comp, target, false));
                processed.add(comp.getId());
            }
        }

        NullAction done = new NullAction(getRoot(), NullAction.Mode.DONE);
        done.setLabel("Done / No Exchanges");
        possibleActions.add(done);
        return true;
    }


    private void advancePlayer() {
        currentPlayerIndex.set((currentPlayerIndex.value() + 1) % gameManager.getPlayers().size());
        playersProcessedCount.add(1);
        findNextActivePlayer();
    }

    // Access must be protected to match Round ---
    @Override
    protected void finishRound() {
        gameManager.nextRound(this);
    }


    private boolean hasExchangeableMinors(Player p) {
        if (p == null) return false;
        for (PublicCertificate cert : p.getPortfolioModel().getCertificates()) {
            PublicCompany comp = cert.getCompany();
            if (comp == null || comp.isClosed() || skippedMinors.contains(comp.getId())) continue;
PublicCompany target = Merger1837.getMergeTarget(gameManager, comp);
            if (target != null && target.hasFloated() && comp.getPresident() == p) return true;
        }
        return false;
    }

    // --- GuiTargetedAction Implementation ---
    // --- START FIX: Proper accessor names ---
    @Override public net.sf.rails.game.state.Owner getActor() { return gameManager.getCurrentPlayer(); }
    @Override public String getGroupLabel() { return "Minor Exchanges"; }
    @Override public String getButtonLabel() { return "Exchange"; }
    @Override public Color getButtonColor() { return Color.ORANGE; }



    // ... (lines of unchanged context code) ...
    @Override
    public boolean process(PossibleAction action) {
        if (action instanceof DiscardTrain) {
            DiscardTrain discard = (DiscardTrain) action;
            // Execute the discard (removes train from company)
            // Note: DiscardTrain.execute() expects an OperatingRound usually, 
            // but the base logic might work if we are careful. 
            // Better to rely on a direct helper or ensure DiscardTrain works in Round.
            // Actually, we should probably manually discard here to be safe, 
            // or cast 'this' if DiscardTrain supports it. 
            // DiscardTrain usually needs an OR to put trains in the Open Market or Scrap.
            
            // Implementation: Manual Discard to avoid class cast issues if DiscardTrain is rigid
            Train train = discard.getSelectedTrain();
            if (train != null) {
                PublicCompany comp = discard.getCompany();
                // Move train to Bank (Pool) or Scrap? 
                // 1837 Rule: usually depends on phase, but generally to Bank Pool if not rusted.
                // For safety, let's assume moving to Bank (Available)

                train.getCard().discard();


                // Re-evaluate Limit
                if (comp.getNumberOfTrains() <= comp.getCurrentTrainLimit()) {
                    companyOverLimit.set(null);
                    // Check if player has more exchanges or is done
                    if (!hasExchangeableMinors(gameManager.getCurrentPlayer())) {
                        advancePlayer();
                    } else {
                        setPossibleActions();
                    }
                } else {
                    // Still over limit, regenerate discard buttons
                    setPossibleActions();
                }
            }
            return true;
        }

if (action instanceof ExchangeMinorAction) {
            ExchangeMinorAction exc = (ExchangeMinorAction) action;
            
            Merger1837.mergeMinor(gameManager, exc.getMinor(), exc.getTargetMajor());
            Merger1837.fixDirectorship(gameManager, exc.getTargetMajor());
            
            //  Detect Over Limit ---
            PublicCompany target = exc.getTargetMajor();
            if (target.getNumberOfTrains() > target.getCurrentTrainLimit()) {
                log.info("1837_LOGIC: Company " + target.getId() + " is over train limit (" 
                        + target.getNumberOfTrains() + "/" + target.getCurrentTrainLimit() + "). Enforcing discard.");
                companyOverLimit.set(target);
                setPossibleActions(); // Will now generate discard buttons
                return true;
            }

            if (!hasExchangeableMinors(gameManager.getCurrentPlayer())) {
                advancePlayer();
            } else {
                setPossibleActions();
            }
            return true;
        }

        if (action instanceof NullAction) {
            // We must explicitly mark current options as skipped to prevent loops
            Player p = gameManager.getCurrentPlayer();
            if (p != null) {
                for (PublicCertificate cert : p.getPortfolioModel().getCertificates()) {
                    PublicCompany comp = cert.getCompany();
                    PublicCompany target = Merger1837.getMergeTarget(gameManager, comp);
                    if (comp != null && target != null) {
                        skippedMinors.add(comp.getId());
                    }
                }
            }
            advancePlayer();

            return true;
        }
        return false;
    }







}