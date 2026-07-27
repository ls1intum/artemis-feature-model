package de.tum.cit.aet.artemis.alpha.config;

@ArtemisFeature(id = "annotated-alpha", group = "annotation-group",
        requiresCapabilities = { "annotation-service", "annotation-secret" }, name = "Annotated Alpha")
public class AlphaEnabled implements Condition {
}
