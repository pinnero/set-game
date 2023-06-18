package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;
    public LinkedList<Player> setClaimed; // list of player who claims set!

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime;
    /**
     * use to lock the queue of the dealer to sync.
     */

    public final Object Lock;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        setClaimed = new LinkedList<Player>() ;
        this.Lock = new Object() ;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        for (Player p : players) {
            Thread p1 = new Thread(p, "player");
            p1.start();
        }
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis; // we added
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(true);
            removeAllCardsFromTable();
        }
        announceWinners();
        if(!terminate){
            terminateEndGame();
        }
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            updateFreezeDisplay(); // we added a penalty timer
            if (this.setClaimed.isEmpty()) { // go to sleep only if there is no player who claims set!
                sleepUntilWokenOrTimeout();
            }
            updateTimerDisplay(false);
            if (!this.setClaimed.isEmpty()) {  // if a set is claimed
                synchronized (table) {   // lock on table - players cant place token while cards is not on the table.
                    removeCardsFromTable();
                    placeCardsOnTable();
                }
            }
        }
    }

    private void updateFreezeDisplay(){
        for (Player p :players){
            long toDisplay = p.unfreezeTime - System.currentTimeMillis();
            env.ui.setFreeze(p.id,toDisplay);
            if(toDisplay<=0){
                p.inFreeze = false;
            }
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() { // terminate
        for (Player p : players) {
            synchronized (p) {
                p.terminate();
                p.getThread().interrupt();
            }
        }
            try {
                players[players.length-1].getThread().join();
            } catch (InterruptedException ignored) {
            }
        terminate = true;
        }


    /**
     * called when the game should finish because the game ended normally.
     */
    public void terminateEndGame() { // terminate
        for (Player p : players) {
                p.terminate();
                p.getThread().interrupt();

        }
            try {
                players[players.length-1].getThread().join();
            } catch (InterruptedException ignored) {
            }
        }


    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() { // we implemented
        System.out.println(setClaimed.size() + "the size of the queue");
        Player p;
        synchronized (Lock) {
            Player temp = setClaimed.removeFirst(); // get the first player in the queue.
            p = temp;
        }
        System.out.println(setClaimed.size() + "the size of the queue after");
        System.out.println("player " +p.id+" removed");
        if (p.queueSet.size() < 3) { // checking if a player claimed a set that is not valid anymore because one of the cards used.
            System.out.println("no set no penalty ");
        }
        else {
            int[] toTestSet = new int[3]; // converting to array to check the set.
            for (int i = 0; i < 3; i++) {
                toTestSet[i] = (int) p.queueSet.toArray()[i];
            }
            if (env.util.testSet(toTestSet)) { // if a legal set was found.
                for (int i : toTestSet) {
                    System.out.println(i);
                }
                updateTimerDisplay(true); // update the timer.
                p.point(); // give a point to the player
                System.out.println("point!  " + p.id);
                for (int i : toTestSet) {
                    this.removeCard(i);
                }
            }
            else{ // if the set is illegal
                p.penalty();
                System.out.println("penalty! " + toTestSet[0] + " " + toTestSet[1] + " " + toTestSet[2] + " " + p.id);
            }
        }
        synchronized (p) {
            p.notifyAll();
            System.out.println("wake up");
        }
    }

    public void removeCard(int card){
        System.out.println(card);
        int slot = table.cardToSlot[card]; // converting card to slot.
        for (Player p: players) {
            if (p.queueSet.contains(card)) {
                p.queueSet.remove(card); // remove from the queue.
                p.tokens[slot] = false; // remove tokens.
                table.removeToken(p.id,slot);
            }
        }
        table.removeCard(slot); // update table.
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    public void placeCardsOnTable() { // we implemented
        if(deck.size()==0){
            return;}
        Collections.shuffle(deck);
        for (int i =0 ; i<12;i++){
            if(table.slotToCard[i]==null){ // if the card is missing in this slot.
                table.placeCard(deck.get(deck.size()-1),i); // place card on the table .
                deck.remove(deck.size()-1); // remove the card from the deck.
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {

        long timeLeft = this.reshuffleTime-System.currentTimeMillis();
        boolean warn = (timeLeft<env.config.turnTimeoutWarningMillis);
        synchronized (this) {
            if (warn){ // if theres less than 5 seconds.
                try {
                    wait(10);
                } catch (InterruptedException ignore) {
                }
            }

            if(env.config.pointFreezeMillis<900||env.config.penaltyFreezeMillis<900){
                try {
                    wait(Math.min(env.config.pointFreezeMillis,env.config.penaltyFreezeMillis));
                } catch (InterruptedException ignore) {
                }
            }
            else {
                try {
                    wait(900);
                } catch (InterruptedException ignore) {
                }
            }

        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    public void updateTimerDisplay(boolean reset) {
        if(reset){
            long timeLeft = reshuffleTime - System.currentTimeMillis();
            long timePassed = env.config.turnTimeoutMillis - timeLeft;
            env.ui.setCountdown(env.config.turnTimeoutMillis,false);
            reshuffleTime += timePassed;
        }
        else{
            long timeLeft = this.reshuffleTime-System.currentTimeMillis();
            boolean warn = (timeLeft<env.config.turnTimeoutWarningMillis);
            env.ui.setCountdown(timeLeft,warn);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        System.out.println("time reset!!");
        if(deck.size()==0){ // no need to replace the cards if the deck is empty.
            return;
        }
        synchronized (table) { // dont allow playres to press while there is not cards on the table
            for (int i = 0; i < 12; i++) {
                deck.add(table.slotToCard[i]); // add the card to the deck
                int card = table.slotToCard[i]; // the card that we return to the deck
                table.slotToCard[i] = null;  // remove from table
                table.cardToSlot[card] = null; // remove from the slot
                env.ui.removeCard(i); // graphics update.
            }
            for (Player p : players) { // removing the tokens placed.
                p.resetQueue();
                p.removeTokens();
                p.afterPenalty = false;
            }
        }
    }
    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        List<Integer> winners = new LinkedList<Integer>();
        Player winner = players[0];
        for (Player temp:players) { // checking who is the winner
            if (temp.getScore() > winner.getScore()) {
                winner = temp;
            }
        }
        winners.add(winner.id); // find the winner
        for (Player temp:players) { // check if there is a draw.
            temp.terminate();
            if (temp!=winner&&temp.getScore() == winner.getScore()) {
                winners.add(temp.id);
            }
        }
        int [] winnersId = new int[winners.size()]; // convert to array fpr the graphics.
        for(int i =0; i<winnersId.length;i++)
            winnersId[i] = winners.get(i);
        env.ui.announceWinner(winnersId);
    }

    public Player[] getPlayers() {
        return players;
    }
    public long getTime(){
        return reshuffleTime;
    }

}
