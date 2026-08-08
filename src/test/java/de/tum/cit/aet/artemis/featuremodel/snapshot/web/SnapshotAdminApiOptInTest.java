package de.tum.cit.aet.artemis.featuremodel.snapshot.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(properties = "artemis.feature-model.snapshot-admin-api-enabled=true")
class SnapshotAdminApiOptInTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void explicitClasspathDevelopmentOptInRegistersAdministrationResource() {
        assertThat(applicationContext.getBeansOfType(SnapshotResource.class)).hasSize(1);
    }
}
