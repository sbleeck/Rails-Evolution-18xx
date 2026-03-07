package net.sf.rails.game.specific._1817;

import net.sf.rails.game.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rails.game.action.*;
import net.sf.rails.common.DisplayBuffer;
import net.sf.rails.common.LocalText;
import net.sf.rails.common.ReportBuffer;
import net.sf.rails.game.financial.Bank;
import net.sf.rails.game.state.GenericState;
import net.sf.rails.game.state.IntegerState;

public class StartRound_1817 extends StartRound {
    private static final Logger log = LoggerFactory.getLogger(StartRound_1817.class);

    protected final int bidIncrement = 5;
    
    private final IntegerState seedMoney = IntegerState.create(this, "seedMoney", 200);

    private final GenericState<StartItem> auctionItemState =
            new GenericState<>(this, "auctionItemState");
            
    private final GenericState<Player> lastInitiator = new GenericState<>(this, "lastInitiator");

    public StartRound_1817(GameManager parent, String id) {
        super(parent, id);
    }

    public IntegerState getSeedMoneyModel() {
        return seedMoney;
    }

    @Override
    public void start() {
        super.start();
        auctionItemState.set(null);
        setPossibleActions();
    }

    @Override
    public boolean setPossibleActions() {
        boolean passAllowed = true;
        possibleActions.clear();

        if (playerManager.getCurrentPlayer() == startPlayer) ReportBuffer.add(this, "");

        while (possibleActions.isEmpty()) {
            Player currentPlayer = playerManager.getCurrentPlayer();
            StartItem activeAuction = auctionItemState.value();

            if (activeAuction != null) {

                int currentBid = activeAuction.getBid();
                boolean hasBidder = activeAuction.getBidder() != null;
int minBid = hasBidder ? currentBid + bidIncrement
                        : Math.max(bidIncrement, activeAuction.getBasePrice() - seedMoney.value());

                if (minBid <= activeAuction.getBasePrice()) {
                    if (currentPlayer.getFreeCash() + activeAuction.getBid(currentPlayer) >= minBid) {
                        possibleActions.add(new BidStartItem(activeAuction, minBid, bidIncrement, true));
                    } else {
                        numPasses.add(1);
                        setNextBiddingPlayer(activeAuction);
                        continue;
                    }
                } else {
                    numPasses.add(1);
                    setNextBiddingPlayer(activeAuction);
                    continue;
                }

            } else {
                for (StartItem item : itemsToSell.view()) {
                    if (!item.isSold()) {
                        item.setStatus(StartItem.BIDDABLE);
                        int maxSubsidizedBid = Math.max(bidIncrement, item.getBasePrice() - seedMoney.value());
                        if (currentPlayer.getFreeCash() >= maxSubsidizedBid) {
                            possibleActions.add(new BidStartItem(item, maxSubsidizedBid, bidIncrement, false));
                        }
                    }
                }
            }

            if (possibleActions.isEmpty()) {
                numPasses.add(1);
                if (auctionItemState.value() == null) {
                    playerManager.setCurrentToNextPlayer();
                } else {
                    setNextBiddingPlayer(auctionItemState.value());
                }
            }
        }

        if (passAllowed) {
            possibleActions.add(new NullAction(getRoot(), NullAction.Mode.PASS));
        }

        return true;
    }

