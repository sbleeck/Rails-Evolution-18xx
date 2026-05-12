package net.sf.rails.ui.swing;

import java.awt.*;
import java.awt.event.ActionListener;
import java.util.List;
import javax.swing.*;

import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.financial.Bank;
import net.sf.rails.ui.swing.elements.RailCard;
import rails.game.action.BuyCertificate;
import rails.game.action.BuyTrain;
import rails.game.action.PossibleAction;

public class BankPanel extends JPanel {

    private final GameUIManager gameUIManager;
    private final Bank bank;
    
    private JLabel cashLabel;
    private JPanel ipoPanel;
    private JPanel poolPanel;
    private JPanel trainPoolPanel;
    private JPanel trainIpoPanel;
    private JPanel trainFuturePanel;
    private int lastCash = Integer.MIN_VALUE;

    public BankPanel(GameUIManager gameUIManager) {
        this.gameUIManager = gameUIManager;
        this.bank = gameUIManager.getRoot().getBank();

        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        setBackground(new Color(245, 245, 250));

        initUI();
    }

    private void initUI() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(176, 224, 230));
        headerPanel.setOpaque(true);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        cashLabel = new JLabel();
        cashLabel.setFont(GameStatus_Alt.FONT_CURRENCY.deriveFont(28f));
        cashLabel.setForeground(new Color(0, 0, 128));
        cashLabel.setHorizontalAlignment(SwingConstants.CENTER);
        headerPanel.add(cashLabel, BorderLayout.CENTER);
        add(headerPanel, BorderLayout.NORTH);

        // SHARES
JPanel sharesPanel = new JPanel();
        sharesPanel.setLayout(new BoxLayout(sharesPanel, BoxLayout.Y_AXIS));
        sharesPanel.setOpaque(false);
        
        ipoPanel = new JPanel(); ipoPanel.setLayout(new BoxLayout(ipoPanel, BoxLayout.Y_AXIS)); ipoPanel.setOpaque(false);
        ipoPanel.setBorder(BorderFactory.createTitledBorder("IPO"));
        poolPanel = new JPanel(); poolPanel.setLayout(new BoxLayout(poolPanel, BoxLayout.Y_AXIS)); poolPanel.setOpaque(false);
        poolPanel.setBorder(BorderFactory.createTitledBorder("Pool"));
        
        sharesPanel.add(ipoPanel); 
        sharesPanel.add(poolPanel);
        add(sharesPanel, BorderLayout.CENTER);

        // TRAINS
        JPanel trainsContainer = new JPanel();
        trainsContainer.setLayout(new BoxLayout(trainsContainer, BoxLayout.Y_AXIS));
        trainsContainer.setOpaque(false);
        
        trainPoolPanel = createTrainSection("Pool Trains");
        trainIpoPanel = createTrainSection("IPO Trains");
        trainFuturePanel = createTrainSection("Future Trains");

        trainsContainer.add(trainPoolPanel);
        trainsContainer.add(trainIpoPanel);
        trainsContainer.add(trainFuturePanel);

