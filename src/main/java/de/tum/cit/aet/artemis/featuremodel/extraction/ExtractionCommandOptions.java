package de.tum.cit.aet.artemis.featuremodel.extraction;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Parses the named {@code --option=value} arguments of the extraction commands. Named options replace the previous
 * positional argument list so that adding an input cannot silently shift the meaning of another one.
 */
public final class ExtractionCommandOptions {

    private static final String OPTION_PREFIX = "--";

    private ExtractionCommandOptions() {
    }

    /**
     * Parses command arguments into option values.
     *
     * @param arguments raw command arguments.
     * @param supportedOptions option names the command accepts, without the leading dashes.
     * @return option values in argument order.
     * @throws IllegalArgumentException if an argument is not a supported {@code --option=value} pair or repeats an option.
     */
    public static Map<String, String> parse(String[] arguments, Set<String> supportedOptions) {
        Map<String, String> options = new LinkedHashMap<>();
        for (String argument : arguments) {
            int separator = argument.indexOf('=');
            if (!argument.startsWith(OPTION_PREFIX) || separator < 0) {
                throw new IllegalArgumentException("Expected an option of the form --name=value but found '" + argument + "'.");
            }
            String name = argument.substring(OPTION_PREFIX.length(), separator);
            if (!supportedOptions.contains(name)) {
                throw new IllegalArgumentException("Unknown option --" + name + "; supported options are " + supportedOptions + ".");
            }
            if (options.put(name, argument.substring(separator + 1)) != null) {
                throw new IllegalArgumentException("Option --" + name + " was given more than once.");
            }
        }
        return options;
    }
}
