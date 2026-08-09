package de.tum.cit.aet.artemis.featuremodel.deployment.repository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.SnapshotProperties;
import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentModes;
import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentProfile;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.DeploymentProfileException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * File-based repository for Deployment Profiles.
 *
 * <p>
 * Profiles are loaded from committed classpath bootstrap files under {@code classpath:deployment-profiles/*.json} and
 * optionally overridden or supplemented by local files under {@code <dataRoot>/deployment-profiles/*.json}. A local
 * profile with the same id as a classpath profile overrides it; a duplicate id within the same source is rejected as a
 * configuration error. Profiles are re-read on each call because the profile set is small and read-only for this phase.
 */
@Repository
public class DeploymentProfileRepository {

    private static final Logger log = LoggerFactory.getLogger(DeploymentProfileRepository.class);

    private static final String DEPLOYMENT_PROFILES_DIR = "deployment-profiles";

    private static final String CLASSPATH_PROFILE_PATTERN = "classpath:" + DEPLOYMENT_PROFILES_DIR + "/*.json";

    private final SnapshotProperties properties;

    private final ObjectMapper objectMapper;

    private final ResourcePatternResolver resourcePatternResolver;

    /**
     * Creates the deployment profile repository for use as a Spring bean. The injected {@link ResourceLoader} (the
     * application context) is wrapped in a pattern resolver so classpath bootstrap profiles can be discovered with a
     * glob.
     *
     * @param properties snapshot configuration providing the shared local data root.
     * @param objectMapper Jackson mapper used to parse profile JSON.
     * @param resourceLoader resource loader used to discover classpath bootstrap profiles.
     */
    @Autowired
    public DeploymentProfileRepository(SnapshotProperties properties, ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.resourcePatternResolver = new PathMatchingResourcePatternResolver(resourceLoader);
    }

    /**
     * Creates a repository using a default classpath resolver. Convenient for focused unit tests.
     *
     * @param properties snapshot configuration providing the shared local data root.
     * @param objectMapper Jackson mapper used to parse profile JSON.
     */
    public DeploymentProfileRepository(SnapshotProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.resourcePatternResolver = new PathMatchingResourcePatternResolver();
    }

    /**
     * Loads all deployment profiles, with local profiles overriding classpath profiles by id, sorted by profile id.
     *
     * @return loaded deployment profiles sorted by id.
     * @throws DeploymentProfileException if a profile cannot be parsed or a duplicate id exists within one source.
     */
    public List<DeploymentProfile> loadProfiles() {
        Map<String, DeploymentProfile> profilesById = new LinkedHashMap<>();
        for (DeploymentProfile profile : readClasspathProfiles()) {
            if (profilesById.put(profile.id(), profile) != null) {
                throw DeploymentProfileException.duplicate(profile.id(), "classpath");
            }
        }
        mergeLocalProfiles(profilesById);

        List<DeploymentProfile> profiles = new ArrayList<>(profilesById.values());
        profiles.sort(Comparator.comparing(DeploymentProfile::id));
        return List.copyOf(profiles);
    }

    /**
     * Reads the committed classpath bootstrap profiles.
     *
     * @return classpath profiles, empty when none are bundled.
     * @throws DeploymentProfileException if a classpath profile cannot be read or parsed.
     */
    private List<DeploymentProfile> readClasspathProfiles() {
        Resource[] resources;
        try {
            resources = resourcePatternResolver.getResources(CLASSPATH_PROFILE_PATTERN);
        }
        catch (IOException e) {
            throw DeploymentProfileException.unreadable("classpath pattern " + CLASSPATH_PROFILE_PATTERN);
        }
        List<DeploymentProfile> profiles = new ArrayList<>();
        for (Resource resource : resources) {
            profiles.add(readProfile(resource, "classpath resource '" + resource.getFilename() + "'"));
        }
        return profiles;
    }

    /**
     * Merges local profiles from {@code <dataRoot>/deployment-profiles} into the given map, overriding classpath
     * profiles with the same id and rejecting duplicate ids within the local source.
     *
     * @param profilesById accumulating map of profiles by id, mutated in place.
     * @throws DeploymentProfileException if a local profile cannot be parsed or a duplicate local id exists.
     */
    private void mergeLocalProfiles(Map<String, DeploymentProfile> profilesById) {
        Path directory = Path.of(properties.dataRoot(), DEPLOYMENT_PROFILES_DIR);
        if (!Files.isDirectory(directory)) {
            return;
        }
        Map<String, DeploymentProfile> localById = new LinkedHashMap<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : files) {
                DeploymentProfile profile = readLocalProfile(file);
                if (localById.put(profile.id(), profile) != null) {
                    throw DeploymentProfileException.duplicate(profile.id(), directory.toString());
                }
            }
        }
        catch (IOException e) {
            throw DeploymentProfileException.unreadable("local directory " + directory);
        }
        for (DeploymentProfile profile : localById.values()) {
            if (profilesById.containsKey(profile.id())) {
                log.info("Local deployment profile '{}' overrides the classpath profile with the same id.", profile.id());
            }
            profilesById.put(profile.id(), profile);
        }
    }

    /**
     * Reads and parses a local profile file.
     *
     * @param file local profile file.
     * @return parsed deployment profile.
     * @throws DeploymentProfileException if the file cannot be read or parsed.
     */
    private DeploymentProfile readLocalProfile(Path file) {
        try (InputStream inputStream = Files.newInputStream(file)) {
            return parseProfile(inputStream, "local file " + file);
        }
        catch (DeploymentProfileException e) {
            throw e;
        }
        catch (IOException | RuntimeException e) {
            throw DeploymentProfileException.unreadable("local file " + file);
        }
    }

    /**
     * Reads and parses a profile from a resource.
     *
     * @param resource resource to read.
     * @param sourceLabel human-readable source label for error messages.
     * @return parsed deployment profile.
     * @throws DeploymentProfileException if the resource cannot be read or parsed.
     */
    private DeploymentProfile readProfile(Resource resource, String sourceLabel) {
        try (InputStream inputStream = resource.getInputStream()) {
            return parseProfile(inputStream, sourceLabel);
        }
        catch (DeploymentProfileException e) {
            throw e;
        }
        catch (IOException | RuntimeException e) {
            throw DeploymentProfileException.unreadable(sourceLabel);
        }
    }

    /**
     * Parses a deployment profile from a JSON input stream, rejecting profiles without an id and legacy profiles that
     * still carry the removed {@code parameters} block. The legacy check reads the JSON tree first, because the
     * lenient record deserialization would otherwise silently drop the retired field.
     *
     * @param inputStream JSON input stream.
     * @param sourceLabel human-readable source label for error messages.
     * @return parsed deployment profile.
     * @throws DeploymentProfileException if the profile still contains a parameters block.
     * @throws IllegalArgumentException if the parsed profile has no id.
     */
    private DeploymentProfile parseProfile(InputStream inputStream, String sourceLabel) {
        JsonNode json = objectMapper.readTree(inputStream);
        if (json.has("parameters")) {
            throw DeploymentProfileException.legacyParameters(sourceLabel);
        }
        DeploymentProfile profile = objectMapper.treeToValue(json, DeploymentProfile.class);
        if (profile.id() == null || profile.id().isBlank()) {
            throw new IllegalArgumentException("Deployment profile is missing a non-blank id.");
        }
        warnAboutUnknownDeploymentModes(profile);
        return profile;
    }

    /**
     * Logs a warning for every unknown deployment-mode id a profile declares. Unknown entries are lenient by design:
     * they never match a requested mode and therefore stay inert, but they are not a load failure, so a profile
     * authored for a future mode still loads today.
     *
     * @param profile parsed deployment profile.
     */
    private void warnAboutUnknownDeploymentModes(DeploymentProfile profile) {
        if (profile.supportedDeploymentModes() == null) {
            return;
        }
        for (String modeId : profile.supportedDeploymentModes()) {
            if (!DeploymentModes.isKnown(modeId)) {
                log.warn("Deployment profile '{}' declares unknown deployment mode '{}'; the entry is ignored.", profile.id(), modeId);
            }
        }
    }
}