add(sharesPanel, BorderLayout.CENTER);
        add(trainsContainer, BorderLayout.SOUTH);
    }

    private JPanel createTrainSection(String title) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        JLabel lbl = new JLabel(title);
        lbl.setFont(GameStatus_Alt.FONT_SHARES);
        panel.add(lbl, BorderLayout.NORTH);
        JPanel grid = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        grid.setOpaque(false);
        panel.add(grid, BorderLayout.CENTER);
        return panel;
    }

    public void refresh() {
        int currentCash = bank.getCash();
        if (lastCash == Integer.MIN_VALUE) {
            lastCash = currentCash;
            cashLabel.setText(gameUIManager.format(currentCash));
        } else if (currentCash != lastCash) {
            if (cashLabel.isShowing()) {
                JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
                if (frame != null) new PlayerPanel.MoneySpinnerAnimator(frame, cashLabel, lastCash, currentCash, gameUIManager).start();
            } else {
                cashLabel.setText(gameUIManager.format(currentCash));
            }
            lastCash = currentCash;
        }

        List<PossibleAction> actions = null;
        if (gameUIManager.getGameManager().getPossibleActions() != null) {
            actions = gameUIManager.getGameManager().getPossibleActions().getList();
        }

        // SHARES
        ipoPanel.removeAll(); poolPanel.removeAll();
        for (PublicCompany c : gameUIManager.getAllPublicCompanies()) {
            if (c.isClosed() || !c.hasStockPrice()) continue;

            int ipoPct = bank.getIpo().getPortfolioModel().getShare(c);
            if (ipoPct > 0) {
                java.util.List<net.sf.rails.game.financial.PublicCertificate> certs = 
                    new java.util.ArrayList<>(bank.getIpo().getPortfolioModel().getCertificates(c));
                ipoPanel.add(buildBankShareRow(c, certs, ipoPct, c.getParPrice(), actions));
            }

            int poolPct = bank.getPool().getPortfolioModel().getShare(c);
            if (poolPct > 0) {
                java.util.List<net.sf.rails.game.financial.PublicCertificate> certs = 
                    new java.util.ArrayList<>(bank.getPool().getPortfolioModel().getCertificates(c));
                int price = (c.getCurrentSpace() != null) ? c.getCurrentSpace().getPrice() : 0;
                poolPanel.add(buildBankShareRow(c, certs, poolPct, price, actions));
            }
        }

        // TRAINS
        ((JPanel)trainPoolPanel.getComponent(1)).removeAll();
        ((JPanel)trainIpoPanel.getComponent(1)).removeAll();
        ((JPanel)trainFuturePanel.getComponent(1)).removeAll();

        for (net.sf.rails.game.TrainCardType tct : gameUIManager.getRoot().getTrainManager().getTrainCardTypes()) {
            if (tct.isAvailable()) {
                RailCard card = new RailCard((net.sf.rails.game.Train)null, new ButtonGroup());
                card.setCustomLabel(tct.getId().replaceAll("_\\d+$", "") + " ($" + (tct.getPotentialTrainTypes().isEmpty() ? 0 : tct.getPotentialTrainTypes().get(0).getCost()) + ")");
                card.setCompactMode(true); card.setBackground(new Color(255, 255, 240));
                
                if (actions != null) {
                    for (PossibleAction pa : actions) {
                        if (pa instanceof BuyTrain && ((BuyTrain) pa).getTrain() != null && ((BuyTrain) pa).getTrain().getType().getName().equals(tct.getId())) {
                            card.addPossibleAction(pa);
                            card.setBorder(BorderFactory.createLineBorder(new Color(0, 160, 0), 3)); // Green border
                            card.setEnabled(true);
                            if (getParent() != null && getParent().getParent() instanceof ActionListener) {
                                card.addActionListener((ActionListener) getParent().getParent());
                            }
                            break;
                        }
                    }
                }
                ((JPanel)trainPoolPanel.getComponent(1)).add(card); // Default to pool for now
            }
        }

        revalidate(); repaint();
    }




    private JPanel buildBankShareRow(PublicCompany company, java.util.List<net.sf.rails.game.financial.PublicCertificate> certs, int totalPct, int price, List<PossibleAction> actions) {
        JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 2));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        // 1. Company ID Badge
        JLabel idLabel = new JLabel(" " + company.getId() + " ");
        idLabel.setFont(GameStatus_Alt.FONT_SHARES);
        idLabel.setOpaque(true);
        idLabel.setBackground(company.getBgColour() != null ? company.getBgColour() : Color.LIGHT_GRAY);
        idLabel.setForeground(company.getFgColour() != null ? company.getFgColour() : Color.BLACK);
        idLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
        row.add(idLabel);

        // 2. Group Certificates by share % and presidency
certs.sort((c1, c2) -> {
            if (c1.isPresidentShare() != c2.isPresidentShare()) return c1.isPresidentShare() ? -1 : 1;
            return Integer.compare(c2.getShare(), c1.getShare());
        });

        int currentGroupShare = -1;
        boolean currentGroupPrez = false;
        JPanel currentStack = null;

        for (net.sf.rails.game.financial.PublicCertificate cert : certs) {
            boolean isCertPrez = cert.isPresidentShare();
            if (cert.getShare() != currentGroupShare || isCertPrez != currentGroupPrez) {
                currentStack = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, -15, 0));
                currentStack.setOpaque(false);
                row.add(currentStack);
                currentGroupShare = cert.getShare();
                currentGroupPrez = isCertPrez;
            }

            RailCard card = new RailCard(cert, new ButtonGroup());
            card.setCustomLabel(cert.getShare() + "%" + (isCertPrez ? " P" : ""));
            
            card.setCompactMode(true);
            
            // Style buyable card
            card.setBackground(new Color(255, 255, 240));
            card.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
            
            if (actions != null) {
                for (PossibleAction pa : actions) {
                    if (pa instanceof BuyCertificate && ((BuyCertificate) pa).getCompany().equals(company)) {
                        card.addPossibleAction(pa);
                        card.setBorder(BorderFactory.createLineBorder(new Color(0, 160, 0), 3)); // GREEN EDGE
                        card.setEnabled(true);
                        break;
                    }
                }
            }

            if (getParent() != null && getParent().getParent() instanceof ActionListener) {
                card.addActionListener((ActionListener) getParent().getParent());
            }

            currentStack.add(card);
        }

        // 3. Overall Sum and Price
        JLabel summaryLabel = new JLabel("Sum: " + totalPct + "% | $" + gameUIManager.format(price));
        summaryLabel.setFont(GameStatus_Alt.FONT_NUMBERS);
        row.add(summaryLabel);

        return row;
    }
// --- END FIX ---
}