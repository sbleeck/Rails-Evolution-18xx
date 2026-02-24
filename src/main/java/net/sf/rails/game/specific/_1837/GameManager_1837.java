package net.sf.rails.game.specific._1837;

import net.sf.rails.common.*;
import net.sf.rails.game.*;
import net.sf.rails.game.financial.*;
import net.sf.rails.game.round.RoundFacade;
import net.sf.rails.game.state.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author martin, erik
 */
public class GameManager_1837 extends GameManager {

    private static final Logger log = LoggerFactory.getLogger(GameManager_1837.class);

    private StringState newPhaseId = StringState.create(this, "newPhaseId", null);
    protected final GenericState<Round> previousSRorOR = new GenericState<>(this, "previousSRorOR");
    private SetState<String> doneThisRound = HashSetState.create(this, "doneThisRound");
    protected final BooleanState buyOnly = new BooleanState(this, "buyOnly", false);

    protected CompanyManager companyManager;
    protected PhaseManager phaseManager;

    private java.util.List<String> tempSkippedMinors = new java.util.ArrayList<>();

    public java.util.List<String> popTempSkippedMinors() {
        java.util.List<String> list = new java.util.ArrayList<>(tempSkippedMinors);
        tempSkippedMinors.clear();
        return list;
    }

    public GameManager_1837(RailsRoot parent, String id) {
        super(parent, id);
    }

    public void init() {
        super.init();
        companyManager = getRoot().getCompanyManager();
        phaseManager = getRoot().getPhaseManager();
    }

    @Override
    public void newPhaseChecks(RoundFacade round) {
        newPhaseId.set(round.getId());
    }

    public boolean checkAndRunNFR(String newPhaseId, Round namingRound, Round interruptedRound) {
        this.newPhaseId.set(newPhaseId);
        setInterruptedRound(interruptedRound);
        String[] nationalNames = GameDef_1837.Nationals;

        if (nationalNames == null)
            return false;

        for (String nationalName : nationalNames) {
            if (doneThisRound.contains(nationalName))
                continue;
            PublicCompany_1837 national = (PublicCompany_1837) companyManager.getPublicCompany(nationalName);
            if (national == null)
                continue;

            boolean canStart = phaseManager.hasReachedPhase(national.getFormationStartPhase());

            if (nationalName.equals("Ug")) {
                boolean has4E = getRoot().getBank().getPool().getPortfolioModel().getTrainList().stream()
                        .anyMatch(t -> t.getType().getName().equals("4E"));
                if (!has4E) {
                    has4E = companyManager.getAllPublicCompanies().stream()
                            .flatMap(c -> c.getPortfolioModel().getTrainList().stream())
                            .anyMatch(t -> t.getType().getName().equals("4E"));
                }
                if (has4E)
                    canStart = true;
            }
            boolean forced = phaseManager.hasReachedPhase(national.getForcedStartPhase())
                    || phaseManager.hasReachedPhase(national.getForcedMergePhase());


                    if (nationalName.equals("KK") && !NationalFormationRound.nationalIsComplete(national)) {
                boolean hasForcingTrain = getRoot().getBank().getPool().getPortfolioModel().getTrainList().stream()
                        .anyMatch(t -> t.getType().getName().equals("4+1") || t.getType().getName().equals("5"));
                if (!hasForcingTrain) {
                    hasForcingTrain = companyManager.getAllPublicCompanies().stream()
                            .flatMap(c -> c.getPortfolioModel().getTrainList().stream())
                            .anyMatch(t -> t.getType().getName().equals("4+1") || t.getType().getName().equals("5"));
                }
                if (hasForcingTrain) {
                    log.info("1837_TRIGGER: Compulsory KK formation forced by train (4+1 or 5).");
                    forced = true;
                }
            }

            if (nationalName.equals("Ug") && !NationalFormationRound.nationalIsComplete(national)) {
                boolean has5 = getRoot().getBank().getPool().getPortfolioModel().getTrainList().stream()
                        .anyMatch(t -> t.getType().getName().equals("5"));
                if (!has5) {
                    has5 = companyManager.getAllPublicCompanies().stream()
                            .flatMap(c -> c.getPortfolioModel().getTrainList().stream())
                            .anyMatch(t -> t.getType().getName().equals("5"));
                }
                if (has5) {
                    log.info("1837_TRIGGER: Compulsory Ug formation forced by train (5).");
                    forced = true;
                }
            }
            

            if ((canStart || forced) && !NationalFormationRound.nationalIsComplete(national)) {

                if (newPhaseId != null) {
                    if (newPhaseId.equals(national.getFormationStartPhase())
                            && NationalFormationRound.presidencyIsInPool(national)
                            || newPhaseId.equals(national.getForcedStartPhase())
                            || newPhaseId.equals(national.getForcedMergePhase())) {
                        startNationalFormationRound(nationalName);
                        return true;
                    } else {
                        doneThisRound.add(nationalName);
                    }
                } else {
                    startNationalFormationRound(nationalName);
                    return true;
                }
            }
        }
        doneThisRound.clear();
        return false;
    }

