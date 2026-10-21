package org.Cry_Legende.sHCrates.crates.config;

public class AnimationConfig {
    private final String type;
    private final String guiTitle;
    private final int spinDurationTicks;
    private final String soundOpen;
    private final String soundSpin;
    private final String soundWin;
    private final int rerollButtonSlot;
    private final QuickCrateAnimConfig quickCrateAnimConfig;


    public AnimationConfig(String type, String guiTitle, int spinDurationTicks, String soundOpen, String soundSpin, String soundWin, int rerollButtonSlot, QuickCrateAnimConfig quickCrateAnimConfig) {
        this.type = type.toUpperCase();
        this.guiTitle = guiTitle;
        this.spinDurationTicks = spinDurationTicks;
        this.soundOpen = soundOpen;
        this.soundSpin = soundSpin;
        this.soundWin = soundWin;
        this.rerollButtonSlot = rerollButtonSlot;
        this.quickCrateAnimConfig = quickCrateAnimConfig;
    }

    public String getType() {
        return type;
    }

    public String getGuiTitle() {
        return guiTitle;
    }

    public int getSpinDurationTicks() {
        return spinDurationTicks;
    }

    public String getSoundOpen() {
        return soundOpen;
    }

    public String getSoundSpin() {
        return soundSpin;
    }

    public String getSoundWin() {
        return soundWin;
    }

    public int getRerollButtonSlot() {
        return rerollButtonSlot;
    }

    public QuickCrateAnimConfig getQuickCrateAnimConfig() {
        return quickCrateAnimConfig;
    }

    public static class QuickCrateAnimConfig {
        private final boolean enabled;
        private final int durationSeconds;
        private final boolean displayHologram;
        private final double itemHeightOffset;
        private final double hologramHeightOffset;

        public QuickCrateAnimConfig(boolean enabled, int durationSeconds, boolean displayHologram, double itemHeightOffset, double hologramHeightOffset) {
            this.enabled = enabled;
            this.durationSeconds = durationSeconds;
            this.displayHologram = displayHologram;
            this.itemHeightOffset = itemHeightOffset;
            this.hologramHeightOffset = hologramHeightOffset;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getDurationSeconds() {
            return durationSeconds;
        }

        public boolean isDisplayHologram() {
            return displayHologram;
        }

        public double getItemHeightOffset() {
            return itemHeightOffset;
        }

        public double getHologramHeightOffset() {
            return hologramHeightOffset;
        }
    }
}
