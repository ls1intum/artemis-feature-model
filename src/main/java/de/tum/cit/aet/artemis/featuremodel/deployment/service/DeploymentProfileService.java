package de.tum.cit.aet.artemis.featuremodel.deployment.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelCatalogService;
import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentProfile;
import de.tum.cit.aet.artemis.featuremodel.deployment.dto.DeploymentProfileDetailDTO;
import de.tum.cit.aet.artemis.featuremodel.deployment.dto.DeploymentProfileSummaryDTO;
import de.tum.cit.aet.artemis.featuremodel.deployment.repository.DeploymentProfileRepository;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.DeploymentProfileException;

/**
 * Read-only service over the deployment profile repository.
 *
 * <p>
 * The prototype has no authentication or per-user profile selection. Teachers configure only functional features, so a
 * single bundled deployment context is used: {@code default-artemis-profile} when present, otherwise the first profile
 * by id. The default profile is what the guided Configurator resolves availability against; the regular UI never asks
 * the user to choose a profile.
 *
 * <p>
 * A profile that omits {@code providedCapabilities} provides the union of the capabilities the active feature model
 * requires: the bundled default profile supports every integration the model knows, and the capability ids are
 * single-source on the model features. A profile that declares an explicit list, such as a maintainer local override,
 * keeps it.
 */
@Service
public class DeploymentProfileService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentProfileService.class);

    /** Fixed prototype default profile id, used when present among the loaded profiles. */
    static final String DEFAULT_PROFILE_ID = "default-artemis-profile";

    private final DeploymentProfileRepository repository;

    private final FeatureModelCatalogService featureModelCatalogService;

    /**
     * Creates the deployment profile service.
     *
     * @param repository repository used to load deployment profiles.
     * @param featureModelCatalogService service supplying the active feature model whose required capabilities a
     *            profile without an explicit list provides.
     */
    public DeploymentProfileService(DeploymentProfileRepository repository, FeatureModelCatalogService featureModelCatalogService) {
        this.repository = repository;
        this.featureModelCatalogService = featureModelCatalogService;
    }

    /**
     * Lists profile summaries with the default profile flagged, sorted by id.
     *
     * @return deployment profile summaries.
     * @throws DeploymentProfileException if profiles cannot be loaded.
     */
    public List<DeploymentProfileSummaryDTO> listProfiles() {
        List<DeploymentProfile> profiles = loadProfiles();
        String defaultProfileId = resolveDefaultProfileId(profiles);
        return profiles.stream().map(profile -> DeploymentProfileSummaryDTO.from(profile, profile.id().equals(defaultProfileId))).toList();
    }

    /**
     * Returns the detail of a single profile by id.
     *
     * @param profileId profile id.
     * @return deployment profile detail.
     * @throws DeploymentProfileException if the profile does not exist or cannot be loaded.
     */
    public DeploymentProfileDetailDTO getProfileDetail(String profileId) {
        List<DeploymentProfile> profiles = loadProfiles();
        String defaultProfileId = resolveDefaultProfileId(profiles);
        DeploymentProfile profile = requireProfile(profiles, profileId);
        return DeploymentProfileDetailDTO.from(profile, profile.id().equals(defaultProfileId));
    }

    /**
     * Loads all deployment profiles as domain records, resolving the provided capabilities of every profile that omits
     * them from the active feature model.
     *
     * @return loaded deployment profiles sorted by id.
     * @throws DeploymentProfileException if profiles cannot be loaded.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelLoadException if a profile derives its
     *             capabilities and the active model cannot be loaded.
     */
    public List<DeploymentProfile> loadProfiles() {
        List<DeploymentProfile> profiles = repository.loadProfiles();
        if (profiles.stream().allMatch(profile -> profile.providedCapabilities() != null)) {
            return profiles;
        }
        List<String> derived = modelRequiredCapabilities();
        List<DeploymentProfile> resolved = new ArrayList<>();
        for (DeploymentProfile profile : profiles) {
            resolved.add(profile.providedCapabilities() == null ? profile.withProvidedCapabilities(derived) : profile);
        }
        return List.copyOf(resolved);
    }

    /**
     * Collects the capabilities the active feature model requires, in model order without duplicates.
     *
     * @return required capability ids.
     */
    private List<String> modelRequiredCapabilities() {
        Set<String> capabilities = new LinkedHashSet<>();
        for (FeatureNode feature : featureModelCatalogService.loadActiveModel().features()) {
            capabilities.addAll(feature.requiresCapabilities());
        }
        return List.copyOf(capabilities);
    }

    /**
     * Resolves the profile to use for the given optional profile id, falling back to the default profile when no id is
     * provided.
     *
     * @param profiles loaded profiles.
     * @param profileId requested profile id, or {@code null}/blank for the default profile.
     * @return resolved profile.
     * @throws DeploymentProfileException if the requested profile does not exist or no profiles are available.
     */
    public DeploymentProfile resolveProfileOrDefault(List<DeploymentProfile> profiles, String profileId) {
        if (profileId == null || profileId.isBlank()) {
            String defaultProfileId = resolveDefaultProfileId(profiles);
            return requireProfile(profiles, defaultProfileId);
        }
        return requireProfile(profiles, profileId);
    }

    /**
     * Resolves the prototype default profile id from the loaded profiles.
     *
     * @param profiles loaded profiles.
     * @return default profile id.
     * @throws DeploymentProfileException if no profiles are available.
     */
    public String resolveDefaultProfileId(List<DeploymentProfile> profiles) {
        if (profiles.isEmpty()) {
            log.error("No deployment profiles are available; cannot resolve a default profile.");
            throw DeploymentProfileException.notFound(DEFAULT_PROFILE_ID);
        }
        for (DeploymentProfile profile : profiles) {
            if (DEFAULT_PROFILE_ID.equals(profile.id())) {
                return DEFAULT_PROFILE_ID;
            }
        }
        return profiles.get(0).id();
    }

    /**
     * Finds a profile by id or fails with a controlled not-found error.
     *
     * @param profiles loaded profiles.
     * @param profileId profile id to find.
     * @return matching profile.
     * @throws DeploymentProfileException if no profile has the given id.
     */
    private DeploymentProfile requireProfile(List<DeploymentProfile> profiles, String profileId) {
        return profiles.stream().filter(profile -> profile.id().equals(profileId)).findFirst().orElseThrow(() -> DeploymentProfileException.notFound(profileId));
    }
}