    public void startNationalFormationRound(String nationalName) {
        String roundId;
        String nfrReportName;

        Round safeNamingRound = previousSRorOR.value();
        if (safeNamingRound == null) {
            safeNamingRound = (Round) getInterruptedRound();
        }

        if (newPhaseId.value() == null) {
            if (safeNamingRound instanceof OperatingRound_1837) {
                nfrReportName = safeNamingRound.getId().replaceFirst("OR_(\\d+)(\\.\\d+)?", "$1$2");
                if (!nfrReportName.contains("."))
                    nfrReportName += ".1";
            } else if (safeNamingRound instanceof StockRound_1837) {
                nfrReportName = safeNamingRound.getId().replaceFirst("SR_(\\d+)", "$1.0");
            } else {
                nfrReportName = "Recovery";
            }
            roundId = "NFR_" + nationalName + "_" + nfrReportName;
        } else {
            nfrReportName = "phase " + newPhaseId.value();
            roundId = "NFR_" + nationalName + "_phase_" + newPhaseId.value();
        }

        PublicCompany_1837 national = (PublicCompany_1837) companyManager.getPublicCompany(nationalName);
        createRound(NationalFormationRound.class, roundId)
                .start(national, newPhaseId.value() != null, nfrReportName);
    }

    @Override
    protected boolean runIfStartPacketIsNotCompletelySold() {
        StartPacket nextStartPacket = getRoot().getCompanyManager().getNextUnfinishedStartPacket();
        return !(nextStartPacket.getId().equalsIgnoreCase("Coal Mines"));
    }

    @Override
    protected void createStartRound(StartPacket startPacket) {
        String startRoundClassName = startPacket.getRoundClassName();
        startRoundNumber.add(1);
        String variant = GameOption.getValue(this, GameOption.VARIANT);
        if (variant.equalsIgnoreCase("1837-2ndEd.") && buyOnly.value()) {
            startRoundClassName += "_buying";
        }
        StartRound startRound = createRound(startRoundClassName, "startRound_" + startRoundNumber.value());
        startRound.start();
    }

    @Override
    public void setGuiParameters() {
        super.setGuiParameters();
        guiParameters.put(GuiDef.Parm.HAS_SPECIAL_COMPANY_INCOME, true);
    }

    public boolean isBuyOnly() {
        return buyOnly.value();
    }