    @Override
    protected boolean bid(String playerName, BidStartItem bidItem) {
        StartItem item = bidItem.getStartItem();
        String errMsg = null;
        Player player = playerManager.getCurrentPlayer();
        int previousBid = item.getBid(player);
        int bidAmount = bidItem.getActualBid();

        while (true) {
            if (!playerName.equals(player.getId())) {
                errMsg = LocalText.getText("WrongPlayer", playerName, player.getId());
                break;
            }


            int maxSubsidizedBid = Math.max(bidIncrement, item.getBasePrice() - seedMoney.value());
            
            if (bidAmount < maxSubsidizedBid) {
                errMsg = "Minimum cash bid is " + Bank.format(this, maxSubsidizedBid) + " (Face: " + item.getBasePrice() + " - Seed: " + seedMoney.value() + ")";
                break;
            }

            if (bidAmount > item.getBasePrice()) {
                errMsg = "Bid cannot exceed the face value of the company.";
                break;
            }

            if (bidAmount % bidIncrement != 0) {
                errMsg = "Bid must be a multiple of " + bidIncrement;
                break;
            }

            int available = player.getFreeCash() + previousBid;
            if (bidAmount > available) {
                errMsg = LocalText.getText("BidTooHigh", Bank.format(this, available));
                break;
            }
            break;
        }

        if (errMsg != null) {
            DisplayBuffer.add(this, errMsg);
            return false;
        }

        item.setBid(bidAmount, player);
        if (previousBid > 0) player.unblockCash(previousBid);
        player.blockCash(bidAmount);
        
        ReportBuffer.add(this, playerName + " bids " + Bank.format(this, bidAmount) + " on " + item.getId());

        if (bidItem.getStatus() != StartItem.AUCTIONED) {
            lastInitiator.set(player);
            item.setStatus(StartItem.AUCTIONED);
            auctionItemState.set(item);
        }

        if (bidAmount >= item.getBasePrice()) {
            assignItem(player, item, bidAmount, 0);
            auctionItemState.set(null);
            numPasses.set(0);
            playerManager.setCurrentPlayer(lastInitiator.value());
            playerManager.setCurrentToNextPlayer();
        } else {
numPasses.set(0); 
            setNextBiddingPlayer(item);
            // Explicitly ensure the UI/GameManager knows the player changed
            log.debug("Next bidder set to: " + playerManager.getCurrentPlayer().getId());
            
        }
        return true;
    }

    @Override
    protected boolean pass(NullAction action, String playerName) {
        Player player = playerManager.getCurrentPlayer();
        StartItem auctionItem = auctionItemState.value();

        if (!playerName.equals(player.getId())) {
            DisplayBuffer.add(this, LocalText.getText("InvalidPass", playerName, "Wrong Player"));
            return false;
        }

        ReportBuffer.add(this, LocalText.getText("PASSES", playerName));
        numPasses.add(1);

        if (auctionItem != null) {
            if (numPasses.value() >= auctionItem.getActiveBidders() - 1) {
                int price = auctionItem.getBid();
                assignItem(auctionItem.getBidder(), auctionItem, price, 0);
                
                auctionItemState.set(null);
                numPasses.set(0);
                
                playerManager.setCurrentPlayer(lastInitiator.value());
                playerManager.setCurrentToNextPlayer();
            } else {
                setNextBiddingPlayer(auctionItem);
            }
        } else {
            if (numPasses.value() >= playerManager.getNumberOfPlayers()) {
                gameManager.reportAllPlayersPassed();
                numPasses.set(0);
                finishRound();
            } else {
                playerManager.setCurrentToNextPlayer();
            }
        }
        return true;
    }

    @Override
    protected void assignItem(Player player, StartItem item, int price, int bPrice) {
        super.assignItem(player, item, price, bPrice);
        
        int subsidy = item.getBasePrice() - price;
        if (subsidy > 0) {
            seedMoney.add(-subsidy);
            ReportBuffer.add(this, Bank.format(this, subsidy) + " seed money used. Remaining: " + Bank.format(this, seedMoney.value()));
        }
    }

    private void setNextBiddingPlayer(StartItem item, Player biddingPlayer) {
        for (Player p : playerManager.getNextPlayersAfter(biddingPlayer, false, false)) {
            if (item.isActive(p)) {
                playerManager.setCurrentPlayer(p);
                break;
            }
        }
    }

    private void setNextBiddingPlayer(StartItem item) {
        setNextBiddingPlayer(item, playerManager.getCurrentPlayer());
    }
}