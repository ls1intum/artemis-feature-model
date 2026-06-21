package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.export.domain.ProfileValueKind;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ResolvedProfileValue;

class ProfileParameterResolverTest {

    private final ProfileParameterResolver resolver = new ProfileParameterResolver();

    @Test
    void convertsEnvironmentReferenceToPlaceholder() {
        ResolvedProfileValue resolved = resolver.resolve("env:ARTEMIS_IRIS_SECRET_TOKEN");

        assertThat(resolved.kind()).isEqualTo(ProfileValueKind.ENV);
        assertThat(resolved.yamlValue()).isEqualTo("${ARTEMIS_IRIS_SECRET_TOKEN}");
        assertThat(resolved.envVarName()).isEqualTo("ARTEMIS_IRIS_SECRET_TOKEN");
        assertThat(resolved.isWritable()).isTrue();
    }

    @Test
    void leavesVaultReferenceUnwritable() {
        ResolvedProfileValue resolved = resolver.resolve("vault:secret/artemis/pyris#secret");

        assertThat(resolved.kind()).isEqualTo(ProfileValueKind.VAULT);
        assertThat(resolved.isWritable()).isFalse();
    }

    @Test
    void typesLiteralBooleansNumbersAndStrings() {
        assertThat(resolver.resolve("true").yamlValue()).isEqualTo(Boolean.TRUE);
        assertThat(resolver.resolve("20").yamlValue()).isEqualTo(20L);
        assertThat(resolver.resolve("1.0").yamlValue()).isEqualTo(1.0d);
        assertThat(resolver.resolve("5m").yamlValue()).isEqualTo("5m");
    }

    @Test
    void flagsPlaceholderLiteralsButNotRealValues() {
        assertThat(resolver.resolve("https://pyris.example.com").placeholder()).isTrue();
        assertThat(resolver.resolve("https://your-theia-instance.com").placeholder()).isTrue();
        assertThat(resolver.resolve("").placeholder()).isTrue();
        assertThat(resolver.resolve("https://search.sharing-codeability.uibk.ac.at/").placeholder()).isFalse();
        assertThat(resolver.resolve("gpt-5.4-mini").placeholder()).isFalse();
    }

    @Test
    void detectsDeprecatedAliasesPresentInProfile() {
        Map<String, String> deprecated = resolver.deprecatedAliasesIn(Map.of("pyris.url", "https://pyris.example.com", "artemis.iris.url", "https://pyris.example.com"));

        assertThat(deprecated).containsEntry("pyris.url", "artemis.iris.url");
        assertThat(deprecated).doesNotContainKey("artemis.iris.url");
    }
}
