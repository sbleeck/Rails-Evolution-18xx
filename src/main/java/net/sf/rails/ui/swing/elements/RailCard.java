/* $Header: /Users/blentz/rails_rcs/cvs/18xx/rails/ui/swing/elements/RailCard.java,v 1.3 2025/12/20 16:10:00 evos Exp $*/
package net.sf.rails.ui.swing.elements;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;

import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.StartItem;
import net.sf.rails.game.Train;
import net.sf.rails.game.PrivateCompany;
import net.sf.rails.game.financial.Certificate;
import net.sf.rails.game.financial.PublicCertificate;

/**
 * A unified presentation component for "Card" items (Shares, Privates, Trains).
 */
public class RailCard extends ClickField {
    
    private static final long serialVersionUID = 1L;

    public enum State {
        PASSIVE,    // Visible, but not interactable
        ACTIONABLE, // Clickable, Buyable
        SELECTED,   // Currently chosen
        DISABLED,   // Sold / Not available
        HIDDEN,      // Not shown
    }

private List<Certificate> certificates = new ArrayList<>();
    private List<Train> trains = new ArrayList<>();
    private List<PrivateCompany> privates = new ArrayList<>();

    private State currentState = State.PASSIVE;
    private Dimension lockedSize = null; 

    // Visual Constants
    private static final String BG_BEIGE = "#FFFFF0";
    private static final String BG_DIMMED = "#E0E0E0";
    private static final String FG_DIMMED = "#808080";
    
    // Pre-defined Borders to ensure constant geometry
    private static final Border BORDER_ACTIONABLE = new CompoundBorder(new EmptyBorder(1,1,1,1), BorderFactory.createRaisedBevelBorder());
    private static final Border BORDER_PASSIVE = new CompoundBorder(new EmptyBorder(1,1,1,1), BorderFactory.createLineBorder(Color.BLACK, 1));
    private static final Border BORDER_DISABLED = new CompoundBorder(new EmptyBorder(1,1,1,1), BorderFactory.createLoweredBevelBorder());
    private static final Border BORDER_SELECTED = BorderFactory.createLineBorder(Color.BLUE, 3);

    private String customLabel = null; // For manual overrides (e.g. Future trains)
    
    private boolean compactMode = false;

    public void setCompactMode(boolean compact) {
        this.compactMode = compact;
        // If compact, unlock the size so the container (GameStatus) can control it via setPreferredSize
        if (compact) {
            this.lockedSize = null; 
            this.setMargin(new Insets(0,0,0,0));
            // Force strict centering for the "Button" look
            this.setHorizontalAlignment(SwingConstants.CENTER);
            this.setVerticalAlignment(SwingConstants.CENTER);
        }
        updateView();
    }


  // File: RailCard.java

// ... (lines 1-84: Imports and Class Definition) ...

    public RailCard(StartItem startItem, ButtonGroup group) {
        super("", "", "", null, group);
        // Safety checks added here
        if (startItem != null) {
            if (startItem.getPrimary() != null) certificates.add(startItem.getPrimary());
            if (startItem.hasSecondary()) certificates.add(startItem.getSecondary());
        }
        initVisuals();
    }

    public RailCard(Certificate cert, ButtonGroup group) {
        super("", "", "", null, group);
        // --- START FIX ---
        // Prevent adding null (essential for empty slots)
        if (cert != null) certificates.add(cert);
        // --- END FIX ---
        initVisuals();
    }

// File: net/sf/rails/ui/swing/elements/RailCard.java

public RailCard(Train train, ButtonGroup group) {
        super("", "", "", null, group);
        
        // --- START FIX ---
        // Safety Check: Prevent adding null trains (used for empty slot initialization)
        if (train != null) {
            trains.add(train);
        }
        // --- END FIX ---
        
        initVisuals();
    }


    public RailCard(PrivateCompany priv, ButtonGroup group) {
        super("", "", "", null, group);
        // --- START FIX ---
        if (priv != null) privates.add(priv);
        // --- END FIX ---
        initVisuals();
    }

// ... (Rest of file) ...




    

    // Legacy/Simple constructors (kept for compatibility)
    public RailCard(Certificate cert) {
        this(cert, null);
    }

    public RailCard(Train train) {
        this(train, null);
    }

    public RailCard(PrivateCompany priv) {
        this(priv, null);
    }


    // New Helper to reset the card for reuse
    public void reset() {
        this.certificates.clear();
        this.trains.clear();
        this.privates.clear();
        this.customLabel = null;
        this.setToolTipText(null);
        this.clearPossibleActions();
        setState(State.PASSIVE);
    }

    // Setters for dynamic updates
    public void setTrain(Train train) {
        reset();
        if (train != null) this.trains.add(train);
        updateView();
    }

    public void setCustomLabel(String label) {
        reset();
        this.customLabel = label;
        updateView();
    }


    private void initVisuals() {
        this.setHorizontalAlignment(SwingConstants.LEFT);
        this.setMargin(new Insets(0, 0, 0, 0)); 
        
        updateView();
        
        // Size Locking Strategy
        this.setBorder(BORDER_SELECTED);
        Dimension d = super.getPreferredSize();
        this.lockedSize = new Dimension(d.width + 2, d.height);
        this.setPreferredSize(lockedSize);
        this.setMinimumSize(lockedSize);
        
        setState(State.PASSIVE);
    }

