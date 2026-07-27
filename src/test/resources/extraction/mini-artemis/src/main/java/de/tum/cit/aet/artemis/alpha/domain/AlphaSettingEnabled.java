package de.tum.cit.aet.artemis.alpha.domain;

/**
 * Named like a condition class but not a Spring condition; the extractor must skip this file.
 */
public class AlphaSettingEnabled {

    private boolean value;

    public boolean isValue() {
        return value;
    }

    public void setValue(boolean value) {
        this.value = value;
    }
}
