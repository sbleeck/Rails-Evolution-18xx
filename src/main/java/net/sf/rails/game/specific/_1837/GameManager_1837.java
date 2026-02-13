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
        for (String nationalName : nationalNames) {
            if (doneThisRound.contains(nationalName)) continue;
            PublicCompany_1837 national = (PublicCompany_1837) companyManager.getPublicCompany(nationalName);
            if (phaseManager.hasReachedPhase(national.getFormationStartPhase())
                    && !NationalFormationRound.nationalIsComplete(national)) {
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
        if (newPhaseId.value() == null) {
            if (previousSRorOR.value() instanceof OperatingRound_1837) {
                nfrReportName = previousSRorOR.value().getId().replaceFirst("OR_(\\d+)(\\.\\d+)?", "$1$2");
                if (!nfrReportName.contains(".")) nfrReportName += ".1";
            } else {
                nfrReportName = previousSRorOR.value().getId().replaceFirst("SR_(\\d+)", "$1.0");
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



// In GameManager_1837.java

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
            // Pass newPhaseId.value() instead of null
            if (checkAndRunNFR(newPhaseId.value(), previousSRorOR.value(), (Round) getInterruptedRound())) {
                return;
            }

            boolean cameFromStockRound = (previousSRorOR.value() instanceof StockRound);

            if (cameFromStockRound) {
                Phase currentPhase = getRoot().getPhaseManager().getCurrentPhase();
                if (currentPhase != null) {
                    numOfORs.set(currentPhase.getNumberOfOperatingRounds());
                }
                relativeORNumber.set(0);
                startOperatingRound(true);
            } else if (relativeORNumber.value() < numOfORs.value()) {
                startOperatingRound(true);
            } else {
                startStockRound();
            }

            getCurrentRound().setPossibleActions();

        } else if (prevRound instanceof NationalFormationRound) {
            doneThisRound.add(((NationalFormationRound) prevRound).getNational().getId());
            OperatingRound_1837 interruptedRound = (OperatingRound_1837) getInterruptedRound();
            
            // Pass newPhaseId.value() instead of null
            if (checkAndRunNFR(newPhaseId.value(), previousSRorOR.value(), interruptedRound)) {
                return;
            }

            if (interruptedRound != null) {
                setRound(interruptedRound);
                interruptedRound.resume();
            } else {
                super.nextRound(previousSRorOR.value());
            }
        } else if (prevRound instanceof StockRound_1837 || prevRound instanceof OperatingRound_1837) {
            previousSRorOR.set(prevRound);
            doneThisRound.clear();
            setInterruptedRound(prevRound);
            log.info("Saving Interrupted Round: {}", prevRound.getId());
            
            // --- FIX: Pass newPhaseId.value() to enforce trigger logic ---
            if (!checkAndRunCER(newPhaseId.value(), prevRound, null)
                    && !checkAndRunNFR(newPhaseId.value(), prevRound, null)) {
                super.nextRound(prevRound);
            }
        } else {
            setInterruptedRound(null);
            super.nextRound(prevRound);
        }
    }

    public boolean checkAndRunCER(String newPhaseId, Round namingRound, Round interruptedRound) {
        if (doneThisRound.contains("CER")) return false;
        
        // --- FIX: Only run CER if we have a specific Phase Trigger ---
        // If newPhaseId is null, it means we are just transitioning rounds normally.
        // We should NOT trigger a CER just because a company is open.
        if (newPhaseId == null) return false;

        List<PublicCompany> coalCompanies = getRoot().getCompanyManager().getPublicCompaniesByType("Coal");
        boolean runCER = false;
        for (PublicCompany coalComp : coalCompanies) {
            if (!coalComp.isClosed() && coalComp.getRelatedPublicCompany().hasFloated()) {
                runCER = true;
                setInterruptedRound(interruptedRound);
                setNewPhaseId(newPhaseId);
                break;
            }
        }
        if (runCER) {
            String cerId;
            if (newPhaseId != null) {
                cerId = "CER_phase_" + newPhaseId;
            } else if (namingRound instanceof StockRound_1837) {
                cerId = namingRound.getId().replaceFirst("SR_(\\d+)", "CER_$1.0");
            } else {
                cerId = namingRound.getId().replaceFirst("OR_(\\d+)(\\.\\d+)?", "CER_$1$2");
                if (!cerId.contains(".")) cerId += ".1";
            }
            log.debug("Prev round {}, new round {}", namingRound.getId(), cerId);
            createRound(CoalExchangeRound.class, cerId).start();
        } else {
            doneThisRound.add("CER");
        }
        return runCER;
    }










}