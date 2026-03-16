package net.sf.rails.game.specific._1817;

import net.sf.rails.game.OperatingRound;
import net.sf.rails.game.GameManager;
import net.sf.rails.game.Company;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.MapHex;
import net.sf.rails.game.special.SpecialBaseTokenLay;
import net.sf.rails.game.special.SpecialProperty;
import net.sf.rails.game.special.SpecialTileLay;

import rails.game.action.LayTile;
import rails.game.action.PossibleAction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.rails.game.StartRound;
import net.sf.rails.game.StartItem;
import net.sf.rails.game.Player;
import net.sf.rails.game.RailsItem;
import net.sf.rails.game.financial.Bank;
import net.sf.rails.common.LocalText;
import net.sf.rails.common.DisplayBuffer;
import net.sf.rails.common.ReportBuffer;
import net.sf.rails.common.GameOption;

import rails.game.action.BuyStartItem;
import rails.game.action.StartItemAction;
import rails.game.action.NullAction;

import java.util.Collection;
import java.util.List;

/**
 * Operating Round specifically for 1830.
 * Handles the "Use it or Lose it" rule for the Delaware & Hudson private
 * company.
 */
public class OperatingRound_1817 extends OperatingRound {

    private static final Logger log = LoggerFactory.getLogger(OperatingRound_1817.class);

    // State to track the loan blackout period (between paying interest and repaying
    // loans)
    protected final net.sf.rails.game.state.BooleanState loanBlackoutPeriod = new net.sf.rails.game.state.BooleanState(
            this, "loanBlackoutPeriod", false);

    protected final net.sf.rails.game.state.IntegerState tilesLaidThisTurn = net.sf.rails.game.state.IntegerState
            .create(this, "tilesLaidThisTurn", 0);
    protected final net.sf.rails.game.state.IntegerState upgradesThisTurn = net.sf.rails.game.state.IntegerState
            .create(this, "upgradesThisTurn", 0);
    protected final net.sf.rails.game.state.ArrayListState<MapHex> hexesLaidThisTurn = new net.sf.rails.game.state.ArrayListState<>(
            this, "hexesLaidThisTurn");
    private String lastLaidTileColour = null;
    private final java.util.Map<String, Integer> hexBaseCosts = new java.util.HashMap<>();

    protected final net.sf.rails.game.state.BooleanState trainBuyingDone = new net.sf.rails.game.state.BooleanState(
            this, "trainBuyingDone", false);

    protected final net.sf.rails.game.state.BooleanState interestPaidThisTurn = new net.sf.rails.game.state.BooleanState(
            this, "interestPaidThisTurn", false);
    protected final net.sf.rails.game.state.BooleanState repayPhaseDoneThisTurn = new net.sf.rails.game.state.BooleanState(
            this, "repayPhaseDoneThisTurn", false);

    // 1817 Financial Sequence Flags
    protected final net.sf.rails.game.state.BooleanState loansRepaidThisTurn = new net.sf.rails.game.state.BooleanState(
            this, "loansRepaidThisTurn", false);

    public OperatingRound_1817(GameManager gameManager, String roundId) {
        super(gameManager, roundId);
    }

    @Override
    protected void finishTurn() {
        // This method is called when the player clicks "Done" or the turn is forced to
        // end.

        super.finishTurn();
    }

    /**
     * Helper to check if the current company has a token on the specified hex name.
     */
    private boolean hasTokenOnHex(Company company, String hexName) {
        if (company == null)
            return false;
        // gameManager is protected in the superclass, so we can access it directly
        MapHex hex = gameManager.getRoot().getMapManager().getHex(hexName);
        if (hex == null)
            return false;

        // Use hasTokenOfCompany, not hasStation
        return hex.hasTokenOfCompany((PublicCompany) company);
    }

    /**
     * Finds the SpecialBaseTokenLay property for D&H and marks it as exercised.
     */
    private void expireDhTokenAbility(Company company) {
        Collection<SpecialProperty> specials = company.getSpecialProperties();

        if (specials == null)
            return;

    }

