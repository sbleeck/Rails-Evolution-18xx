package net.sf.rails.ui.swing;

import java.awt.*;
import java.awt.event.ActionEvent; // FIX: Added Import
import java.awt.event.ActionListener; // FIX: Added Import
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedMap;

// Import for animation
import javax.swing.*;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import net.sf.rails.common.Config;
import net.sf.rails.common.ConfigManager;
import net.sf.rails.common.GameInfo;
import net.sf.rails.common.GameOption;
import net.sf.rails.common.GameOptionsSet;
import net.sf.rails.common.LocalText;

/**
 * The Game Setup Window displays the first window presented to the user. This
 * window contains all of the options available for starting a new rails.game.
 */
public class GameSetupWindow extends JDialog {
    private static final long serialVersionUID = 1L;

    private final JPanel gameListPane = new JPanel();
    private final JPanel playersPane = new JPanel();
    private final JPanel buttonPane = new JPanel();
    private final JPanel optionsPane = new JPanel();

    private final JButton newButton = new JButton(LocalText.getText("NewGame"));
    private final JButton loadButton = new JButton(LocalText.getText("LoadGame"));
    private final JButton optionButton = new JButton(LocalText.getText("OPTIONS"));
    private final JButton creditsButton = new JButton(LocalText.getText("CREDITS"));
    private final JButton configureButton = new JButton(LocalText.getText("CONFIG"));
    private final JButton randomizeButton = new JButton(LocalText.getText("RandomizePlayers"));
    private final JButton timeOptionsButton = new JButton(LocalText.getText("TIME_SETTINGS", "Time Settings"));

    private final JComboBox<String> configureBox = new JComboBox<>();
    private final JComboBox<String> gameNameBox = new JComboBox<>();

    private DefaultListModel<String> rosterModel;
    private JList<String> rosterList;

    private static class PlayerInfo {
        private final JLabel number = new JLabel();
        private final JTextField name = new JTextField(14);
        private String fullName = "";
    }

    private final List<PlayerInfo> players = Lists.newArrayList();

    private final SortedMap<GameOption, JComponent> optionComponents = Maps.newTreeMap();

    private final GameSetupController controller;

    public GameSetupWindow(GameSetupController controller) {
        super();

        this.controller = controller;
        initialize();
        initGridBag();
        GameInfo selectedGame = initGameList();
        initPlayersPane(selectedGame);
        initConfigBox();
        this.pack();
        this.setVisible(false);
    }

