/* $Header: /Users/blentz/rails_rcs/cvs/18xx/rails/ui/swing/elements/RailCard.java,v 1.7 2026/01/01 23:15:00 evos Exp $*/
package net.sf.rails.ui.swing.elements;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import net.sf.rails.game.Company; // Added for generic access
import net.sf.rails.game.special.SpecialProperty;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;

import net.sf.rails.game.PrivateCompany;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.StartItem;
import net.sf.rails.game.Train;
import net.sf.rails.game.financial.Certificate;
import net.sf.rails.game.financial.PublicCertificate;
import net.sf.rails.game.Company;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * RailCard - Dynamic Geometry Edition.
 * Sizes are calculated strictly based on the Font metrics.
 */
public class RailCard extends ClickField {

    private static final long serialVersionUID = 1L;

    // --- CONFIGURATION CONSTANTS ---
    private static final double HEIGHT_FACTOR = 1.3; // Height = 1.3 * Font Height
    private static final int WIDTH_CHARS_COMPACT = 5; // Trains/Privates = 5 chars
    private static final int WIDTH_GAP_SHARES = 10; // Extra padding for Shares

    public enum State {
        PASSIVE, ACTIONABLE, SELECTED, DISABLED, HIDDEN
    }

    private List<Certificate> certificates = new ArrayList<>();
    private List<Train> trains = new ArrayList<>();
    private List<PrivateCompany> privates = new ArrayList<>();

    private State currentState = State.PASSIVE;
    private boolean compactMode = false;
    private String customLabel = null;

    // Colors
    private static final Color COL_BG_BEIGE = new Color(255, 255, 240);
    private static final Color COL_BG_DIMMED = new Color(224, 224, 224);
    private static final Color COL_FG_DIMMED = new Color(128, 128, 128);
    private static final Color COL_ACTIONABLE_BG = new Color(200, 255, 200);
    private static final Color COL_SELECTED_BG = new Color(100, 200, 100);
    private static final Color COL_SELECTED_BORDER = Color.BLUE;

    // Borders (Minimal)
    private static final Border BORDER_ACTIONABLE = BorderFactory.createRaisedBevelBorder();
    private static final Border BORDER_PASSIVE = BorderFactory.createLineBorder(Color.BLACK, 1);
    private static final Border BORDER_DISABLED = BorderFactory.createLoweredBevelBorder();
    private static final Border BORDER_SELECTED = BorderFactory.createLineBorder(COL_SELECTED_BORDER, 2);

    private double scaleFactor = 1.0;

    private JPanel contentPanel;

    public RailCard(StartItem startItem, ButtonGroup group) {
        super("", "", "", null, group);
        if (startItem != null) {
            if (startItem.getPrimary() != null)
                certificates.add(startItem.getPrimary());
            if (startItem.hasSecondary())
                certificates.add(startItem.getSecondary());
        }
        initVisuals();
    }


    public RailCard(Certificate cert, ButtonGroup group) {
        super("", "", "", null, group);
        if (cert != null)
            certificates.add(cert);
        initVisuals();
    }

    public RailCard(Train train, ButtonGroup group) {
        super("", "", "", null, group);
        if (train != null)
            trains.add(train);
        initVisuals();
    }

    public RailCard(PrivateCompany priv, ButtonGroup group) {
        super("", "", "", null, group);
        if (priv != null)
            privates.add(priv);
        initVisuals();
    }

    public RailCard(Certificate cert) {
        this(cert, null);
    }

    public RailCard(Train train) {
        this(train, null);
    }

    public RailCard(PrivateCompany priv) {
        this(priv, null);
    }

    public void reset() {
        this.certificates.clear();
        this.trains.clear();
        this.privates.clear();
        this.customLabel = null;
        this.setToolTipText(null);
        this.clearPossibleActions();
        setState(State.PASSIVE);
        updateView();
    }

    /**
     * Scales the entire card: Width, Height, and Font.
     * 
     * @param factor 1.0 is standard. e.g., 1.2 is 20% larger.
     */
    public void setScale(double factor) {
        this.scaleFactor = factor;
        // Derive and set the new font size
        Font currentFont = getFont();
        if (currentFont != null) {
            float newSize = (float) (currentFont.getSize() * factor);
            setFont(currentFont.deriveFont(newSize));
        }
        this.revalidate();
    }

    public void setTrain(Train train) {
        reset();
        if (train != null)
            this.trains.add(train);
        updateView();
    }

    public void setCustomLabel(String label) {
        // DO NOT call reset() here!
        // reset() calls setToolTipText(null), which wipes the tooltip we just set in
        // GameStatus.

        // Manually clear content lists instead to ensure the label displays correctly
        this.certificates.clear();
        this.trains.clear();
        this.privates.clear();

        this.customLabel = label;
        updateView();
    }

    public void setCompactMode(boolean compact) {
        this.compactMode = compact;
        if (compact) {
            this.setMargin(new Insets(0, 0, 0, 0));
            this.setHorizontalAlignment(SwingConstants.CENTER);
            this.setVerticalAlignment(SwingConstants.CENTER);
        }
        updateView();
    }

