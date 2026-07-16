/**
 * FeatureToggles
 * Must be the same as in de.tum.cit.aet.artemis.core.service.feature.Feature on the server side
 */
export enum FeatureToggle {
    ToggleOne = 'ToggleOne',
    ToggleTwo = 'ToggleTwo',
    ClientOnlyToggle = 'ClientOnlyToggle',
}
export type ActiveFeatureToggles = Array<FeatureToggle>;