    @Override
    protected boolean gameSpecificTileLayAllowed(PublicCompany company, MapHex hex, int orientation) {
        if (!super.gameSpecificTileLayAllowed(company, hex, orientation)) {
            return false;
        }
        // Rule 6.3: A Company may not lay a yellow tile and upgrade it during the same
        // turn.
        if (hexesLaidThisTurn.contains(hex)) {
            return false;
        }
        return true;
    }

    @Override
    public int getTileLayCost(PublicCompany company, MapHex hex, int standardCost) {
        int cost = super.getTileLayCost(company, hex, standardCost);
        // Capture the raw terrain cost from the XML before any dynamic surcharges are
        // applied
        hexBaseCosts.put(hex.getId(), standardCost);

        // Rule 6.3: Second tile operation costs an additional $20.
        if (tilesLaidThisTurn.value() > 0) {
            int extraCost = 20;
            cost += extraCost;
        } else {
        }

        return cost;
    }// We are modifying OperatingRound_1817.java

    @Override
    public boolean layTile(LayTile action) {
        MapHex hex = action.getChosenHex();

        lastLaidTileColour = null;
        // super.layTile() calls registerNormalTileLay() -> updateAllowedTileColours()
        boolean success = super.layTile(action);

        if (success && action.getType() != LayTile.CORRECTION) {
            // Rule 6.3: Prevent upgrading the exact same hex twice in one turn.
            hexesLaidThisTurn.add(hex);

            // Rule 1.2.6: Mountain Engineers Bonus
            if ("Yellow".equalsIgnoreCase(lastLaidTileColour)) {
                PublicCompany activeCompany = operatingCompany.value();
                Integer baseCost = hexBaseCosts.get(hex.getId());

                // Using the intercepted standardCost = 15 to identify mountains
                if (baseCost != null && baseCost == 15) {
                    for (net.sf.rails.game.PrivateCompany priv : activeCompany.getPortfolioModel()
                            .getPrivateCompanies()) {
                        if ("MNE40".equals(priv.getId())) {
                            int bonus = 20;
                            log.info("1817_DEBUG: Mountain Engineers (MNE40) triggered for {} on hex {}. Paying $20.",
                                    activeCompany.getId(), hex.getId());
                            net.sf.rails.common.ReportBuffer.add(gameManager,
                                    activeCompany.getId() + " receives $" + bonus
                                            + " from Mountain Engineers for tile on " + hex.getId());
                            if (activeCompany instanceof PublicCompany_1817) {
                                ((PublicCompany_1817) activeCompany).addCashFromBank(bonus,
                                        gameManager.getRoot().getBank());
                            }
                        }
                    }
                }
            }

            // The UI's MapWindow click listener checks the base class's
            // normalTileLaidThisTurn flag.
            // If true, it swallows mouse clicks, assuming the normal lay step is over.
            // We must reset this flag to trick the UI into accepting the second click.
            if (tilesLaidThisTurn.value() < 2) {
                normalTileLaidThisTurn.set(false);
                log.info("1817_TRACE: Reset normalTileLaidThisTurn to unblock UI MapWindow.");
            }
        }

        return success;
    }

    @Override
    protected void updateAllowedTileColours(String colour, int oldAllowedNumber) {

        lastLaidTileColour = colour;
        // 1. Update Rule 6.3 counters based on the tile JUST laid.
        // 'colour' is provided by the base engine as the color of the placed tile.
        if (!"Yellow".equalsIgnoreCase(colour)) {
            upgradesThisTurn.set(upgradesThisTurn.value() + 1);
        }
        tilesLaidThisTurn.set(tilesLaidThisTurn.value() + 1);

        // 2. Clear the map to define the NEXT operation's limits.
        tileLaysPerColour.clear();

        // 3. If we've only used one operation, allow a second one.
        if (tilesLaidThisTurn.value() < 2) {
            // Bypassing base engine case-sensitivity inconsistencies
            tileLaysPerColour.put("yellow", 1);
            tileLaysPerColour.put("Yellow", 1);

            if (upgradesThisTurn.value() == 0) {
                net.sf.rails.game.Phase currentPhase = net.sf.rails.game.Phase.getCurrent(this);
                if (currentPhase.isTileColourAllowed("Green") || currentPhase.isTileColourAllowed("green")) {
                    tileLaysPerColour.put("Green", 1);
                    tileLaysPerColour.put("green", 1);
                }
                if (currentPhase.isTileColourAllowed("Brown") || currentPhase.isTileColourAllowed("brown")) {
                    tileLaysPerColour.put("Brown", 1);
                    tileLaysPerColour.put("brown", 1);
                }
                if (currentPhase.isTileColourAllowed("Grey") || currentPhase.isTileColourAllowed("grey")) {
                    tileLaysPerColour.put("Grey", 1);
                    tileLaysPerColour.put("grey", 1);
                }
            }
        }

    }