    public void setNewPhaseId(String newPhaseId) {
        this.newPhaseId.set(newPhaseId);
    }



public boolean checkAndRunCER(String newPhaseId, Round namingRound, Round interruptedRound) {
        if (doneThisRound.contains("CER"))
            return false;

        List<PublicCompany> coalCompanies = getRoot().getCompanyManager().getPublicCompaniesByType("Coal");
        boolean runCER = false;
        if (coalCompanies != null) {
            for (PublicCompany coalComp : coalCompanies) {
                if (coalComp.isClosed())
                    continue;
                PublicCompany target = Merger1837.getMergeTarget(this, coalComp);
                if (target != null && target.hasFloated()) {
                    runCER = true;
                    break;
                }
            }
        }

        if (runCER) {
            try {
                String cerId;
// --- START FIX ---
                // Prioritize the interrupted round for naming to avoid URI collisions
                Round safeNamingRound = interruptedRound;
                if (safeNamingRound == null) {
                    safeNamingRound = namingRound;
                }
                if (safeNamingRound == null) {
                    safeNamingRound = (Round) currentRound.value();
                }

                if (newPhaseId != null) {
                    cerId = "CER_phase_" + newPhaseId;
                } else if (safeNamingRound instanceof StockRound_1837) {
                    cerId = safeNamingRound.getId().replaceFirst("SR_(\\d+)", "CER_$1.0");
                } else if (safeNamingRound != null) {
                    cerId = safeNamingRound.getId().replaceFirst("OR_(\\d+)(\\.\\d+)?", "CER_$1$2");
                    if (!cerId.contains(".")) {
                        cerId += ".1";
                    }
                } else {
                    cerId = "CER_Recovery";
                    // Add timestamp to ensure Root URI uniqueness
                cerId += "_" + System.currentTimeMillis();
                }

                log.info("1837_TRACE: [5-4] Starting CER with ID: " + cerId);
                setInterruptedRound(interruptedRound);
                createRound(CoalExchangeRound.class, cerId).start();
                return true;
            } catch (Throwable t) {
                log.error("1837_CRITICAL: Fatal error during CER creation", t);
                return false;
            }
// --- END FIX ---
        } else {
            doneThisRound.add("CER");
        }
        return false;
    }


