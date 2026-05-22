package de.tum.cit.aet.artemis.featuremodel.client.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    private static final String INDEX_FORWARD = "forward:/index.html";

    /**
     * Forwards known Angular routes to the packaged client entry point.
     *
     * @return forward target for the Angular index file.
     */
    @GetMapping({ "/", "/feature-model", "/feature-model/**" })
    public String forwardClientRoute() {
        return INDEX_FORWARD;
    }
}