    @Override
    public boolean processGameSpecificAction(PossibleAction action) {
        log.info("1817_TRACE: processGameSpecificAction() called with action: {}", action.getClass().getSimpleName());

        if (action instanceof net.sf.rails.game.specific._1817.action.TakeLoans_1817) {
            net.sf.rails.game.specific._1817.action.TakeLoans_1817 tlAction = (net.sf.rails.game.specific._1817.action.TakeLoans_1817) action;
            PublicCompany comp = companyManager.getPublicCompany(tlAction.getCompanyId());
            int count = tlAction.getLoansToTake();

            log.info("1817_TRACE: Processing TakeLoans_1817. Company: {}, Count: {}", comp.getId(), count);

            if (count > 0) {
                if (comp.isClosed() || (comp.hasStockPrice() && comp.getCurrentSpace().getPrice() == 0)) {
                    log.error("1817_ERROR: Company {} in liquidation cannot take loans.", comp.getId());
                    return false;
                }

                // Rule 6.1: Cannot take loans between paying interest and repaying loans
                if (interestPaidThisTurn.value() && !repayPhaseDoneThisTurn.value()) {
                    log.error("1817_ERROR: Company {} attempted to take loans during the blackout period.",
                            comp.getId());
                    return false;
                }

                int currentLoans = comp.getNumberOfBonds();
                if (currentLoans + count > tlAction.getMaxLoansAllowed()) {
                    log.error("1817_ERROR: {} loan capacity exceeded. Current: {}, Requested: {}, Max: {}",
                            comp.getId(), currentLoans, count, tlAction.getMaxLoansAllowed());
                    return false;
                }

                if (comp instanceof net.sf.rails.game.specific._1817.PublicCompany_1817) {
                    net.sf.rails.game.specific._1817.PublicCompany_1817 comp1817 = (net.sf.rails.game.specific._1817.PublicCompany_1817) comp;
                    comp1817.setNumberOfBonds(currentLoans + count);
                    int loanAmount = count * 100;
                    comp1817.addCashFromBank(loanAmount, gameManager.getRoot().getBank());

                    // TODO: Implement stock price movement left one space (unless in leftmost gray
                    // area)
                    log.warn("1817_TODO: Move stock price left for {} loans taken by {}.", count, comp.getId());

                    net.sf.rails.common.ReportBuffer.add(this, comp.getId() + " took " + count + " loans.");
                    log.info("1817_TRACE: TakeLoans_1817 successful for {}.", comp.getId());
                    return true;
                }
            }
            return false;
        }

        if (action instanceof net.sf.rails.game.specific._1817.action.PayLoanInterest_1817) {
            net.sf.rails.game.specific._1817.action.PayLoanInterest_1817 payAction = (net.sf.rails.game.specific._1817.action.PayLoanInterest_1817) action;
            PublicCompany comp = companyManager.getPublicCompany(payAction.getCompanyName());

            log.info("1817_TRACE: Processing PayLoanInterest_1817. Company: {}, Interest Due: {}, Cash: {}",
                    comp.getId(), payAction.getInterestDue(), comp.getCash());

            if (comp.getCash() >= payAction.getInterestDue()) {
                net.sf.rails.game.state.Currency.toBank(comp, payAction.getInterestDue());
                net.sf.rails.common.ReportBuffer.add(this,
                        comp.getId() + " pays $" + payAction.getInterestDue() + " in loan interest.");
                interestPaidThisTurn.set(true);
                log.info("1817_TRACE: PayLoanInterest_1817 successful. State interestPaidThisTurn set to TRUE.");
                return true;
            } else {
                log.error("1817_ERROR: {} does not have enough cash (${}) to pay interest (${}).", comp.getId(),
                        comp.getCash(), payAction.getInterestDue());
            }
            return false;
        }

        if (action instanceof net.sf.rails.game.specific._1817.action.RepayLoans_1817) {
            net.sf.rails.game.specific._1817.action.RepayLoans_1817 rlAction = (net.sf.rails.game.specific._1817.action.RepayLoans_1817) action;
            net.sf.rails.game.PublicCompany comp = companyManager.getPublicCompany(rlAction.getCompanyId());
            int count = rlAction.getLoansToRepay();

            log.info("1817_TRACE: Processing RepayLoans_1817. Company: {}, Loans to Repay: {}", comp.getId(), count);

            if (count > 0) {
                int cost = count * 100;
                if (comp.getCash() >= cost) {
                    net.sf.rails.game.state.Currency.toBank(comp, cost);
                    if (comp instanceof net.sf.rails.game.specific._1817.PublicCompany_1817) {
                        net.sf.rails.game.specific._1817.PublicCompany_1817 comp1817 = (net.sf.rails.game.specific._1817.PublicCompany_1817) comp;
                        comp1817.setNumberOfBonds(comp1817.getNumberOfBonds() - count);

                        // TODO: Implement stock price movement right one space (unless at $600)
                        log.warn("1817_TODO: Move stock price right for {} loans repaid by {}.", count, comp.getId());
                    }
                    net.sf.rails.common.ReportBuffer.add(this,
                            comp.getId() + " repays " + count + " loan(s) for $" + cost + ".");
                    log.info("1817_TRACE: RepayLoans_1817 successful for {}.", comp.getId());
                    return true;
                } else {
                    log.error("1817_ERROR: {} lacks cash to repay {} loans. Needed: {}, Has: {}", comp.getId(), count,
                            cost, comp.getCash());
                }
            }
            return true;
        }

        log.info("1817_TRACE: Action {} not handled by processGameSpecificAction, delegating to superclass.",
                action.getClass().getSimpleName());
        return super.processGameSpecificAction(action);
    }

