package net.sf.rails.game.specific._1837;

import net.sf.rails.common.*;
import net.sf.rails.game.*;
import net.sf.rails.game.financial.*;
import net.sf.rails.game.round.RoundFacade;
import net.sf.rails.game.state.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class GameManager_1837 extends GameManager {

    private static final Logger log = LoggerFactory.getLogger(GameManager_1837.class);

    private StringState newPhaseId = StringState.create(this, "newPhaseId", null);
    protected final GenericState<Round> previousSRorOR = new GenericState<>(this, "previousSRorOR");
    private SetState<String> doneThisRound = HashSetState.create(this, "doneThisRound");
    protected final BooleanState buyOnly = new BooleanState(this, "buyOnly", false);

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
    }

    @Override
    public void newPhaseChecks(RoundFacade round) {
        newPhaseId.set(round.getId());
    }

    public boolean checkAndRunKK(String newPhaseId, Round namingRound, Round interruptedRound) {
        if (doneThisRound.contains("KK")) return false;
        
        CompanyManager cm = getRoot().getCompanyManager();
        if (cm == null) return false;
        
        PublicCompany_1837 kk = (PublicCompany_1837) cm.getPublicCompany("KK");
        if (kk == null || NationalFormationRound.nationalIsComplete(kk)) return false;

        boolean canStart = getRoot().getPhaseManager().hasReachedPhase(kk.getFormationStartPhase());
        boolean forced = false;

        boolean hasForcingTrain = false;
        try {
            hasForcingTrain = getRoot().getBank().getPool().getPortfolioModel().getTrainList().stream()
                    .anyMatch(t -> t != null && t.getType() != null && (t.getType().getName().equals("4+1") || t.getType().getName().equals("5")));
            if (!hasForcingTrain) {
                hasForcingTrain = cm.getAllPublicCompanies().stream()
                        .flatMap(c -> c.getPortfolioModel().getTrainList().stream())
                        .anyMatch(t -> t != null && t.getType() != null && (t.getType().getName().equals("4+1") || t.getType().getName().equals("5")));
            }
        } catch (Exception e) {
            log.error("1837_ERROR: Failed evaluating KK forcing trains: ", e);
        }
        
        if (hasForcingTrain) {
            // log.info("1837_TRIGGER: Compulsory KK formation forced by train (4+1 or 5).");
            forced = true;
        }

        if (canStart || forced) {
            startNationalFormationRound("KK", newPhaseId, namingRound, interruptedRound);
            return true;
        }
        return false;
    }

    public boolean checkAndRunUG(String newPhaseId, Round namingRound, Round interruptedRound) {
        if (doneThisRound.contains("Ug")) return false;
        
        CompanyManager cm = getRoot().getCompanyManager();
        if (cm == null) return false;
        
        PublicCompany_1837 ug = (PublicCompany_1837) cm.getPublicCompany("Ug");
        if (ug == null || NationalFormationRound.nationalIsComplete(ug)) return false;

        boolean canStart = false;
        boolean forced = false;
        
        try {
            boolean has4E = getRoot().getBank().getPool().getPortfolioModel().getTrainList().stream()
                    .anyMatch(t -> t != null && t.getType() != null && t.getType().getName().equals("4E"));
            if (!has4E) {
                has4E = cm.getAllPublicCompanies().stream()
                        .flatMap(c -> c.getPortfolioModel().getTrainList().stream())
                        .anyMatch(t -> t != null && t.getType() != null && t.getType().getName().equals("4E"));
            }
            if (has4E) canStart = true;

            boolean has5 = getRoot().getBank().getPool().getPortfolioModel().getTrainList().stream()
                    .anyMatch(t -> t != null && t.getType() != null && t.getType().getName().equals("5"));
            if (!has5) {
                has5 = cm.getAllPublicCompanies().stream()
                        .flatMap(c -> c.getPortfolioModel().getTrainList().stream())
                        .anyMatch(t -> t != null && t.getType() != null && t.getType().getName().equals("5"));
            }
            
            if (has5) {
                // log.info("1837_TRIGGER: Compulsory Ug formation forced by train (5).");
                forced = true;
            }
        } catch (Exception e) {
            log.error("1837_ERROR: Failed evaluating UG triggers: ", e);
        }

        if (canStart || forced) {
            startNationalFormationRound("Ug", newPhaseId, namingRound, interruptedRound);
            return true;
        }
        return false;
    }

    public void startNationalFormationRound(String nationalName, String phaseId, Round safeNamingRound, Round interruptedRound) {
        String roundId;
        String nfrReportName;

        if (safeNamingRound == null) safeNamingRound = interruptedRound;
        if (safeNamingRound == null) safeNamingRound = (Round) currentRound.value();

        if (phaseId == null) {
            if (safeNamingRound instanceof OperatingRound_1837) {
                nfrReportName = safeNamingRound.getId().replaceFirst("OR_(\\d+)(\\.\\d+)?", "$1$2");
                if (!nfrReportName.contains(".")) nfrReportName += ".1";
            } else if (safeNamingRound instanceof StockRound_1837) {
                nfrReportName = safeNamingRound.getId().replaceFirst("SR_(\\d+)", "$1.0");
            } else {
                nfrReportName = "Recovery";
            }
            roundId = "NFR_" + nationalName + "_" + nfrReportName + "_" + System.currentTimeMillis();
        } else {
            nfrReportName = "phase " + phaseId;
            roundId = "NFR_" + nationalName + "_phase_" + phaseId + "_" + System.currentTimeMillis();
        }

        CompanyManager cm = getRoot().getCompanyManager();
        if (cm == null) return;
        PublicCompany_1837 national = (PublicCompany_1837) cm.getPublicCompany(nationalName);
        
        // Explicitly suspend the current round and register the new one
        setInterruptedRound(interruptedRound);
        
        if (nationalName.equals("KK")) {
            KKFormationRound kkRound = createRound(KKFormationRound.class, roundId);
            setRound(kkRound);
            kkRound.start(national, phaseId != null, nfrReportName);
        } else if (nationalName.equals("Ug")) {
            UgFormationRound ugRound = createRound(UgFormationRound.class, roundId);
            setRound(ugRound);
            ugRound.start(national, phaseId != null, nfrReportName);
        }

    }

    public boolean checkAndRunCER(String newPhaseId, Round namingRound, Round interruptedRound) {
        if (doneThisRound.contains("CER")) return false;

        CompanyManager cm = getRoot().getCompanyManager();
        if (cm == null) return false;
        
        List<PublicCompany> coalCompanies = cm.getPublicCompaniesByType("Coal");
        boolean runCER = false;
        
        try {
            if (coalCompanies != null) {
                for (PublicCompany coalComp : coalCompanies) {
                    if (coalComp.isClosed()) continue;
                    PublicCompany target = Merger1837.getMergeTarget(this, coalComp);
                    if (target != null && target.hasFloated()) {
                        runCER = true;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.error("1837_ERROR: Failed checking CER eligibility: ", e);
            return false;
        }

        if (runCER) {
            try {
                String cerId;
                Round safeNamingRound = interruptedRound;
                if (safeNamingRound == null) safeNamingRound = namingRound;
                if (safeNamingRound == null) safeNamingRound = (Round) currentRound.value();

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
                    cerId = "CER_Recovery_" + System.currentTimeMillis();
                }
log.info("1837_TRACE: Starting CER with ID: " + cerId);
                
                // Explicitly suspend the current round and register the new one
                setInterruptedRound(interruptedRound);
                CoalExchangeRound cer = createRound(CoalExchangeRound.class, cerId);
                setRound(cer);
                cer.start();
                
                return true;
                
            } catch (Throwable t) {
                log.error("1837_CRITICAL: Fatal error during CER creation", t);
                return false;
            }
        } else {
            doneThisRound.add("CER");
        }
        return false;
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

    @Override
    public boolean process(rails.game.action.PossibleAction action) {
        if (action instanceof rails.game.action.NullAction) {
            rails.game.action.NullAction incoming = (rails.game.action.NullAction) action;

            if (incoming.getMode() == rails.game.action.NullAction.Mode.SKIP ||
                    incoming.getMode() == rails.game.action.NullAction.Mode.PASS) {

                net.sf.rails.game.round.RoundFacade facade = currentRound.value();

                if (facade instanceof net.sf.rails.game.Round) {
                    net.sf.rails.game.Round activeRound = (net.sf.rails.game.Round) facade;

                    for (rails.game.action.PossibleAction valid : activeRound.getPossibleActionsList()) {
                        if (valid instanceof rails.game.action.NullAction) {
                            rails.game.action.NullAction validNa = (rails.game.action.NullAction) valid;

                            if (validNa.getMode() == rails.game.action.NullAction.Mode.SKIP ||
                                    validNa.getMode() == rails.game.action.NullAction.Mode.PASS) {

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

            tempSkippedMinors.clear();
            for (String minorId : ((CoalExchangeRound) prevRound).skippedMinors) {
                tempSkippedMinors.add(minorId);
            }

            Round interrupted = (Round) getInterruptedRound();
            
            try {
                if (checkAndRunKK(newPhaseId.value(), previousSRorOR.value(), interrupted)) return;
                if (checkAndRunUG(newPhaseId.value(), previousSRorOR.value(), interrupted)) return;
            } catch (Exception e) {
                log.error("1837_CRITICAL: Crash intercept during CER cascading triggers.", e);
            }

            if (interrupted != null) {
                log.info("1837_LOGIC: Returning from exchanges to interrupted round: " + interrupted.getId());
                setInterruptedRound(null); 
                setRound(interrupted);
                if (interrupted instanceof OperatingRound_1837) {
                    ((OperatingRound_1837) interrupted).resume();
                } else if (interrupted instanceof StockRound_1837) {
                    ((StockRound_1837) interrupted).resume();
                }
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

        } else if (prevRound instanceof NationalFormationRound) {
            
            try {
                if (prevRound instanceof KKFormationRound || prevRound instanceof UgFormationRound) {
                    PublicCompany_1837 national = ((NationalFormationRound) prevRound).getNational();
                    if (national != null) {
                        doneThisRound.add(national.getId());
                        log.info("1837_TRACE: Added " + national.getId() + " to doneThisRound");
                    }
                }
            } catch (Exception e) {
                log.error("1837_ERROR: Failed evaluating NFR completion: ", e);
            }
            
            Round interruptedRound = (Round) getInterruptedRound();

            try {
                if (!doneThisRound.contains("CER") && checkAndRunCER(newPhaseId.value(), previousSRorOR.value(), interruptedRound)) return;
                if (!doneThisRound.contains("KK") && checkAndRunKK(newPhaseId.value(), previousSRorOR.value(), interruptedRound)) return;
                if (!doneThisRound.contains("Ug") && checkAndRunUG(newPhaseId.value(), previousSRorOR.value(), interruptedRound)) return;
            } catch (Exception e) {
                log.error("1837_CRITICAL: Crash intercept during NFR cascading triggers.", e);
            }

            if (interruptedRound != null) {
                log.info("1837_TRACE: Resuming interrupted round: " + interruptedRound.getId());
                setInterruptedRound(null); 
                setRound(interruptedRound);
                if (interruptedRound instanceof OperatingRound_1837) {
                    ((OperatingRound_1837) interruptedRound).resume();
                } else if (interruptedRound instanceof StockRound_1837) {
                    ((StockRound_1837) interruptedRound).resume();
                } else {
                    super.nextRound(interruptedRound);
                }
            } else {
                Round safeRound = previousSRorOR.value();
                if (safeRound == null) safeRound = (Round) currentRound.value(); 
                super.nextRound(safeRound);
            }

        } else if (prevRound instanceof StockRound_1837 || prevRound instanceof OperatingRound_1837) {
            previousSRorOR.set(prevRound);
            doneThisRound.clear();

            try {
                if (checkAndRunCER(newPhaseId.value(), prevRound, null)) return;
                if (checkAndRunKK(newPhaseId.value(), prevRound, null)) return;
                if (checkAndRunUG(newPhaseId.value(), prevRound, null)) return;
            } catch (Exception e) {
                log.error("1837_CRITICAL: Crash intercept during standard round cascading triggers.", e);
            }

            super.nextRound(prevRound);
        } else {
            setInterruptedRound(null);
            super.nextRound(prevRound);
        }
    }
}