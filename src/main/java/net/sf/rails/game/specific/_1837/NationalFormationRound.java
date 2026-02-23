package net.sf.rails.game.specific._1837;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.sf.rails.game.*;
import net.sf.rails.game.financial.Bank;
import net.sf.rails.game.state.*;
import net.sf.rails.common.LocalText;
import net.sf.rails.common.ReportBuffer;
import net.sf.rails.common.DisplayBuffer;

import rails.game.action.NullAction;
import rails.game.action.PossibleAction;
import rails.game.action.DiscardTrain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NationalFormationRound extends Round {
    private static final Logger log = LoggerFactory.getLogger(NationalFormationRound.class);

    protected final StringState nationalId = StringState.create(this, "nationalId");
    protected final BooleanState forcedStart = new BooleanState(this, "forcedStart");

    // Tracks which minor we are currently asking (0=K1, 1=K2, 2=K3)
    protected final IntegerState minorIndex = IntegerState.create(this, "MinorIndex", 0);

    // Helper to track if we need to discard trains at the end
    protected final BooleanState discardStep = new BooleanState(this, "DiscardStep", false);

    protected Player currentPlayer;

    public NationalFormationRound(GameManager parent, String id) {
        super(parent, id);
    }

    public PublicCompany_1837 getNational() {
        if (nationalId.value() == null)
            return null;
        return (PublicCompany_1837) companyManager.getPublicCompany(nationalId.value());
    }

    // --- STATIC HELPERS REQUIRED BY GameManager_1837 ---
    public static boolean nationalIsComplete(PublicCompany_1837 national) {
        for (PublicCompany company : national.getMinors()) {
            if (!company.isClosed())
                return false;
        }
        return true;
    }

    public static boolean presidencyIsInPool(PublicCompany_1837 national) {
        return national.getPresidentsShare().getOwner() == national.getRoot().getBank().getPool();
    }
    // ---------------------------------------------------


    private List<PublicCompany_1837> getSortedMinors() {
        PublicCompany_1837 national = getNational();
        if (national == null)
            return new ArrayList<>();

        List<PublicCompany_1837> list = new ArrayList<>();
        for (PublicCompany c : national.getMinors()) {
            if (c instanceof PublicCompany_1837) {
                list.add((PublicCompany_1837) c);
            }
        }
        // Strict Sort by ID (K1, K2, K3) to ensure correct order of asking
        Collections.sort(list, Comparator.comparing(PublicCompany::getId));
        return list;
    }

    /**
     * Pure check to see if the national is over the limit.
     */
    private boolean isOverTrainLimit() {
        PublicCompany national = getNational();
        return (national != null && national.hasStarted() &&
                national.getNumberOfTrains() > national.getCurrentTrainLimit());
    }

    @Override
    protected void floatCompany(PublicCompany company) {
        if (!company.hasFloated()) {
            company.setFloated();
        }
    }

    private void setCurrentPlayer(Player p) {
        this.currentPlayer = p;
        playerManager.setCurrentPlayer(p);
    }


    private void processExchange(PublicCompany minor, PublicCompany major, ExchangeMinorAction action) {
        log.info("1837_NFR: Merging " + minor.getId() + " into " + major.getId());

if (action.isFormation() && !major.hasFloated()) {
            net.sf.rails.game.financial.StockMarket market = getRoot().getStockMarket();
            net.sf.rails.game.financial.StockSpace parSpace = null;
            
            boolean isKK = "KK".equals(major.getId());
            int targetPar = isKK ? 120 : 175;

            for (net.sf.rails.game.financial.StockSpace ss : market.getStartSpaces()) {
                if (ss.getPrice() == targetPar) {
                    parSpace = ss;
                    break;
                }
            }
            if (parSpace == null) {
                for (int r = 0; r < 50; r++) {
                    for (int c = 0; c < 50; c++) {
                        net.sf.rails.game.financial.StockSpace ss = market.getStockSpace(r, c);
                        if (ss != null && ss.getPrice() == targetPar) {
                            parSpace = ss;
                            break;
                        }
                    }
                    if (parSpace != null) break;
                }
            }
            if (parSpace != null) major.setCurrentSpace(parSpace);
            
            // Inject starting capital!
net.sf.rails.game.state.Currency.fromBank(isKK ? 840 : 875, major);

            major.setFloated();
        }

        Merger1837.mergeMinor(gameManager, minor, major);
        Merger1837.fixDirectorship(gameManager, (PublicCompany_1837) major);
    }



public void start(PublicCompany_1837 national, boolean isTriggered, String reportName) {
        this.nationalId.set(national.getId());
        this.minorIndex.set(0);
        this.discardStep.set(false);

        PhaseManager pm = getRoot().getPhaseManager();

        boolean isForced = pm.hasReachedPhase(national.getForcedMergePhase()) 
                           || (!national.hasStarted() && pm.hasReachedPhase(national.getForcedStartPhase()));


        if ("Sd".equals(national.getId()))
            isForced = true;


if ("KK".equals(national.getId())) {
            boolean has4Plus1 = getRoot().getBank().getPool().getPortfolioModel().getTrainList().stream()
                    .anyMatch(t -> t.getType().getName().equals("4+1"));
            if (!has4Plus1) {
                has4Plus1 = getRoot().getCompanyManager().getAllPublicCompanies().stream()
                        .flatMap(c -> c.getPortfolioModel().getTrainList().stream())
                        .anyMatch(t -> t.getType().getName().equals("4+1"));
            }
            if (has4Plus1) isForced = true;
        }

        
this.forcedStart.set(isForced);
// Suppress redundant engine-triggered NFR prompts if already declined in this OR
        if (!isForced) {
            Object interrupted = gameManager.getInterruptedRound();
            if (interrupted instanceof OperatingRound_1837) {
                OperatingRound_1837 or = (OperatingRound_1837) interrupted;
                if (or.declinedNationals.contains(national.getId())) {
                    log.info("1837_NFR: Skipping auto-triggered NFR because " + national.getId() + " was already declined.");
                    finishRound();
                    return;
                }
            }
        }
        ReportBuffer.add(this, LocalText.getText("StartFormationRound", national.getId(), reportName));
        
        // Immediately check if the first minor is valid or if we need to skip/finish
        advanceToNextValidMinorOrFinish();
    }

    private void advanceToNextValidMinorOrFinish() {
        List<PublicCompany_1837> minors = getSortedMinors();
        while (minorIndex.value() < minors.size()) {
            PublicCompany_1837 target = minors.get(minorIndex.value());
            // If valid, pause here and wait for user input
            if (target != null && !target.isClosed() && target.getPresident() != null) {
                // Auto-process mandatory formations (Phase 5) without prompting the user.
                if (forcedStart.value()) {
                    log.info("1837_NFR: Auto-processing forced exchange for " + target.getId());
                    PublicCompany_1837 national = getNational();
                    boolean isFormation = (minorIndex.value() == 0 && !national.hasStarted());
                    
                    ExchangeMinorAction ema = new ExchangeMinorAction(target, national, isFormation);
                    
                    if (isFormation) {
                        national.start();

                        String msg = LocalText.getText("START_MERGED_COMPANY", national.getId(),
                                Bank.format(this, national.getIPOPrice()), national.getStartSpace());
                        ReportBuffer.add(this, msg);
                        DisplayBuffer.add(this, msg);
                    }
                    
                    processExchange(target, national, ema);
                    minorIndex.add(1);
                    continue;
                }
                
                return;
            }
            // Skip closed or invalid minor
            minorIndex.add(1);
        }

        // We have processed all minors. Check for discard or finish.
        if (isOverTrainLimit()) {
            discardStep.set(true);
            setCurrentPlayer(getNational().getPresident());
        } else {
            finishRound();
        }
    }

@Override
    protected void finishRound() {
        // CRITICAL FIX: Pass 'this' (NFR) to GameManager.
        // Do NOT pass the interrupted round, or GameManager will think the OR is done.
        gameManager.nextRound(this);
    }

@Override
    public boolean setPossibleActions() {
        possibleActions.clear();
        PublicCompany_1837 national = getNational();

        // 1. DISCARD STEP
        if (discardStep.value()) {
            if (isOverTrainLimit()) {
                generateGroupedDiscardActions(national, possibleActions);
                return true;
            }
            return false;
        }

        // 2. EXCHANGE STEPS (Purely declarative, no state mutation)
        List<PublicCompany_1837> minors = getSortedMinors();
        if (minorIndex.value() < minors.size()) {
            PublicCompany_1837 target = minors.get(minorIndex.value());
            Player owner = target.getPresident();
            
            setCurrentPlayer(owner);

            boolean isFormation = (minorIndex.value() == 0 && !national.hasStarted());
            ExchangeMinorAction exchange = new ExchangeMinorAction(target, national, isFormation);

            if (isFormation) {
                exchange.setButtonLabel(LocalText.getText("FormCompany", national.getId()));
            } else {
                exchange.setButtonLabel(LocalText.getText("ExchangeMinorForShare", target.getId(), national.getId()));
            }
            possibleActions.add(exchange);
if (!forcedStart.value()) {
                NullAction done = new NullAction(getRoot(), NullAction.Mode.DONE);
                if (isFormation) {
                    done.setLabel(LocalText.getText("DeclineFormation"));
                } else {
                    done.setLabel(LocalText.getText("KeepMinor", target.getId()));
                }
                possibleActions.add(done);
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean process(PossibleAction action) {
        if (action instanceof ExchangeMinorAction) {
            ExchangeMinorAction ema = (ExchangeMinorAction) action;
            PublicCompany_1837 national = getNational();

            if (ema.isFormation()) {
                national.start();
                String msg = LocalText.getText("START_MERGED_COMPANY", national.getId(),
                        Bank.format(this, national.getIPOPrice()), national.getStartSpace());
                ReportBuffer.add(this, msg);
                DisplayBuffer.add(this, msg);
            }

processExchange(ema.getMinor(), national, ema);

            minorIndex.add(1);
            advanceToNextValidMinorOrFinish();
            return true;
        }

        if (action instanceof NullAction) {

// Register decline to prevent re-prompting in the same OR for ANY minor
            if (gameManager.getInterruptedRound() instanceof OperatingRound_1837) {
                ((OperatingRound_1837) gameManager.getInterruptedRound())
                        .setNationalFormationDeclined(nationalId.value());
            }


            // Case A: Director Declined Formation
            if (minorIndex.value() == 0 && !getNational().hasStarted()) {
                log.info("1837_NFR: Formation DECLINED by " + currentPlayer.getName());
                if (gameManager.getInterruptedRound() instanceof OperatingRound_1837) {
                    ((OperatingRound_1837) gameManager.getInterruptedRound())
                            .setNationalFormationDeclined(nationalId.value());
                }
                finishRound();
                return true;
            }

            // Case B: Player Kept Minor
            log.info("1837_NFR: Player passed/done on minor " + getSortedMinors().get(minorIndex.value()).getId());
            minorIndex.add(1);
            advanceToNextValidMinorOrFinish();
            return true;
        }

        if (action instanceof DiscardTrain) {
            DiscardTrain dt = (DiscardTrain) action;
            executeDiscardTrain(dt);
            if (!isOverTrainLimit()) {
                finishRound();
            }
            return true;
        }

        return false;
    }



}