    // We are modifying OperatingRound_1817.java 'TEST'


    @Override
    protected void initTurn() {
        super.initTurn();
        interestPaidThisTurn.set(false);
        repayPhaseDoneThisTurn.set(false);
        trainBuyingDone.set(false);
        tilesLaidThisTurn.set(0);
        upgradesThisTurn.set(0);
        hexesLaidThisTurn.clear();
        log.info("1817_TRACE: Turn initialized for {}. Flags reset.", operatingCompany.value().getId());
    }

    @Override
    public boolean process(PossibleAction action) {
        if (action instanceof rails.game.action.NullAction) {
            rails.game.action.NullAction nullAction = (rails.game.action.NullAction) action;
            if (nullAction.getMode() == rails.game.action.NullAction.Mode.DONE
                    || nullAction.getMode() == rails.game.action.NullAction.Mode.SKIP) {
                if (getStep() == net.sf.rails.game.GameDef.OrStep.BUY_TRAIN) {

                    if (!trainBuyingDone.value()) {
                        log.info("1817_TRACE: Intercepted DONE/SKIP. Finalizing Train Phase.");
                        trainBuyingDone.set(true);
                        return true; // Stay in OR to process interest
                    }

                    if (interestPaidThisTurn.value() && !repayPhaseDoneThisTurn.value()) {
                        log.info("1817_TRACE: Intercepted DONE/SKIP. Finalizing Repayment Phase.");
                        repayPhaseDoneThisTurn.set(true);
                        return true; // Stay in OR for post-repayment loan window
                    }
                }
            }
        }
        return super.process(action);
    }