    public void setState(State state) {
        this.currentState = state;
        
        switch (state) {
            case ACTIONABLE:
                this.setEnabled(true);
                this.setSelected(false);
                this.setBorder(BORDER_ACTIONABLE);
                this.setVisible(true);
                break;
            case SELECTED:
                this.setEnabled(true);
                this.setSelected(true);
                this.setBorder(BORDER_SELECTED);
                this.setVisible(true);
                break;
            case PASSIVE:
                // Keep ENABLED so HTML colors (Black text) render correctly. 
                // Disabled buttons gray out text, making it invisible on beige.
                this.setEnabled(true); 
                this.setSelected(false);
                this.setBorder(BORDER_PASSIVE);
                this.setVisible(true);
                // Clear any leftover actions to ensure it acts like a label
                this.clearPossibleActions();
                break;
            case DISABLED:
                this.setEnabled(false);
                this.setSelected(false);
                this.setBorder(BORDER_DISABLED);
                this.setVisible(true);
                break;
            case HIDDEN:
                this.setVisible(false);
                break;
        }
        updateView();
    }
    
private void updateView() {
        boolean isDimmed = (currentState == State.DISABLED);
        // Compact Mode: Render simple centered text (Matching GameStatus.configureTrainButton)
        if (compactMode) {
            
            // 1. Text Extraction (Simplify to just the first available item ID)
            String label = "";
            if (customLabel != null) label = customLabel;
            else if (!certificates.isEmpty()) label = certificates.get(0).getId();
            else if (!trains.isEmpty()) label = trains.get(0).getName();
            else if (!privates.isEmpty()) label = privates.get(0).getId();

            if (label.trim().toLowerCase().startsWith("<html>")) {
                this.setText(label);
                return;
            }
            
            // --- START FIX ---
            // 2. HTML Detection (CRITICAL FIX)
            // If the label is ALREADY HTML (from GameStatus), set it directly.
            // Wrapping <html> inside another <html> breaks Swing rendering -> Invisible Text.
            if (label.trim().toLowerCase().startsWith("<html>")) {
                this.setText(label);
                return;
            }

            // 3. Default Wrapping (only for plain text)
            StringBuilder sb = new StringBuilder("<html><center>");
            
            String fgColor = "#000000";
            if (isDimmed) fgColor = FG_DIMMED;
            
            sb.append("<font size='4' color='").append(fgColor).append("'><b>")
              .append(label)
              .append("</b></font></center></html>");
              
            this.setText(sb.toString());
            return;
            // --- END FIX ---
        }

        StringBuilder sb = new StringBuilder("<html><table border='0' cellspacing='3' cellpadding='0'><tr>");
        
// Render Certificates
        for (Certificate cert : certificates) {
            // Display Company Name (e.g. "OBB") instead of Certificate ID ("cert_0")
            String label = cert.getId();
            if (cert instanceof PublicCertificate) {
                // If it's a share, show the Parent Company's ID
                label = ((PublicCertificate) cert).getParent().getId();
            } else if (cert instanceof PrivateCompany) {
                 // If it's a private, show its ID
                label = ((PrivateCompany) cert).getId();
            }
            appendItemHtml(sb, label, getCertColor(cert), isDimmed);
        }

        
        // Render Trains
        for (Train train : trains) {
            appendItemHtml(sb, train.getName(), null, isDimmed);
        }

        // Render Privates
        for (PrivateCompany priv : privates) {
            appendItemHtml(sb, priv.getId(), null, isDimmed);
        }

        sb.append("</tr></table></html>");
        this.setText(sb.toString());
    }

    private void appendItemHtml(StringBuilder sb, String text, Color itemColor, boolean dimmed) {
        String bgColor = BG_BEIGE; 
        String fgColor = "#000000"; 
        
        if (itemColor != null) {
            bgColor = toHexString(itemColor);
            if (isDark(itemColor)) {
                fgColor = "#FFFFFF";
            }
        }

        if (dimmed) {
            bgColor = BG_DIMMED; 
            fgColor = FG_DIMMED; 
        }

        sb.append("<td width='50' height='24' align='center' valign='middle' bgcolor='").append(bgColor).append("'>") 
          .append("<font size='4' color='").append(fgColor).append("'><b>")
          .append(text) 
          .append("</b></font>")
          .append("</td>");
    }

    private Color getCertColor(Certificate cert) {
        if (cert instanceof PublicCertificate) {
            PublicCertificate pubCert = (PublicCertificate) cert;
            PublicCompany pc = pubCert.getCompany();
            if (pc != null) {
                return pc.getBgColour();
            }
        } 
        return null; 
    }

    private String toHexString(Color c) {
        if (c == null) return "#FFFFFF";
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
    
    private boolean isDark(Color c) {
        double y = (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue());
        return y < 128;
    }
    
    public List<Certificate> getCertificates() {
        return certificates;
    }
    
    @Override
    public Dimension getPreferredSize() {
        if (lockedSize != null) return lockedSize;
        return super.getPreferredSize();
    }
}