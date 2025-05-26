package world.landfall.verbatim.chat;

public abstract class FocusTarget {
    public abstract String getDisplayName();
    public abstract boolean isValid(); // Check if target still exists/is online
} 