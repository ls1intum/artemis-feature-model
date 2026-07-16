import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import {
    MODULE_FEATURE_ALPHA,
    MODULE_FEATURE_BETA,
    MODULE_FEATURE_BETA_EXTRA,
    ModuleFeature,
    PROFILE_CIONE,
    ProfileFeature,
} from 'app/app.constants';

export class AdminFeatureToggleComponent {
    /** Profiles to display */
    private readonly displayedProfiles: ProfileFeature[] = [PROFILE_CIONE];

    /** Module features to display */
    private readonly displayedModuleFeatures: ModuleFeature[] = [
        MODULE_FEATURE_ALPHA,
        MODULE_FEATURE_BETA,
        MODULE_FEATURE_BETA_EXTRA,
    ];

    /** Documentation links for runtime feature toggles */
    private readonly documentationLinks: Partial<Record<FeatureToggle, string>> = {
        [FeatureToggle.ToggleOne]: 'https://docs.example.org/toggle-one',
    };

    /** Documentation links for profile-based features */
    private readonly profileDocumentationLinks: Partial<Record<ProfileFeature, string>> = {
        [PROFILE_CIONE]: 'https://docs.example.org/cione',
    };

    /** Documentation links for module features */
    private readonly moduleDocumentationLinks: Partial<Record<ModuleFeature, string>> = {
        [MODULE_FEATURE_ALPHA]: 'https://docs.example.org/alpha',
    };
}
