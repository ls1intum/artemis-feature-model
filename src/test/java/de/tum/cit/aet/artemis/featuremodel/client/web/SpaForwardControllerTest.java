package de.tum.cit.aet.artemis.featuremodel.client.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SpaForwardControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SpaForwardController()).build();

    @ParameterizedTest
    @ValueSource(strings = { "/", "/feature-model", "/feature-model/explorer", "/feature-model/configurator" })
    void forwardsClientRoutesToAngularIndex(String route) throws Exception {
        mockMvc.perform(get(route))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/api/feature-model", "/api/feature-model/validate", "/main.js" })
    void doesNotForwardApiOrAssetRoutes(String route) throws Exception {
        mockMvc.perform(get(route))
                .andExpect(status().isNotFound());
    }
}
