export const MODULE_FEATURE_ALPHA = 'alpha';

export const MODULE_FEATURE_BETA = 'beta';

export const MODULE_FEATURE_BETA_EXTRA = 'beta-extra';

export type ModuleFeature = typeof MODULE_FEATURE_ALPHA | typeof MODULE_FEATURE_BETA | typeof MODULE_FEATURE_BETA_EXTRA;

export const PROFILE_CIONE = 'cione';

export const PROFILE_FE_ONLY = 'feprofile';

export type ProfileFeature = typeof PROFILE_CIONE | typeof PROFILE_FE_ONLY;
