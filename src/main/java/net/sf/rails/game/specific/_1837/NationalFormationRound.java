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
        if (nationalId.value() == null) return null;
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

    public void start(PublicCompany_1837 national, boolean isTriggered, String reportName) {
        this.nationalId.set(national.getId());
        this.minorIndex.set(0); 
        this.discardStep.set(false);

        PhaseManager pm = getRoot().getPhaseManager();
        boolean isForced = !national.hasStarted() && pm.hasReachedPhase(national.getForcedStartPhase());
        if ("Sd".equals(national.getId())) isForced = true;
        this.forcedStart.set(isForced);

        ReportBuffer.add(this, LocalText.getText("StartFormationRound", national.getId(), reportName));
    }

    private List<PublicCompany_1837> getSortedMinors() {
        PublicCompany_1837 national = getNational();
        if (national == null) return new ArrayList<>();
        
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

    @Override
    public boolean setPossibleActions() {
        possibleActions.clear();

        PublicCompany_1837 national = getNational();
        
        // 1. DISCARD STEP
        if (discardStep.value()) {
            if (national.getNumberOfTrains() > national.getCurrentTrainLimit()) {
                generateGroupedDiscardActions(national, possibleActions);
                return true;
            } else {
                finishRound();
                return false;
            }
        }

        // 2. EXCHANGE STEPS
        List<PublicCompany_1837> minors = getSortedMinors();
        
        while (minorIndex.value() < minors.size()) {
            PublicCompany_1837 target = minors.get(minorIndex.value());
            
            if (target.isClosed()) {
                minorIndex.add(1);
                continue;
            }
            
            Player owner = target.getPresident();
            if (owner == null) {
                minorIndex.add(1); 
                continue;
            }

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
                NullAction pass = new NullAction(getRoot(), NullAction.Mode.PASS);
                if (isFormation) {
                    pass.setLabel(LocalText.getText("DeclineFormation"));
                } else {
                    pass.setLabel(LocalText.getText("KeepMinor", target.getId()));
                }
                possibleActions.add(pass);
            }

            return true;
        }

        checkTrainLimit();
        return true;
    }

    @Override
    public boolean process(PossibleAction action) {
        if (action instanceof ExchangeMinorAction) {
            ExchangeMinorAction ema = (ExchangeMinorAction) action;
            PublicCompany_1837 national = getNational();

            if (ema.isFormation()) {
                national.start();
                floatCompany(national);
                String msg = LocalText.getText("START_MERGED_COMPANY", national.getId(), 
                             Bank.format(this, national.getIPOPrice()), national.getStartSpace());
                ReportBuffer.add(this, msg);
                DisplayBuffer.add(this, msg);
            }

            processExchange(ema.getMinor(), national);
            
            minorIndex.add(1);
            return true;
        }

        if (action instanceof NullAction) {
            // Case A: Director Declined Formation
            if (minorIndex.value() == 0) {
                log.info("1837_NFR: Formation DECLINED by " + currentPlayer.getName());
                
                // Inform Operating Round directly
                if (gameManager.getInterruptedRound() instanceof OperatingRound_1837) {
                    ((OperatingRound_1837) gameManager.getInterruptedRound())
                        .setNationalFormationDeclined(nationalId.value());
                }
                
                finishRound(); 
                return true;
            }

            // Case B: Player Kept Minor (Pass) -> Move to next
            log.info("1837_NFR: Player kept minor " + getSortedMinors().get(minorIndex.value()).getId());
            minorIndex.add(1);
            return true;
        }

        if (action instanceof DiscardTrain) {
            DiscardTrain dt = (DiscardTrain) action;
            // Use generic execute helper from Round.java if available, or local logic
            executeDiscardTrain(dt);
            checkTrainLimit();
            return true;
        }

        return false;
    }

    private void processExchange(PublicCompany minor, PublicCompany major) {
        log.info("1837_NFR: Merging " + minor.getId() + " into " + major.getId());
        
        // Use Merger1837 class for specific token and asset logic
        Merger1837.mergeMinor(gameManager, minor, major);
        Merger1837.fixDirectorship(gameManager, (PublicCompany_1837)major);
    }
    
    private void checkTrainLimit() {
        PublicCompany national = getNational();
        if (national != null && national.hasStarted() && 
            national.getNumberOfTrains() > national.getCurrentTrainLimit()) {
            
            discardStep.set(true);
            setCurrentPlayer(national.getPresident());
        } else {
            finishRound();
        }
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
    
    @Override
    protected void finishRound() {
        // CRITICAL FIX: Pass 'this' (NFR) to GameManager.
        // Do NOT pass the interrupted round, or GameManager will think the OR is done.
        gameManager.nextRound(this);
    }
}