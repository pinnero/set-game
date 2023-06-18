# set-card -game
Multithread version of the known game of "Set". This is an assignment part of SPL course in my Bachelor degree. I have implemented the "Dealer", "Table", and "Player" classes., And the game logics. 
## about 
game Flow : The game contains a deck of 81 cards. Each card contains a drawing with four features (color,number, shape, shading).
The game starts with 12 drawn cards from the deck that are placed on a 3x4 grid on the table.
The goal of each player is to find a combination of three cards from the cards on the table that are said to make up a “legal set”.
The game ends when there is no legal sets in the deck.

Supply the main thread that runs the table logics, the main thread that runs the "Dealer" that manages the game flow, and a single thread for each player.
## Main features
1. Use locks and atomic variables to manage the threads
2. Use synchronization concepts for a "Fair" game
3. Fully support human players and computer players