    private void initVisuals() {
        super.setText("");
        this.setLayout(new BorderLayout());
        this.setMargin(new Insets(0, 0, 0, 0));

        contentPanel = new JPanel();
        contentPanel.setLayout(new GridBagLayout());
        contentPanel.setOpaque(false);

        this.add(contentPanel, BorderLayout.CENTER);

        setState(State.PASSIVE);
        updateView();
    }

    private void updateView() {
        if (contentPanel == null)
            return;
        contentPanel.removeAll();

        boolean isDimmed = (currentState == State.DISABLED);
        Color fgColor = isDimmed ? COL_FG_DIMMED : Color.BLACK;

        if (compactMode) {
            String labelText = getFirstAvailableLabel();
            JLabel lbl = createStyledLabel(labelText, null, fgColor, true);
            contentPanel.add(lbl);

        } else {
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.BOTH;
            gbc.insets = new Insets(0, 0, 0, 0);
            gbc.gridy = 0;
            gbc.weighty = 1.0;

            int x = 0;

            for (Certificate cert : certificates) {
                String label = cert.getId();
                if (cert instanceof PublicCertificate) {
                    label = ((PublicCertificate) cert).getParent().getId();
                } else if (cert instanceof PrivateCompany) {
                    label = ((PrivateCompany) cert).getId();
                }
                // Strip

                Color bg = getCertColor(cert);
                if (bg == null)
                    bg = isDimmed ? COL_BG_DIMMED : COL_BG_BEIGE;
                Color itemFg = isDark(bg) ? Color.WHITE : Color.BLACK;
                if (isDimmed)
                    itemFg = COL_FG_DIMMED;

                JLabel lbl = createStyledLabel(label, bg, itemFg, false);
                lbl.setBorder(new LineBorder(Color.GRAY, 1));
                gbc.gridx = x++;
                contentPanel.add(lbl, gbc);
            }

            for (Train train : trains) {
                JLabel lbl = createStyledLabel(train.getName(), null, fgColor, false);
                gbc.gridx = x++;
                contentPanel.add(lbl, gbc);
            }

            for (PrivateCompany priv : privates) {
                JLabel lbl = createStyledLabel(priv.getId(), null, fgColor, false);
                gbc.gridx = x++;
                contentPanel.add(lbl, gbc);
            }
        }
        contentPanel.revalidate();
        contentPanel.repaint();
        this.revalidate();
        this.repaint();
    }

    private JLabel createStyledLabel(String text, Color bg, Color fg, boolean isCentered) {
        JLabel l = new JLabel(text);
        l.setOpaque(bg != null);
        if (bg != null)
            l.setBackground(bg);
        l.setForeground(fg);
        l.setFont(this.getFont());
        if (isCentered)
            l.setHorizontalAlignment(SwingConstants.CENTER);
        l.setFocusable(false);
        return l;
    }

    private String getFirstAvailableLabel() {
        if (customLabel != null)
            return customLabel;
        if (!certificates.isEmpty())
            return certificates.get(0).getId();
        if (!trains.isEmpty())
            return trains.get(0).getName();
        if (!privates.isEmpty())
            return privates.get(0).getId();
        return "";
    }

    public void setState(State state) {
        this.currentState = state;
        this.setEnabled(true);
        this.setOpaque(true);
        this.setSelected(false);
        this.setVisible(true);

        switch (state) {
            case ACTIONABLE:
                this.setBorder(BORDER_ACTIONABLE);
                this.setBackground(COL_ACTIONABLE_BG);
                break;
            case SELECTED:
                this.setSelected(true);
                this.setBorder(BORDER_SELECTED);
                this.setBackground(COL_SELECTED_BG);
                break;
            case PASSIVE:
                this.setBorder(BORDER_PASSIVE);
                this.setBackground(COL_BG_BEIGE);
                this.clearPossibleActions();
                break;
            case DISABLED:
                this.setEnabled(false);
                this.setBorder(BORDER_DISABLED);
                this.setBackground(COL_BG_DIMMED);
                break;
            case HIDDEN:
                this.setVisible(false);
                break;
        }
        updateView();
    }

    @Override
    public void setFont(Font f) {
        super.setFont(f);
        if (contentPanel != null) {
            for (Component c : contentPanel.getComponents()) {
                c.setFont(f);
            }
            updateView();
        }
    }