    private void initialize() {
        newButton.setMnemonic(KeyEvent.VK_N);
        loadButton.setMnemonic(KeyEvent.VK_L);
        optionButton.setMnemonic(KeyEvent.VK_O);
        creditsButton.setMnemonic(KeyEvent.VK_E);
        configureButton.setMnemonic(KeyEvent.VK_C);
        randomizeButton.setMnemonic(KeyEvent.VK_R);
        timeOptionsButton.setMnemonic(KeyEvent.VK_T);

        this.getContentPane().setLayout(new GridBagLayout());
        this.setTitle("Rails: New Game");
        this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        gameListPane.add(new JLabel("Games we want to play:"));
        gameListPane.add(gameNameBox);
        gameListPane.add(optionButton); // empty slot
        gameListPane.setLayout(new GridLayout(2, 2));
        gameListPane.setBorder(BorderFactory.createLoweredBevelBorder());

        newButton.addActionListener(e -> {
            if (getPlayers().isEmpty()) {
                JOptionPane.showMessageDialog(this, 
                    "You must enter at least one player name!", 
                    "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }
            // Directly proceed. Operator settings are handled in the Time Dialog.
            controller.getNewAction().actionPerformed(e);
        });

        loadButton.addActionListener(controller.getLoadAction());
        optionButton.addActionListener(controller.getOptionPanelAction());
        creditsButton.addActionListener(controller.getCreditsAction());
        configureButton.addActionListener(controller.getConfigureAction());
        
        // Randomize with animation
        randomizeButton.addActionListener(e -> {
            performRandomizationEffect(e);
        });
        
        timeOptionsButton.addActionListener(controller.getTimeOptionsAction());

        buttonPane.add(configureButton);
        buttonPane.add(configureBox);
        buttonPane.add(timeOptionsButton);
        buttonPane.add(new JLabel()); // Placeholder
        buttonPane.add(newButton);
        buttonPane.add(loadButton);
        buttonPane.add(creditsButton);

        buttonPane.setLayout(new GridLayout(0, 2));
        buttonPane.setBorder(BorderFactory.createLoweredBevelBorder());

        optionsPane.setLayout(new FlowLayout());
        optionsPane.setVisible(false);
    }

    private void initGridBag() {
        GridBagConstraints gc;

        gc = new GridBagConstraints();
        gc.gridx = 0; gc.gridy = 0; gc.weightx = 0; gc.weighty = 0;
        gc.gridwidth = 1; gc.gridheight = 1; gc.ipadx = 0; gc.ipady = 0;
        gc.anchor = GridBagConstraints.CENTER;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(0, 0, 0, 0);
        this.getContentPane().add(playersPane, gc);

        gc = new GridBagConstraints();
        gc.gridx = 0; gc.gridy = 1; gc.weightx = 0; gc.weighty = 0;
        gc.gridwidth = 1; gc.gridheight = 1; gc.ipadx = 0; gc.ipady = 0;
        gc.anchor = GridBagConstraints.CENTER;
        gc.fill = GridBagConstraints.BOTH;
        gc.insets = new Insets(0, 0, 0, 0);
        this.getContentPane().add(gameListPane, gc);

        gc = new GridBagConstraints();
        gc.gridx = 0; gc.gridy = 2; gc.weightx = 0; gc.weighty = 0;
        gc.gridwidth = 1; gc.gridheight = 1; gc.ipadx = 0; gc.ipady = 0;
        gc.anchor = GridBagConstraints.CENTER;
        gc.fill = GridBagConstraints.BOTH;
        gc.insets = new Insets(0, 0, 0, 0);
        this.getContentPane().add(optionsPane, gc);

        gc = new GridBagConstraints();
        gc.gridx = 0; gc.gridy = 3; gc.weightx = 0; gc.weighty = 0;
        gc.gridwidth = 1; gc.gridheight = 1; gc.ipadx = 0; gc.ipady = 0;
        gc.anchor = GridBagConstraints.CENTER;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(0, 0, 0, 0);
        this.getContentPane().add(buttonPane, gc);
    }

    private GameInfo initGameList() {
        GameInfo selectedGame = null;
        for (GameInfo game : controller.getGameList()) {
            String gameName = game.getName();
            String gameText = gameName + " - " + game.getNote();
            gameNameBox.addItem(gameText);
            if (game.equals(controller.getDefaultGame())) {
                gameNameBox.setSelectedItem(gameText);
                selectedGame = game;
            }
        }
        gameNameBox.addActionListener(controller.getGameAction());
        return selectedGame;
    }

    private void initConfigBox() {
        final ConfigManager cm = ConfigManager.getInstance();
        for (String profile : cm.getProfiles()) {
            configureBox.addItem(profile);
        }
        configureBox.setSelectedItem(cm.getActiveProfile());

        configureBox.addItemListener(arg0 -> cm.changeProfile((String) configureBox.getSelectedItem()));
    }

    // FIX: Re-added missing public method used by GameSetupController
    public void toggleOptions() {
        if (optionsPane.isVisible()) {
            optionsPane.setVisible(false);
            optionButton.setText(LocalText.getText("OPTIONS"));
        } else {
            optionsPane.setVisible(true);
            optionButton.setText(LocalText.getText("HIDE_OPTIONS"));
        }
    }

    // FIX: Re-added missing public method used by GameSetupController
    public void initOptions(GameInfo selectedGame) {
        // clear all previous options
        optionsPane.removeAll();
        optionComponents.clear();

        GameOptionsSet.Builder availableOptions = controller.getAvailableOptions(selectedGame);
        if (availableOptions == null || availableOptions.getOptions().isEmpty()) {
            // no options available
            JLabel label = new JLabel(LocalText.getText("NoGameOptions"));
            optionsPane.add(label);
        } else {
            List<GameOption> options = availableOptions.getOptions();
            optionsPane.setLayout(new GridLayout(((options.size() + 1) / 2), 2, 2, 2));

            for (GameOption option : options) {
                String selectedValue = option.getSelectedValue();
                if (option.isBoolean()) {
                    JCheckBox checkbox = new JCheckBox(option.getLocalisedName());
                    if (selectedValue.equalsIgnoreCase("yes")) {
                        checkbox.setSelected(true);
                    }
                    // the action related to the action
                    checkbox.addActionListener(controller.getOptionChangeAction(option));
                    optionComponents.put(option, checkbox);

                    optionsPane.add(checkbox);
                } else if (option.isHidden()) {
                    continue;
                } else {
                    // put dropdown and label into one panel to align with checkboxes
                    JPanel dropdownPanel = new JPanel();
                    dropdownPanel.setLayout(new BoxLayout(dropdownPanel, BoxLayout.LINE_AXIS));
                    dropdownPanel.add(new JLabel(LocalText.getText("SelectSomething",
                            option.getLocalisedName())));
                    dropdownPanel.add(Box.createHorizontalGlue());

                    JComboBox<String> dropdown = new JComboBox<>();
                    for (String value : option.getAllowedValues()) {
                        dropdown.addItem(value);
                    }
                    if (selectedValue != null) {
                        dropdown.setSelectedItem(selectedValue);
                    }
                    // the action related to the action
                    dropdown.addActionListener(controller.getOptionChangeAction(option));
                    optionComponents.put(option, dropdown);

                    dropdownPanel.add(dropdown);
                    optionsPane.add(dropdownPanel);
                }
            }
            optionsPane.setVisible(true);
            optionButton.setText(LocalText.getText("HIDE_OPTIONS"));
        }
    }

    // FIX: Re-added missing public method used by GameSetupController
    public void hideOptions() {
        optionsPane.setVisible(false);
        optionsPane.removeAll();
        optionComponents.clear();
        optionButton.setText(LocalText.getText("OPTIONS"));
    }

    void initPlayersPane(GameInfo selectedGame) {
        playersPane.setVisible(false);

        // 1. Remember names that have already been filled-in...
        List<String> prefilledPlayers = Lists.newArrayList();
        List<String> prefilledFullNames = Lists.newArrayList();
        for (PlayerInfo player : players) {
            if (player.name != null
                    && player.name.getText().trim().length() > 0) {
                prefilledPlayers.add(player.name.getText().trim());
                prefilledFullNames.add(player.fullName);
            }
        }
        // and remove existing players
        players.clear();

        // create playersPane
        playersPane.removeAll();

        int maxPlayers = selectedGame.getMaxPlayers();
        int minPlayers = selectedGame.getMinPlayers();

        playersPane.setLayout(new BorderLayout(10, 0));
        playersPane.setBorder(BorderFactory.createTitledBorder(""));
        
        // --- ACTIVE PLAYERS PANEL (LEFT) ---
        JPanel activePanel = new JPanel(new GridLayout(maxPlayers + 2, 1, 0, 2));
        activePanel.setBorder(BorderFactory.createTitledBorder("Active Players"));

        for (int i = 0; i < maxPlayers; i++) {

            PlayerInfo player = new PlayerInfo();

            player.name.setInputVerifier(controller.getPlayerNameVerifier());

            if (i < prefilledPlayers.size()) {
                player.name.setText(prefilledPlayers.get(i));
                player.fullName = prefilledFullNames.get(i);
            }
            if (i < minPlayers) {
                player.name.setBorder(BorderFactory.createLineBorder(Color.RED));
            }
            if (i < minPlayers || i <= prefilledPlayers.size()) {
                player.name.setEnabled(true);
            } else {
                player.name.setEnabled(false);
            }

            // allow activation of the next field by mouse click
            final int playerNr = i;
            player.name.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (playerNr == getPlayerCount()) {
                        enablePlayer(playerNr);
                        setFocus(playerNr);
                    }
                }
            });

            JPanel slot = new JPanel(new BorderLayout(5, 0));
            JLabel numberLabel = new JLabel((i + 1) + ".");
            numberLabel.setPreferredSize(new Dimension(20, 20));
            slot.add(numberLabel, BorderLayout.WEST);
            slot.add(player.name, BorderLayout.CENTER);
            
            JButton clearBtn = new JButton("X");
            clearBtn.setMargin(new Insets(0, 0, 0, 0));
            clearBtn.setPreferredSize(new Dimension(24, 20));
            clearBtn.addActionListener(e -> {
                player.name.setText("");
                player.fullName = "";
                compactActivePlayers();
            });
            slot.add(clearBtn, BorderLayout.EAST);
            
            activePanel.add(slot);
            players.add(player);
        }
        
