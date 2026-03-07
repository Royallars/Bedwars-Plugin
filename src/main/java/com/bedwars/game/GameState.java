package com.bedwars.game;

public enum GameState {
    WAITING,    // Waiting for players in lobby
    STARTING,   // Countdown before game begins
    PLAYING,    // Game is active
    ENDING,     // Game ended, showing winner
    RESTARTING  // Resetting for next game
}
