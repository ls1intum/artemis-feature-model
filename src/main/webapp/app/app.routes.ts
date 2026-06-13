import { Routes } from '@angular/router';

export const APP_ROUTES: Routes = [
    {
        path: '',
        pathMatch: 'full',
        redirectTo: 'feature-model/configurator',
    },
    {
        path: 'feature-model/explorer',
        loadComponent: () =>
            import('./feature-model/explorer/feature-model-explorer.component').then(
                (m) => m.FeatureModelExplorerComponent,
            ),
        title: 'Feature Model Explorer',
    },
    {
        path: 'feature-model/configurator',
        loadComponent: () =>
            import('./feature-model/configurator/feature-model-configurator.component').then(
                (m) => m.FeatureModelConfiguratorComponent,
            ),
        title: 'Feature Model Configurator',
    },
    {
        path: '**',
        redirectTo: 'feature-model/explorer',
    },
];
