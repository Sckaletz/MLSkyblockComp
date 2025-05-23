package eu.minelife.mLSkyblockComp.models;

import java.time.LocalTime;

/**
 * Represents a farming competition with a specific category and time period.
 */
public class Competition {
    private final String category;
    private final LocalTime startTime;
    private final LocalTime endTime;

    /**
     * Creates a new competition.
     *
     * @param category   The farming category for this competition (e.g., "Wheat", "Nether_Wart")
     * @param startTime  The start time of the competition
     * @param endTime    The end time of the competition
     */
    public Competition(String category, LocalTime startTime, LocalTime endTime) {
        this.category = category;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /**
     * Gets the farming category for this competition.
     *
     * @return The category name
     */
    public String getCategory() {
        return category;
    }

    /**
     * Gets the start time of the competition.
     *
     * @return The start time
     */
    public LocalTime getStartTime() {
        return startTime;
    }

    /**
     * Gets the end time of the competition.
     *
     * @return The end time
     */
    public LocalTime getEndTime() {
        return endTime;
    }

    /**
     * Checks if the competition is currently active at the given time.
     *
     * @param time The time to check
     * @return True if the competition is active at the given time, false otherwise
     */
    public boolean isActiveAt(LocalTime time) {
        return !time.isBefore(startTime) && !time.isAfter(endTime);
    }

    @Override
    public String toString() {
        return "Competition{" +
                "category='" + category + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                '}';
    }
}