    @Override
    public boolean process(rails.game.action.PossibleAction action) {
        if (action instanceof rails.game.action.NullAction) {
            rails.game.action.NullAction incoming = (rails.game.action.NullAction) action;

            // Logic: If the UI sends a SKIP or PASS, we treat them as semantically
            // identical for the purpose of moving the game forward in 1837.
            if (incoming.getMode() == rails.game.action.NullAction.Mode.SKIP ||
                    incoming.getMode() == rails.game.action.NullAction.Mode.PASS) {

                // 1. Get the current RoundFacade from the state model
                net.sf.rails.game.round.RoundFacade facade = currentRound.value();

                // 2. Cast to the concrete Round class to access the action list
                if (facade instanceof net.sf.rails.game.Round) {
                    net.sf.rails.game.Round activeRound = (net.sf.rails.game.Round) facade;

                    for (rails.game.action.PossibleAction valid : activeRound.getPossibleActionsList()) {
                        if (valid instanceof rails.game.action.NullAction) {
                            rails.game.action.NullAction validNa = (rails.game.action.NullAction) valid;

                            // 3. If the server is offering EITHER mode, and the user
                            // sent EITHER mode, we have a match.
                            if (validNa.getMode() == rails.game.action.NullAction.Mode.SKIP ||
                                    validNa.getMode() == rails.game.action.NullAction.Mode.PASS) {

                                log.info("1837_FIX: Normalizing NullAction " + incoming.getMode() +
                                        " -> " + validNa.getMode() + " for " + action.getPlayerName());
                                return super.process(validNa);
                            }
                        }
                    }
                }
            }
        }
        return super.process(action);
    }


@Override
    public void nextRound(Round prevRound) {
        log.info("Transitioning Round. Previous: {} ({})", prevRound.getId(), prevRound.getClass().getSimpleName());

        if (prevRound instanceof StartRound) {
            buyOnly.set(true);
            if (((StartRound) prevRound).getStartPacket().areAllSold()) {
                beginStartRound();
            } else {
                startOperatingRound(runIfStartPacketIsNotCompletelySold());
            }
        } else if (prevRound instanceof CoalExchangeRound) {
            doneThisRound.add("CER");

            if (prevRound instanceof CoalExchangeRound) {
                tempSkippedMinors.clear();
                for (String minorId : ((CoalExchangeRound) prevRound).skippedMinors) {
                    tempSkippedMinors.add(minorId);
                }
            }

            // 1. Check if we have a suspended round to resume (e.g. OR interrupted by formation)
            Round interrupted = (Round) getInterruptedRound();
            if (interrupted != null) {
                log.info("1837_LOGIC: Returning from CER to interrupted round: " + interrupted.getId());
                setInterruptedRound(null); // Clear memory to prevent loops
                setRound(interrupted);
                if (interrupted instanceof OperatingRound_1837) {
                    ((OperatingRound_1837) interrupted).resume();
                } else if (interrupted instanceof StockRound_1837) {
                    ((StockRound_1837) interrupted).resume();
                }
                return;
            }

            // 2. Standard Flow: No interruption, so proceed to next logical round
            boolean cameFromStockRound = (previousSRorOR.value() instanceof StockRound);
            if (cameFromStockRound) {
                Phase currentPhase = getRoot().getPhaseManager().getCurrentPhase();
                if (currentPhase != null) {
                    numOfORs.set(currentPhase.getNumberOfOperatingRounds());
                }
                relativeORNumber.set(0);
                // SR -> CER -> OR
                startOperatingRound(true);
            } else if (relativeORNumber.value() < numOfORs.value()) {
                // OR -> CER -> OR (next in sequence)
                startOperatingRound(true);
            } else {
                // OR -> CER -> SR
                startStockRound();
            }

        } else if (prevRound instanceof NationalFormationRound) {
log.info("1837_TRACE: [1] Entering NFR block");
            PublicCompany_1837 national = ((NationalFormationRound) prevRound).getNational();
            log.info("1837_TRACE: [2] National Company: " + (national != null ? national.getId() : "null"));
            
            if (national != null) {
                doneThisRound.add(national.getId());
                log.info("1837_TRACE: [3] Added to doneThisRound");
            }
            
            Round interruptedRound = (Round) getInterruptedRound();
            log.info("1837_TRACE: [4] InterruptedRound retrieved: " + (interruptedRound != null ? interruptedRound.getId() : "null"));

           try {
                log.info("1837_TRACE: [5] Checking CER");
                if (checkAndRunCER(newPhaseId.value(), previousSRorOR.value(), interruptedRound)) {
                    log.info("1837_TRACE: [5a] Transitioning to CER");
                    return;
                }
            } catch (Throwable t) {
                log.error("1837_FIX: Throwable in nextRound while checking CER", t);
            }

            try {
                log.info("1837_TRACE: [6] Checking subsequent NFR");
                if (checkAndRunNFR(newPhaseId.value(), previousSRorOR.value(), interruptedRound)) {
                    log.info("1837_TRACE: [6a] Transitioning to NFR");
                    return;
                }
            } catch (Throwable t) {
                log.error("1837_FIX: Throwable in nextRound while checking NFR", t);
            }

            if (interruptedRound != null) {
                log.info("1837_TRACE: [7] Resuming interrupted round: " + interruptedRound.getId());
                setInterruptedRound(null); 
                setRound(interruptedRound);

                if (interruptedRound instanceof OperatingRound_1837) {
                    log.info("1837_TRACE: [7a] Invoking OR resume");
                    ((OperatingRound_1837) interruptedRound).resume();
                } else if (interruptedRound instanceof StockRound_1837) {
                    log.info("1837_TRACE: [7b] Invoking SR resume");
                    ((StockRound_1837) interruptedRound).resume();
                } else {
                    log.info("1837_TRACE: [7c] Fallback super.nextRound");
                    super.nextRound(interruptedRound);
                }

            } else {
                log.info("1837_TRACE: [8] No interrupted round. Using fallback");
                Round safeRound = previousSRorOR.value();
                if (safeRound == null) {
                    safeRound = (Round) currentRound.value(); 
                }
                super.nextRound(safeRound);
            }

        } else if (prevRound instanceof StockRound_1837 || prevRound instanceof OperatingRound_1837) {
            previousSRorOR.set(prevRound);
            doneThisRound.clear();

            // DO NOT setInterruptedRound(prevRound) here.
            // If we are transitioning, the previous round is over.
            // We pass 'null' to checkAndRunCER/NFR so they don't set a return point.

            // 1. Check for Coal Exchanges
            if (checkAndRunCER(newPhaseId.value(), prevRound, null)) {
                return;
            }

            // 2. Check for National Formations
            if (checkAndRunNFR(newPhaseId.value(), prevRound, null)) {
                return;
            }

            // 3. No specials triggered, proceed normally
            super.nextRound(prevRound);
        } else {
            setInterruptedRound(null);
            super.nextRound(prevRound);
        }
    }







}