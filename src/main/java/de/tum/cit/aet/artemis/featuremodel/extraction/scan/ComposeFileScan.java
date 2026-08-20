package de.tum.cit.aet.artemis.featuremodel.extraction.scan;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.ArtemisSourceConventions;

/**
 * Scans the top-level Docker compose files for the paired database alternatives (mysql versus postgres stacks) and
 * the Jenkins compose file. The pairing is structural exclusivity evidence only; turning it into an xor group is a
 * later curation decision.
 */
class ComposeFileScan {

    static final String MYSQL_TOKEN = "mysql";

    private static final List<String> POSTGRES_TOKENS = List.of("postgres", "postgresql");

    /**
     * One compose file with its paired alternative, when one exists.
     *
     * @param file checkout-relative compose file path.
     * @param databaseToken database token found in the file name, {@code mysql} or a postgres variant.
     * @param pairedFile checkout-relative path of the paired alternative stack, or null when unpaired.
     */
    record ComposeAlternative(String file, String databaseToken, String pairedFile) {
    }

    /**
     * Scan result over the compose files.
     *
     * @param alternatives database compose alternatives sorted by file path.
     * @param jenkinsComposeFile checkout-relative Jenkins compose path, or null when absent.
     */
    record Result(List<ComposeAlternative> alternatives, String jenkinsComposeFile) {

        /**
         * Creates an empty result for a failed or skipped scan.
         *
         * @return result without compose files.
         */
        static Result empty() {
            return new Result(List.of(), null);
        }
    }

    /**
     * Scans the top-level compose files of the given checkout.
     *
     * @param source Artemis source repository.
     * @return database alternatives and the Jenkins compose file.
     * @throws IOException if the docker directory cannot be traversed.
     */
    Result scan(ArtemisSourceRepository source) throws IOException {
        List<ComposeAlternative> alternatives = new ArrayList<>();
        for (String file : listTopLevelComposeFiles(source)) {
            String fileName = file.substring(file.lastIndexOf('/') + 1);
            if (fileName.contains(MYSQL_TOKEN)) {
                alternatives.add(new ComposeAlternative(file, MYSQL_TOKEN, findPostgresTwin(source, file).orElse(null)));
            }
            else {
                findPostgresToken(fileName).ifPresent(token -> alternatives.add(new ComposeAlternative(file, token, findMysqlTwin(source, file, token).orElse(null))));
            }
        }
        String jenkinsComposeFile = source.fileExists(ArtemisSourceConventions.Files.JENKINS_COMPOSE) ? ArtemisSourceConventions.Files.JENKINS_COMPOSE : null;
        return new Result(List.copyOf(alternatives), jenkinsComposeFile);
    }

    /**
     * Lists the compose files directly inside the docker directory, sorted by path.
     *
     * @param source Artemis source repository.
     * @return sorted checkout-relative paths.
     * @throws IOException if the docker directory cannot be traversed.
     */
    private List<String> listTopLevelComposeFiles(ArtemisSourceRepository source) throws IOException {
        List<String> files = new ArrayList<>();
        for (String file : source.findFiles(ArtemisSourceConventions.Roots.DOCKER, ArtemisSourceConventions.Naming.YAML_SUFFIX)) {
            if (file.chars().filter(character -> character == '/').count() == 1) {
                files.add(file);
            }
        }
        return files;
    }

    /**
     * Finds the postgres twin of a mysql compose file by substituting the database token.
     *
     * @param source Artemis source repository.
     * @param mysqlFile checkout-relative mysql compose path.
     * @return existing twin path, or empty.
     */
    private Optional<String> findPostgresTwin(ArtemisSourceRepository source, String mysqlFile) {
        List<String> twinCandidates = POSTGRES_TOKENS.stream().map(token -> mysqlFile.replace(MYSQL_TOKEN, token)).toList();
        return source.firstExisting(twinCandidates);
    }

    /**
     * Finds the mysql twin of a postgres compose file by substituting the database token.
     *
     * @param source Artemis source repository.
     * @param postgresFile checkout-relative postgres compose path.
     * @param postgresToken postgres token present in the file name.
     * @return existing twin path, or empty.
     */
    private Optional<String> findMysqlTwin(ArtemisSourceRepository source, String postgresFile, String postgresToken) {
        return source.firstExisting(List.of(postgresFile.replace(postgresToken, MYSQL_TOKEN)));
    }

    /**
     * Finds the postgres token contained in a compose file name.
     *
     * @param fileName compose file name.
     * @return longest matching postgres token, or empty.
     */
    private Optional<String> findPostgresToken(String fileName) {
        return POSTGRES_TOKENS.stream().filter(fileName::contains).max((first, second) -> Integer.compare(first.length(), second.length()));
    }
}