    @Override
    public boolean setPossibleActions() {
        boolean actionsAdded = super.setPossibleActions();
        PublicCompany comp = operatingCompany.value();
        net.sf.rails.game.GameDef.OrStep step = getStep();

        if (!(comp instanceof net.sf.rails.game.specific._1817.PublicCompany_1817))
            return actionsAdded;
        net.sf.rails.game.specific._1817.PublicCompany_1817 comp1817 = (net.sf.rails.game.specific._1817.PublicCompany_1817) comp;

        int currentLoans = comp1817.getNumberOfBonds();
        int maxLoans = comp1817.getShareCount();

        // 1. Take Loans (Available until the final DONE, except in the interest-repay
        // blackout)
        boolean inBlackout = (interestPaidThisTurn.value() && !repayPhaseDoneThisTurn.value());
        if (!inBlackout && currentLoans < maxLoans) {
            possibleActions.add(
                    new net.sf.rails.game.specific._1817.action.TakeLoans_1817(getRoot(), comp1817.getId(), maxLoans));
            actionsAdded = true;
        }

        // 2. Financial Sequence Logic
        if (step == net.sf.rails.game.GameDef.OrStep.BUY_TRAIN) {

            // State A: Buying Trains (Normal engine actions)
            if (!trainBuyingDone.value()) {
                log.info("1817_TRACE: Phase - Buying Trains.");
                // Let super.setPossibleActions() handle BuyTrain actions
            }

            // State B: Interest Phase
            if (trainBuyingDone.value() && !interestPaidThisTurn.value()) {
                if (currentLoans == 0) {
                    log.info("1817_TRACE: 0 loans. Auto-advancing to Interest Paid.");
                    interestPaidThisTurn.set(true);
                } else {
                    log.info("1817_TRACE: Phase - Paying Interest.");
                    possibleActions.clear(); // Block train buying/done until interest is handled
                    int interestDue = currentLoans * 5; // TODO: Dynamic rate
                    if (comp1817.getCash() >= interestDue) {
                        possibleActions.add(new net.sf.rails.game.specific._1817.action.PayLoanInterest_1817(getRoot(),
                                comp1817.getId(), interestDue));
                    } else {
                        possibleActions.add(new net.sf.rails.game.specific._1817.action.LiquidateCompany_1817(getRoot(),
                                comp1817.getId(), interestDue));
                    }
                    return true;
                }
            }

            // State C: Repayment Phase
            if (interestPaidThisTurn.value() && !repayPhaseDoneThisTurn.value()) {
                if (currentLoans == 0 || comp1817.getCash() < 100) {
                    log.info("1817_TRACE: Repayment impossible. Auto-advancing.");
                    repayPhaseDoneThisTurn.set(true);
                } else {
                    log.info("1817_TRACE: Phase - Repaying Loans.");
                    possibleActions.clear();
                    int maxRepay = Math.min(currentLoans, comp1817.getCash() / 100);
                    possibleActions.add(new net.sf.rails.game.specific._1817.action.RepayLoans_1817(getRoot(),
                            comp1817.getId(), maxRepay));
                    possibleActions
                            .add(new rails.game.action.NullAction(getRoot(), rails.game.action.NullAction.Mode.DONE));
                    return true;
                }
            }

            // State D: Post-Repayment / Final Window
            if (repayPhaseDoneThisTurn.value()) {
                log.info("1817_TRACE: Phase - Post-Repayment Final Window.");
                // Ensure only loans and a single DONE action are present
                for (rails.game.action.BuyTrain pa : possibleActions.getType(rails.game.action.BuyTrain.class))
                    possibleActions.remove(pa);
                if (!possibleActions.contains(rails.game.action.NullAction.class)) {
                    possibleActions
                            .add(new rails.game.action.NullAction(getRoot(), rails.game.action.NullAction.Mode.DONE));
                }
            }
        }
        return actionsAdded;
    }
    // --- END FIX ---

}