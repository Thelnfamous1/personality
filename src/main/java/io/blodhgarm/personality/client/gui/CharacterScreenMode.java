package io.blodhgarm.personality.client.gui;

public enum CharacterScreenMode {
    VIEWING("View Your Character"),
    CREATION("Create Your Character"),
    EDITING("Edit Your Character");

    public String titleText;

    CharacterScreenMode(String titleText){
        this.titleText = titleText;
    }

    public boolean isModifiableMode() {
        return this != VIEWING;
    }

    public boolean importFromCharacter() {
        return this != CREATION;
    }
}