    // --- START FIX ---
    /**
     * STATIC CALCULATOR: The single source of truth for card dimensions.
     * Calculated dynamically from the font to avoid fixed sizes.
     */
    public static Dimension calculateBaseSize(Font f, boolean compactMode, double scaleFactor) {
        // 1. Safety Default
        if (f == null)
            f = new Font("SansSerif", Font.BOLD, 12);

        // 2. Use Toolkit Metrics (No Display Peer required)
        FontMetrics fm = java.awt.Toolkit.getDefaultToolkit().getFontMetrics(f);

        // 3. HEIGHT: 1.3 times the Font Height (No fixed floor)
        int fontHeight = (fm != null) ? fm.getHeight() : 14;
        int height = (int) (fontHeight * HEIGHT_FACTOR * scaleFactor);

        // 4. WIDTH: Context-Aware
        int width;
        if (compactMode) {
            // TRAINS / PRIVATES: 5 spaces long
            // We use '0' as a standard proxy for "space" in UI terms to avoid thin gaps
            int charW = (fm != null) ? fm.charWidth('0') : 8;
            width = (int) (charW * WIDTH_CHARS_COMPACT * scaleFactor);
        } else {
            // SHARES: "Owner" or "100%P" + Gap
            int w1 = (fm != null) ? fm.stringWidth("Owner") : 40;
            int w2 = (fm != null) ? fm.stringWidth("100%P") : 40;
            int maxW = Math.max(w1, w2);

            width = (int) ((maxW + WIDTH_GAP_SHARES) * scaleFactor);
        }

        return new Dimension(width, height);
    }

    @Override
    public Dimension getPreferredSize() {
        // 1. Get Base Size via static helper
        Dimension base = calculateBaseSize(getFont(), compactMode, scaleFactor);

        // 2. Instance-Specific Expansion (e.g. Multi-Share cards)
        if (scaleFactor == 1.0) {
            int items = Math.max(1, certificates.size() + trains.size() + privates.size());
            if (items > 1)
                base.width *= items;
        } else {
            // Complex scaling logic for "Start Round" if needed
            Font f = getFont();
            if (f != null) {
                FontMetrics fm = getFontMetrics(f);
                int totalW = 0;
                int gap = 10;
                for (Certificate cert : certificates) {
                    String label = cert.getId();
                    if (cert instanceof PublicCertificate)
                        label = ((PublicCertificate) cert).getParent().getId();
                    else if (cert instanceof PrivateCompany)
                        label = ((PrivateCompany) cert).getId();
                    if (label != null && fm != null) {
                        label = label.replaceAll("\\<.*?\\>", "");
                        totalW += fm.stringWidth(label) + gap;
                    }
                }
                if (fm != null) {
                    for (Train t : trains)
                        totalW += fm.stringWidth(t.getName()) + gap;
                    for (PrivateCompany p : privates)
                        totalW += fm.stringWidth(p.getId()) + gap;
                }
                if (totalW > 0)
                    return new Dimension(Math.max(40, totalW + gap), base.height);
            }
        }
        return base;
    }
    // --- END FIX ---

    private Color getCertColor(Certificate cert) {
        if (cert instanceof PublicCertificate) {
            PublicCertificate pubCert = (PublicCertificate) cert;
            PublicCompany pc = pubCert.getCompany();
            if (pc != null)
                return pc.getBgColour();
        }
        return null;
    }

    private boolean isDark(Color c) {
        if (c == null)
            return false;
        double y = (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue());
        return y < 128;
    }

    public List<Certificate> getCertificates() {
        return certificates;
    }


    /**
     * Prevents the card from being stretched horizontally by BoxLayout
     * (GameStatus).
     */
    @Override
    public Dimension getMaximumSize() {
        Dimension pref = getPreferredSize();
        return new Dimension(pref.width, pref.height);
    }

    public void setShareStackTooltip(Collection<PublicCertificate> certs) {
        if (certs == null || certs.isEmpty()) {
            setToolTipText(null);
            return;
        }

        Map<Integer, Integer> counts = new TreeMap<>(Collections.reverseOrder());
        PublicCompany company = null;

        for (PublicCertificate cert : certs) {
            if (company == null)
                company = cert.getCompany();
            int s = cert.getShare();
            counts.put(s, counts.getOrDefault(s, 0) + 1);
        }

        StringBuilder text = new StringBuilder();
        text.append("<html><b>");
        if (company != null)
            text.append(company.getId()).append(" ");
        text.append("Shares</b><hr><font size='4'>");

        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            text.append(entry.getKey()).append("% x ").append(entry.getValue()).append("<br>");
        }
        text.append("</font></html>");

        setToolTipText(text.toString());
    }

    public void setPrivateCompanyTooltip(PrivateCompany pc) {
        if (pc == null) {
            setToolTipText(null);
            return;
        }
        String info = pc.getInfoText();
        if (info != null && !info.toLowerCase().startsWith("<html>")) {
            info = "<html>" + info + "</html>";
        }
        setToolTipText(info);
    }

    /**
     * Generic tooltip setter for any Company (Public or Private).
     * Used by StartRoundWindow.
     */
    public void setCompanyDetailsTooltip(Company c) {
        if (c == null) {
            setToolTipText(null);
            return;
        }
        String info = c.getInfoText();
        if (info != null && !info.toLowerCase().startsWith("<html>")) {
            info = "<html>" + info + "</html>";
        }
        setToolTipText(info);
    }

}
