package de.tum.cit.aet.artemis.featuremodel.visualization.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.visualization.dto.FeatureTreeNodeDTO;
import tools.jackson.databind.ObjectMapper;

class FeatureModelTreeServiceTest {

    private final FeatureModelTreeService service = new FeatureModelTreeService();

    private final JsonFeatureModelStore store = new JsonFeatureModelStore(new DefaultResourceLoader(), new ObjectMapper());

    @Test
    void buildsSingleRootTreeContainingAllFeatures() {
        var model = store.loadActiveModel();
        FeatureTreeNodeDTO tree = service.buildTree(model);

        assertThat(tree.feature().id()).isEqualTo("artemis");
        assertThat(tree.incomingRelation()).isNull();
        assertThat(countNodes(tree)).isEqualTo(model.features().size());
    }

    @Test
    void sortsChildrenByRelationOrderAndIncludesIncomingRelations() {
        FeatureTreeNodeDTO tree = service.buildTree(store.loadActiveModel());

        assertThat(tree.children()).extracting(child -> child.feature().id()).containsExactly("teaching-and-content", "exercise-system",
                "assessment-and-integrity", "adaptive-learning-and-ai", "platform-integrations", "database", "ci-provider", "localvc");
        assertThat(tree.children()).allSatisfy(child -> assertThat(child.incomingRelation()).isNotNull());
        assertThat(tree.children().getFirst().children()).extracting(child -> child.feature().id()).containsExactly("lecture", "tutorialgroup",
                "course-workflow", "communication");
        assertThat(tree.children().get(5).children()).extracting(child -> child.feature().id()).containsExactly("mysql", "postgresql");
        assertThat(tree.children().get(6).children()).extracting(child -> child.feature().id()).containsExactly("integrated-code-lifecycle", "jenkins");
    }

    private int countNodes(FeatureTreeNodeDTO node) {
        return 1 + node.children().stream().mapToInt(this::countNodes).sum();
    }
}