        JPanel buttonWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonWrapper.add(randomizeButton);
        activePanel.add(buttonWrapper);

        // --- ROSTER PANEL (RIGHT) ---
        JPanel rosterPanel = new JPanel(new BorderLayout(0, 5));
        rosterPanel.setBorder(BorderFactory.createTitledBorder("Player Roster"));
        
        rosterModel = new DefaultListModel<>();
        loadRoster(rosterModel);
        rosterList = new JList<>(rosterModel);
        rosterList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        rosterList.setVisibleRowCount(maxPlayers);
        
        rosterList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    String selected = rosterList.getSelectedValue();
                    if (selected != null) {
                        addPlayerToActive(selected);
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(rosterList);
        rosterPanel.add(scrollPane, BorderLayout.CENTER);

        JButton addRosterBtn = new JButton("Add...");
        addRosterBtn.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(GameSetupWindow.this, "Enter Player Name:");
            if (name != null && !name.trim().isEmpty()) {
                String cleanName = name.trim();
                if (!rosterModel.contains(cleanName)) {
                    rosterModel.addElement(cleanName);
                    saveRoster(rosterModel);
                } else {
                    JOptionPane.showMessageDialog(GameSetupWindow.this, 
                        "Player '" + cleanName + "' is already in the roster!", 
                        "Duplicate Player", JOptionPane.WARNING_MESSAGE);
                }
            }
        });
        
        JButton removeRosterBtn = new JButton("Remove");
        removeRosterBtn.addActionListener(e -> {
            int selectedIndex = rosterList.getSelectedIndex();
            if (selectedIndex != -1) {
                rosterModel.remove(selectedIndex);
                saveRoster(rosterModel);
            }
        });

        JPanel rosterBtnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        rosterBtnPanel.add(addRosterBtn);
        rosterBtnPanel.add(removeRosterBtn);
        rosterPanel.add(rosterBtnPanel, BorderLayout.SOUTH);

        playersPane.add(activePanel, BorderLayout.WEST);
        playersPane.add(rosterPanel, BorderLayout.CENTER);

        playersPane.setVisible(true);
    }

    private void compactActivePlayers() {
        List<String> currentNames = new ArrayList<>();
        List<String> currentFullNames = new ArrayList<>();
        for (PlayerInfo p : players) {
            if (!p.name.getText().trim().isEmpty()) {
                currentNames.add(p.name.getText().trim());
                currentFullNames.add(p.fullName);
            }
        }
        for (int i = 0; i < players.size(); i++) {
            if (i < currentNames.size()) {
                players.get(i).name.setText(currentNames.get(i));
                players.get(i).fullName = currentFullNames.get(i);
                players.get(i).name.setEnabled(true);
            } else {
                players.get(i).name.setText("");
                players.get(i).fullName = "";
                if (i == currentNames.size()) {
                    players.get(i).name.setEnabled(true);
                } else {
                    players.get(i).name.setEnabled(false);
                }
            }
        }
    }

    private String extractShortName(String rosterEntry) {
        int start = rosterEntry.lastIndexOf('(');
        int end = rosterEntry.lastIndexOf(')');
        if (start != -1 && end != -1 && end > start) {
            return rosterEntry.substring(start + 1, end).trim();
        }
        // Dynamically extract first name as short name if no parentheses
        String[] parts = rosterEntry.trim().split("\\s+");
        if (parts.length > 0) {
            return parts[0];
        }
        return rosterEntry;
    }

    private void addPlayerToActive(String fullRosterName) {
        // 1. Prevent adding the exact same roster entry twice
        for (PlayerInfo player : players) {
            if (player.name.isEnabled() && !player.name.getText().trim().isEmpty()) {
                if (fullRosterName.equals(player.fullName)) {
                    JOptionPane.showMessageDialog(GameSetupWindow.this, 
                        "Player '" + fullRosterName + "' is already in the game!", 
                        "Duplicate Player", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
        }

        // 2. Find empty slot
        PlayerInfo foundSlot = null;
        for (PlayerInfo player : players) {
            if (player.name.isEnabled() && player.name.getText().trim().isEmpty()) {
                foundSlot = player;
                break;
            }
        }
        if (foundSlot == null) return;
        final PlayerInfo targetSlot = foundSlot;

        // 3. Resolve Short Name uniqueness
        String baseShortName = extractShortName(fullRosterName);
        boolean exactMatchFound = false;
        boolean baseNameUsedInNumbered = false;
        
        for (PlayerInfo p : players) {
            if (p == targetSlot || p.name.getText().trim().isEmpty()) continue;
            String text = p.name.getText().trim();
            if (text.equals(baseShortName)) exactMatchFound = true;
            else if (text.startsWith(baseShortName + " ")) baseNameUsedInNumbered = true;
        }

        String finalShortName = baseShortName;
        
        if (exactMatchFound || baseNameUsedInNumbered) {
            if (exactMatchFound) {
                for (PlayerInfo p : players) {
                    if (p != targetSlot && p.name.getText().trim().equals(baseShortName)) {
                        int c = 1;
                        while (true) {
                            String test = baseShortName + " " + c;
                            boolean taken = players.stream().anyMatch(other -> other != p && other.name.getText().trim().equals(test));
                            if (!taken) { p.name.setText(test); break; }
                            c++;
                        }
                        break;
                    }
                }
            }
            
            int counter = 1;
            while (true) {
                String testName = baseShortName + " " + counter;
                boolean taken = players.stream().anyMatch(p -> p != targetSlot && p.name.getText().trim().equals(testName));
                if (!taken) { finalShortName = testName; break; }
                counter++;
            }
        }

        // 4. Assign to slot
        targetSlot.name.setText(finalShortName);
        targetSlot.fullName = fullRosterName;
        compactActivePlayers();
    }

    private void loadRoster(DefaultListModel<String> model) {
        java.io.File file = new java.io.File("PlayerNames18xx.txt");
        if (file.exists()) {
            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        model.addElement(line.trim());
                    }
                }
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(GameSetupWindow.class).error("Error loading roster file", e);
            }
        } else {
            model.addElement("Stefan Bleeck");
            model.addElement("Ralf Arenmann");
            model.addElement("Rainer Kluge");
            model.addElement("Bjoern Ebeling");
            model.addElement("Mark Arnd");
            model.addElement("Stefan Boehme");
            model.addElement("Christian Stieling");
        }
    }

    private void saveRoster(DefaultListModel<String> model) {
        java.io.File file = new java.io.File("PlayerNames18xx.txt");
        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(file))) {
            for (int i = 0; i < model.size(); i++) {
                pw.println(model.get(i));
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(GameSetupWindow.class).error("Error saving roster file", e);
        }
    }

    GameInfo getSelectedGame() {
        return controller.getGameList().get(gameNameBox.getSelectedIndex());
    }

    String getPlayerName(int i) {
        PlayerInfo player = players.get(i);
        return player.name.getText();
    }

    int getPlayerCount() {
        return getPlayers().size();
    }

    ImmutableList<String> getPlayers() {
        ImmutableList.Builder<String> playerList = ImmutableList.builder();
        for (PlayerInfo player : players) {
            String name = player.name.getText();
            if (name != null && name.length() > 0) {
                playerList.add(name);
            }
        }
        return playerList.build();
    }

    ImmutableList<String> getFullNames() {
        ImmutableList.Builder<String> playerList = ImmutableList.builder();
        for (PlayerInfo player : players) {
            String name = player.name.getText();
            if (name != null && name.length() > 0) {
                // Fallback to short name if fullName is somehow empty
                String full = (player.fullName != null && !player.fullName.trim().isEmpty()) ? player.fullName : name;
                playerList.add(full);
            }
        }
        return playerList.build();
    }

    void setPlayers(List<String> newPlayers) {
        LinkedList<String> newPlayersCopy = Lists.newLinkedList(newPlayers);
        for (PlayerInfo player : players) {
            if (newPlayersCopy.isEmpty()) {
                player.name.setText(null);
            } else {
                player.name.setText(newPlayersCopy.pop());
            }
        }
    }

    void enablePlayer(Integer playerNr) {
        final PlayerInfo player = players.get(playerNr);
        player.name.setEnabled(true);
        player.number.setForeground(Color.BLACK);
    }

    void disablePlayer(Integer playerNr) {
        PlayerInfo player = players.get(playerNr);
        player.name.setEnabled(false);
        player.number.setForeground(Color.GRAY);
    }

    // FIX: Re-added missing public method used by GameSetupController
    public boolean isPlayerEnabled(Integer playerNr) {
        return players.get(playerNr).name.isEnabled();
    }

    void setFocus(Integer playerNr) {
        final PlayerInfo focus = players.get(playerNr);
        EventQueue.invokeLater(() -> focus.name.requestFocusInWindow());
    }

    // FIX: Re-added missing public method used by GameSetupController
    public boolean areOptionsVisible() {
        return optionsPane.isVisible();
    }

    String getSelectedGameOption(GameOption option) {
        if (option.isBoolean()) {
            JCheckBox checkbox = (JCheckBox) optionComponents.get(option);
            return checkbox.isSelected() ? "yes" : "no";
        } else {
            JComboBox dropdown = (JComboBox) optionComponents.get(option);
            return (String) dropdown.getSelectedItem();
        }
    }

    public void addConfigureProfile(String profile) {
        configureBox.addItem(profile);
    }

    public void removeConfigureProfile(String profile) {
        configureBox.removeItem(profile);
    }

    public void changeConfigureProfile(String profile) {
        configureBox.setSelectedItem(profile);
    }


    private void performRandomizationEffect(ActionEvent originalEvent) {
        // 1. Setup & Guard Clauses
        randomizeButton.setEnabled(false);

        class PlayerIdentity {
            String shortName;
            String fullName;
            PlayerIdentity(String s, String f) { shortName = s; fullName = f; }
        }
        
        List<PlayerIdentity> originalIdentities = new ArrayList<>();
        for (PlayerInfo p : players) {
            if (!p.name.getText().trim().isEmpty()) {
                originalIdentities.add(new PlayerIdentity(p.name.getText().trim(), p.fullName));
            }
        }
        int activeCount = originalIdentities.size();

        if (activeCount < 2) {
            randomizeButton.setEnabled(true);
            return;
        }

        // 2. Pre-calculate the "Destiny" (Final Result)
        List<PlayerIdentity> finalIdentities = new ArrayList<>(originalIdentities);
        java.util.Collections.shuffle(finalIdentities);

        
        // Track which UI rows (indices) are "locked"
        boolean[] locked = new boolean[activeCount]; 
        
        // 3. Animation Configuration
        final int SHUFFLE_TICK_MS = 50;   // Update "flux" text every 50ms
        final int LOCK_DELAY_MS = 1000;    // Lock one player every 600ms (0.6s)
        
        Timer timer = new Timer(SHUFFLE_TICK_MS, null); 
        
        timer.addActionListener(new ActionListener() {
            int lockedCount = 0;
            long lastLockTime = System.currentTimeMillis();

            @Override
            public void actionPerformed(ActionEvent e) {
                long now = System.currentTimeMillis();
                
                // --- Phase A: Lock a new player? ---
                // We wait for the delay, then pick a RANDOM unlocked position to fix
                if (lockedCount < activeCount && (now - lastLockTime > LOCK_DELAY_MS)) {
                    
                    // Find all indices that are not yet locked
                    List<Integer> availableIndices = Lists.newArrayList();
                    for (int i = 0; i < activeCount; i++) {
                        if (!locked[i]) availableIndices.add(i);
                    }
                    
                    if (!availableIndices.isEmpty()) {
                        // Pick one random slot to "crystallize"
                        int indexToLock = availableIndices.get((int)(Math.random() * availableIndices.size()));
                        locked[indexToLock] = true;
                        
// Fix the value to the pre-calculated destiny
                        PlayerInfo pInfo = players.get(indexToLock);
                        pInfo.name.setText(finalIdentities.get(indexToLock).shortName);
                        pInfo.fullName = finalIdentities.get(indexToLock).fullName; // Sync full name

                        
                        // Visual Cue: Locked = Black & Bold
                        pInfo.name.setForeground(Color.BLACK); 
                        pInfo.name.setFont(pInfo.name.getFont().deriveFont(Font.BOLD)); 
                        
                        lockedCount++;
                        lastLockTime = now;
                    }
                }

                // --- Phase B: Animate the "Flux" (Unlocked players) ---
                if (lockedCount < activeCount) {
                    for (int i = 0; i < activeCount; i++) {
                        if (!locked[i]) {
                            PlayerInfo pInfo = players.get(i);
                            
// Show random noise (pick any name from the original list)
                            String randomName = originalIdentities.get((int)(Math.random() * activeCount)).shortName;
                            pInfo.name.setText(randomName);

                            // Visual Cue: Flux = Gray & Italic
                            pInfo.name.setForeground(Color.GRAY);
                            pInfo.name.setFont(pInfo.name.getFont().deriveFont(Font.ITALIC)); 
                        }
                    }
                } else {
                    // --- Phase C: Finish ---
                    ((Timer)e.getSource()).stop();
                    
// Final cleanup to ensure clean state
                    for (int i = 0; i < activeCount; i++) {
                        players.get(i).name.setText(finalIdentities.get(i).shortName);
                        players.get(i).fullName = finalIdentities.get(i).fullName;
                    }
                    
                    
                    // Reset font styles for everyone
                    for (int i = 0; i < activeCount; i++) {
                        PlayerInfo pInfo = players.get(i);
                        pInfo.name.setForeground(Color.BLACK);
                        pInfo.name.setFont(pInfo.name.getFont().deriveFont(Font.PLAIN));
                    }
                    
                    randomizeButton.setEnabled(true);
                }
            }
        });
        
        timer.start();
    }
}