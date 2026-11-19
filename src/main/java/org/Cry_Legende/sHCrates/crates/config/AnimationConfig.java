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
        // Ticks entre l'ouverture et l'apparition du lot (le couvercle d'un coffre animé qui s'ouvre)
        private final int appearDelayTicks;
        // Hauteur d'où le lot monte jusqu'à itemHeightOffset (NaN : il apparaît directement à sa hauteur)
        private final double riseFromHeight;
        private final int riseTicks;

        public QuickCrateAnimConfig(boolean enabled, int durationSeconds, boolean displayHologram, double itemHeightOffset, double hologramHeightOffset) {
            this(enabled, durationSeconds, displayHologram, itemHeightOffset, hologramHeightOffset, 0, Double.NaN, 0);
        }

        public QuickCrateAnimConfig(boolean enabled, int durationSeconds, boolean displayHologram, double itemHeightOffset, double hologramHeightOffset,
                                    int appearDelayTicks, double riseFromHeight, int riseTicks) {
            this.enabled = enabled;
            this.durationSeconds = durationSeconds;
            this.displayHologram = displayHologram;
            this.itemHeightOffset = itemHeightOffset;
            this.hologramHeightOffset = hologramHeightOffset;
            this.appearDelayTicks = Math.max(0, appearDelayTicks);
            this.riseFromHeight = riseFromHeight;
            this.riseTicks = Math.max(0, riseTicks);
        }

        public int getAppearDelayTicks() {
            return appearDelayTicks;
        }

        /** Le lot monte depuis une autre hauteur que la sienne. */
        public boolean rises() {
            return !Double.isNaN(riseFromHeight) && riseTicks > 0;
        }

        public double getRiseFromHeight() {
            return riseFromHeight;
        }

        public int getRiseTicks() {
            return riseTicks;
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
