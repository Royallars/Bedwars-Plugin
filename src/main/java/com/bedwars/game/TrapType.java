package com.bedwars.game;

public enum TrapType {
    ALARM("Alarm Trap",
            "&c&l[ALARM] &r&cAn enemy has entered your island!"),
    COUNTER_OFFENSE("Counter-Offensive Trap",
            "&a&l[COUNTER-OFFENSIVE] &r&aSpeed & Jump boost activated!"),
    MINER_FATIGUE("Miner Fatigue Trap",
            "&a&l[MINER FATIGUE] &r&aMiner Fatigue applied to intruder!"),
    REGEN_BOOST("It's a Trap!",
            "&a&l[REGEN TRAP] &r&aRegeneration field activated!");

    private final String displayName;
    private final String activationMessage;

    TrapType(String displayName, String activationMessage) {
        this.displayName = displayName;
        this.activationMessage = activationMessage;
    }

    public String getDisplayName() { return displayName; }
    public String getActivationMessage() { return activationMessage; }
}
