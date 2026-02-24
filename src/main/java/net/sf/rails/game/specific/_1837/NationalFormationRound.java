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
    protected final ArrayListState<String> sortedMinorIds = new ArrayListState<>(this, "sortedMinorIds");

    // Tracks which minor we are currently asking (0=K1, 1=K2, 2=K3)
    protected final IntegerState minorIndex = IntegerState.create(this, "MinorIndex", 0);
    protected final StringState lockedMinorOrder = StringState.create(this, "lockedMinorOrder");

    // Helper to track if we need to discard trains at the end
    protected final BooleanState discardStep = new BooleanState(this, "DiscardStep", false);
    // Guard to prevent double-firing of finishRound and corrupting reload states
    protected final BooleanState nfrFinishedGuard = new BooleanState(this, "nfrFinishedGuard", false);
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
        List<PublicCompany_1837> list = new ArrayList<>();
        PublicCompany_1837 national = getNational();
        if (national == null)
            return list;

        // 1. If we have a locked order, ALWAYS use it to ensure stable clockwise
        // resolution
        String order = lockedMinorOrder.value();
        if (order != null && !order.isEmpty()) {
            for (String id : order.split(",")) {
                PublicCompany c = companyManager.getPublicCompany(id);
                if (c instanceof PublicCompany_1837) {
                    list.add((PublicCompany_1837) c);
                }
            }
            return list;
        }

        // 2. We don't have a locked order. Calculate it dynamically!
        List<PublicCompany_1837> minors = new ArrayList<>();
        for (PublicCompany c : national.getMinors()) {
            if (c instanceof PublicCompany_1837) {
                minors.add((PublicCompany_1837) c);
            }
        }

        Player director = national.getPresident();
        if (director == null) {
            // Unformed: Strict Alphanumeric. DO NOT LOCK (Wait for formation to lock)
            minors.sort(Comparator.comparing(PublicCompany::getId));
            return minors;
        }

        // Formed (Start of new OR): Clockwise from Director
        List<Player> players = gameManager.getPlayers();
        int directorIndex = players.indexOf(director);

        minors.sort((m1, m2) -> {
            Player p1 = m1.getPresident();
            Player p2 = m2.getPresident();

            if (p1 == null && p2 == null)
                return m1.getId().compareTo(m2.getId());
            if (p1 == null)
                return -1;
            if (p2 == null)
                return 1;

            int i1 = players.indexOf(p1);
            int i2 = players.indexOf(p2);

            int dist1 = (i1 >= directorIndex) ? (i1 - directorIndex) : (i1 + players.size() - directorIndex);
            int dist2 = (i2 >= directorIndex) ? (i2 - directorIndex) : (i2 + players.size() - directorIndex);

            if (dist1 == dist2) {
                return m1.getId().compareTo(m2.getId());
            }
            return Integer.compare(dist1, dist2);
        });

        // Lock it NOW so it survives mid-round directorship shifts in this OR
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < minors.size(); i++) {
            sb.append(minors.get(i).getId());
            if (i < minors.size() - 1)
                sb.append(",");
        }
        lockedMinorOrder.set(sb.toString());
        log.info("1837_NFR: Calculated and locked new clockwise evaluation order: " + sb.toString());

        return minors;
    }

    private void lockClockwiseOrder(PublicCompany_1837 national) {
        List<PublicCompany_1837> minors = new ArrayList<>();
        for (PublicCompany c : national.getMinors()) {
            if (c instanceof PublicCompany_1837)
                minors.add((PublicCompany_1837) c);
        }

        Player director = national.getPresident();
        if (director != null) {
            List<Player> players = gameManager.getPlayers();
            int directorIndex = players.indexOf(director);

            minors.sort((m1, m2) -> {
                Player p1 = m1.getPresident();
                Player p2 = m2.getPresident();

                // Closed minors (like the newly formed K1) must go to the front
                // so the advancing minorIndex skips them correctly.
                if (p1 == null && p2 == null)
                    return m1.getId().compareTo(m2.getId());
                if (p1 == null)
                    return -1;
                if (p2 == null)
                    return 1;

                int i1 = players.indexOf(p1);
                int i2 = players.indexOf(p2);

                int dist1 = (i1 >= directorIndex) ? (i1 - directorIndex) : (i1 + players.size() - directorIndex);
                int dist2 = (i2 >= directorIndex) ? (i2 - directorIndex) : (i2 + players.size() - directorIndex);

                if (dist1 == dist2) {
                    return m1.getId().compareTo(m2.getId());
                }
                return Integer.compare(dist1, dist2);
            });
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < minors.size(); i++) {
            sb.append(minors.get(i).getId());
            if (i < minors.size() - 1)
                sb.append(",");
        }
        lockedMinorOrder.set(sb.toString());
        log.info("1837_NFR: Locked clockwise evaluation order: " + sb.toString());
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

    protected void processExchange(PublicCompany minor, PublicCompany major, ExchangeMinorAction action) {
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
                    if (parSpace != null)
                        break;
                }
            }
            if (parSpace != null)
                major.setCurrentSpace(parSpace);

            // Inject starting capital!
            net.sf.rails.game.state.Currency.fromBank(isKK ? 840 : 875, major);

            major.setFloated();
        }

        Merger1837.mergeMinor(gameManager, minor, major);
        Merger1837.fixDirectorship(gameManager, (PublicCompany_1837) major);
    }


    protected void calculateAndLockClockwiseOrder(PublicCompany_1837 national) {
        List<PublicCompany_1837> list = new ArrayList<>();

        // 1. Collect Minors
        for (PublicCompany c : national.getMinors()) {
            if (c instanceof PublicCompany_1837) {
                list.add((PublicCompany_1837) c);
            }
        }

        Player director = national.getPresident();
        if (director == null) {
            // Unformed: Strict Alphanumeric sequence (K1, K2, K3)
            list.sort(Comparator.comparing(PublicCompany::getId));
        } else {
            // Formed: Clockwise from Director
            List<Player> players = gameManager.getPlayers();
            int directorIndex = players.indexOf(director);

            list.sort((m1, m2) -> {
                Player p1 = m1.getPresident();
                Player p2 = m2.getPresident();

                if (p1 == null && p2 == null)
                    return m1.getId().compareTo(m2.getId());
                if (p1 == null)
                    return -1;
                if (p2 == null)
                    return 1;

                int i1 = players.indexOf(p1);
                int i2 = players.indexOf(p2);

                int dist1 = (i1 >= directorIndex) ? (i1 - directorIndex) : (i1 + players.size() - directorIndex);
                int dist2 = (i2 >= directorIndex) ? (i2 - directorIndex) : (i2 + players.size() - directorIndex);

                if (dist1 == dist2)
                    return m1.getId().compareTo(m2.getId());
                return Integer.compare(dist1, dist2);
            });
        }

        // 2. Lock the order in the state variable for the duration of this round
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append(list.get(i).getId());
            if (i < list.size() - 1)
                sb.append(",");
        }
        lockedMinorOrder.set(sb.toString());
        log.info("1837_NFR: Locked evaluation order for " + national.getId() + ": " + sb.toString());
    }

    @Override
    public void finishRound() {
        if (nfrFinishedGuard.value()) {
            log.debug("1837_NFR: Suppressing duplicate finishRound() to prevent state corruption.");
            return;
        }
        nfrFinishedGuard.set(true);
        log.info("1837_NFR: finishRound initiated for " + nationalId.value());

        super.finishRound();
    }

    private void advanceToNextValidMinorOrFinish() {
        List<PublicCompany_1837> minors = getSortedMinors();
        while (minorIndex.value() < minors.size()) {
            PublicCompany_1837 target = minors.get(minorIndex.value());

            if (target != null && !target.isClosed() && target.getPresident() != null) {
                // Auto-process mandatory formations (Phase 5/4+1) without prompting the user.
                if (forcedStart.value()) {
                    log.info("1837_NFR: Auto-processing forced exchange for " + target.getId());
                    PublicCompany_1837 national = getNational();
                    boolean isFormation = (minorIndex.value() == 0 && !national.hasStarted());

                    ExchangeMinorAction ema = new ExchangeMinorAction(target, national, isFormation);

                    if (isFormation) {
                        national.start();
                        executeFormationFloat(national);
                        String msg = LocalText.getText("START_MERGED_COMPANY", national.getId(),
                                Bank.format(this, national.getIPOPrice()), national.getStartSpace());
                        ReportBuffer.add(this, msg);
                        DisplayBuffer.add(this, msg);
                    }

                    processExchange(target, national, ema);
                    minorIndex.add(1);
                    continue;
                }

                // If not forced, pause here for user input
                return;
            }

            // Skip closed or invalid minor
            minorIndex.add(1);
        }

        // We have processed all minors. Check for discard or finish.
        if (isOverTrainLimit()) {
            discardStep.set(true);
            setCurrentPlayer(getNational().getPresident());
            return;
        }

        finishRound();
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

        // 2. EXCHANGE STEPS
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

        // 3. AUTO-CLOSE
        return false;
    }

    @Override
    public boolean process(PossibleAction action) {
        if (action instanceof ExchangeMinorAction) {
            ExchangeMinorAction ema = (ExchangeMinorAction) action;
            PublicCompany_1837 national = getNational();
            boolean wasFormation = ema.isFormation();

            if (wasFormation) {
                national.start();
                executeFormationFloat(national);
                String msg = LocalText.getText("START_MERGED_COMPANY", national.getId(),
                        Bank.format(this, national.getIPOPrice()), national.getStartSpace());
                ReportBuffer.add(this, msg);
                DisplayBuffer.add(this, msg);
            }

            processExchange(ema.getMinor(), national, ema);

            if (wasFormation) {
                // Lock the clockwise order NOW, using the formally established national
                // director.
                lockClockwiseOrder(national);
            }

            minorIndex.add(1);
            advanceToNextValidMinorOrFinish();
            return true;
        }

        if (action instanceof NullAction) {

            // Register decline to prevent re-prompting in the same OR/SR for ANY minor
            Object interrupted = gameManager.getInterruptedRound();
            if (interrupted instanceof OperatingRound_1837) {
                ((OperatingRound_1837) interrupted).setNationalFormationDeclined(nationalId.value());
            } else if (interrupted instanceof StockRound_1837) {
                ((StockRound_1837) interrupted).setNationalFormationDeclined(nationalId.value());
            }

            // Case A: Director Declined Formation
            if (minorIndex.value() == 0 && !getNational().hasStarted()) {
                log.info("1837_NFR: Formation DECLINED by " + currentPlayer.getName());
                minorIndex.set(getSortedMinors().size());
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

public void start(PublicCompany_1837 national, boolean isTriggered, String reportName) {
        this.nationalId.set(national.getId());
        this.minorIndex.set(0);
        this.discardStep.set(false);

        // --- START FIX: Mandatory Trigger Override ---
        boolean isForced = false;
        if ("KK".equals(national.getId())) {
            isForced = getRoot().getBank().getPool().getPortfolioModel().getTrainList().stream()
                    .anyMatch(t -> t.getType().getName().equals("4+1") || t.getType().getName().equals("5"));
            if (!isForced) {
                isForced = getRoot().getCompanyManager().getAllPublicCompanies().stream()
                        .flatMap(c -> c.getPortfolioModel().getTrainList().stream())
                        .anyMatch(t -> t.getType().getName().equals("4+1") || t.getType().getName().equals("5"));
            }
        } else if ("Ug".equals(national.getId())) {
            isForced = getRoot().getBank().getPool().getPortfolioModel().getTrainList().stream()
                    .anyMatch(t -> t.getType().getName().equals("5"));
            if (!isForced) {
                isForced = getRoot().getCompanyManager().getAllPublicCompanies().stream()
                        .flatMap(c -> c.getPortfolioModel().getTrainList().stream())
                        .anyMatch(t -> t.getType().getName().equals("5"));
            }
        }
        // --- END FIX ---

        this.forcedStart.set(isForced);
        
        // 3. Lock the Order before starting
        calculateAndLockClockwiseOrder(national);
        
        ReportBuffer.add(this, LocalText.getText("StartFormationRound", national.getId(), reportName));
        advanceToNextValidMinorOrFinish();
    }



    private void executeFormationFloat(PublicCompany_1837 major) {
        if (!major.hasFloated()) {
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
                    if (parSpace != null)
                        break;
                }
            }
            if (parSpace != null)
                major.setCurrentSpace(parSpace);

            // Inject starting capital!
            net.sf.rails.game.state.Currency.fromBank(isKK ? 840 : 875, major);
            major.setFloated();
            log.info("1837_NFR: " + major.getId() + " successfully floated with Par " + targetPar + " and received capital.");
        }
    }




}