package bguspl.set.ex;

import bguspl.set.Env;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;


    public Queue<Integer> queueSet; // we created a public set

    /**
     * The current score of the player.
     */
    private int score;


    /**
     * the slots between 0 - 12 we placed the tokens .
     */
    public boolean[] tokens;
    /**
     * check if the player need to be freezed .
     */
    public boolean inFreeze;
    /**
     * the time we added to the penalty.
     */
    public long unfreezeTime;
    private Dealer dealer;

    public boolean afterPenalty;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.tokens = new boolean[12]; // where the tokens being placed
        this.dealer = dealer; // we added.
        this.inFreeze = false; // we added.
        this.unfreezeTime = System.currentTimeMillis();
        this.queueSet = new LinkedList<>();
        this.afterPenalty = false;
    }


    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            if (queueSet.size() == 3 && !afterPenalty) { // a set is claimed
                synchronized (dealer) { // declare a set to the dealer and wake him
                    dealer.notify();
                }
                try {
                    synchronized (this) { // add this set to the dealer queue
                        System.out.println("hey " + id);
                        synchronized (dealer.Lock) {
                            dealer.setClaimed.add(this);
                        }
                        wait();
                    }
                    System.out.println("player back to work " + id);
                } catch (InterruptedException ignore) {
                    break;
                }
                if (!this.human) {
                    synchronized (aiThread) {
                        aiThread.notify();
                        System.out.println("ai back to work!" + " " + id);
                    }
                }
            }
        }

        if (!human){
            aiThread.interrupt();
            try {
                aiThread.join();
            } catch (InterruptedException ignored) {
            }
    }


        if(id!=0) {
            try {
                    dealer.getPlayers()[id - 1].getThread().interrupt();
                    dealer.getPlayers()[id - 1].getThread().join();
            } catch (InterruptedException ignored) {
            }
        }

        env.logger.info("thread "+ id + Thread.currentThread().getName() + " terminated.");
    }

    private void createArtificialIntelligence() {
        createArtificialIntelligence(0);
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence(int x) {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");

            while (!terminate) {
                if (x == 0) {   // random presses
                    for (int i = 0; i < 3; i++) {
                        int slot = (int) (Math.random() * 12); //  generate random number between 0 and 12.
                        this.keyPressed(slot);
                        try {
                            synchronized (this) {
                                wait(1);
                            }
                        } catch (InterruptedException ignored) {
                        }

                        // stop pressing if you claim a set
                        try {
                            synchronized (this) {
                                if (!this.afterPenalty && queueSet.size() == 3) {
                                    synchronized (dealer) { // declare a set to the dealer and wake him up
                                        dealer.notify();
                                    }
                                    System.out.println("ai sleep" + id);
                                    wait();
                                }
                            }
                        } catch (InterruptedException ignore) { break;
                        }
                    }
                } // end of random presses.

                if (x == 1) {// smart ai!!! allways the right set
                    List<Integer> cards = new LinkedList<Integer>();
                    while (cards.size() != 12) {
                        cards = new LinkedList<Integer>();
                        for (int i = 0; i < 12; i++) {
                            if (table.slotToCard[i] != null) {
                                cards.add(table.slotToCard[i]);
                            }
                        }
                    }
                    List<int[]> set = env.util.findSets(cards, 1);
                    if (set.isEmpty()) { // if there is not a legal set on the table;
                        try {
                            System.out.println("no sets on table");
                            Thread.sleep(env.config.turnTimeoutMillis + 2000); // wait to the next turn
                        } catch (InterruptedException ignored) {
                        }
                    } else {
                        for (int i = 0; i < 3 && table.cardToSlot[set.get(0)[i]] != null; i++) {
                            keyPressed(table.cardToSlot[set.get(0)[i]]);

                            if (!this.afterPenalty && queueSet.size() == 3) { // stop pressing if you claim a set
                                try {
                                    synchronized (this) {
                                        System.out.println("ai sleep" + id);
                                        wait();
                                    }
                                } catch (InterruptedException ignore) {
                                }
                            }
                            try {
                                Thread.sleep(10);
                            } catch (InterruptedException ignored) {
                            }

                        }
                    }

                }

            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate = true;

    }

    public boolean isTerminated() {
        return terminate;
    }

    public Thread getThread (){
        return playerThread;
    }

    public void resetQueue() {
        queueSet.clear();

    }

    public void removeFromQueue(int card) {
        if (queueSet.contains(card)) {

            queueSet.remove(card);
        }
    }


    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        synchronized (table) {
            if (inFreeze || table.slotToCard[slot] == null) {
                return; // player in freeze cant press.
            }
            if (!tokens[slot] && queueSet.size() < 3) { // if the slot was'nt pressed before.
                tokens[slot] = true;
                table.placeToken(id, slot);// updating the table.
                queueSet.add(table.slotToCard[slot]); // update the tokens queue
            } else if (tokens[slot]) { // if the token is already pressed.
                tokens[slot] = false;
                table.removeToken(id, slot);
                removeFromQueue(table.slotToCard[slot]); // update the queue
                afterPenalty = false;
            }
        }

    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
        inFreeze = true;
        unfreezeTime = System.currentTimeMillis() + env.config.pointFreezeMillis; //freeze player for one second
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        inFreeze = true;
        afterPenalty = true;
        this.unfreezeTime = System.currentTimeMillis() + env.config.penaltyFreezeMillis; //freeze player

    }
    public boolean getTerminate(){
        return terminate;
    }


    public int getScore() {
        return score;
    }

    public void removeTokens() {
        for (int i = 0; i < 12; i++) {
            tokens[i] = false;
            table.removeToken(id, i);
        }
    }


}
