package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.export.domain.EnvironmentRequirement;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException;

class DemoDefaultValuesTest {

    @Test
    void derivesTheNonResolvingDemoUrlForUrlKeys() {
        assertThat(DemoDefaultValues.valueFor(catalogKeyed("artemis.iris.url", "url"))).isEqualTo("https://feature-model-demo.invalid");
    }

    @Test
    void derivesFalseForBooleanKeys() {
        assertThat(DemoDefaultValues.valueFor(catalogKeyed("artemis.demo.flag", "boolean"))).isEqualTo("false");
    }

    @Test
    void derivesThePlaceholderForStringKeys() {
        assertThat(DemoDefaultValues.valueFor(catalogKeyed("artemis.iris.secret-token", "string"))).isEqualTo("demo-change-me");
    }

    @Test
    void keepsThePlaceholderForPackageOnlyRequirements() {
        EnvironmentRequirement packageOnly = new EnvironmentRequirement("ZZ_PACKAGE_VALUE", "jenkins", "Jenkins", null, null, true,
                EnvironmentRequirement.SOURCE_RUNTIME_PACKAGE, "Required by the runtime package.");

        assertThat(DemoDefaultValues.valueFor(packageOnly)).isEqualTo("demo-change-me");
        assertThatCode(() -> DemoDefaultValues.validate(packageOnly, "demo-change-me")).doesNotThrowAnyException();
    }

    @Test
    void rejectsACatalogKeyedRequirementWithoutACatalogEntry() {
        assertThatThrownBy(() -> DemoDefaultValues.valueFor(catalogKeyed("artemis.unknown.key", null)))
                .isInstanceOfSatisfying(ArtifactGenerationException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("ARTIFACT_GENERATION_MISSING_CATALOG_ENTRY"));
    }

    @Test
    void rejectsADemoValueThatDoesNotMatchTheCatalogType() {
        assertThatThrownBy(() -> DemoDefaultValues.validate(catalogKeyed("artemis.iris.url", "url"), "demo-change-me"))
                .isInstanceOfSatisfying(ArtifactGenerationException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("ARTIFACT_GENERATION_INVALID_DEMO_DEFAULT"));
        assertThatThrownBy(() -> DemoDefaultValues.validate(catalogKeyed("artemis.demo.flag", "boolean"), "maybe"))
                .isInstanceOf(ArtifactGenerationException.class);
    }

    private EnvironmentRequirement catalogKeyed(String configKey, String catalogType) {
        return new EnvironmentRequirement("DEMO_NAME", "demo", "Demo", configKey, catalogType, false,
                EnvironmentRequirement.SOURCE_ARTIFACT_MAPPING, "Value for configuration key '" + configKey + "' required by Demo.");
    }
}
