package de.tum.cit.aet.artemis.alpha.config;

@ArtemisFeature(id = "annotated-alpha", configuration = { @ArtemisFeatureConfig(key = "artemis.alpha.url"),
        @ArtemisFeatureConfig(key = "artemis.alpha.secret", secret = true) })
public class AlphaEnabled implements Condition {
}
