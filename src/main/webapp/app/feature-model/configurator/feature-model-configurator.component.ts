import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
    selector: 'fm-feature-model-configurator',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: `
        <section class="feature-model-configurator">
            <header class="d-flex align-items-baseline justify-content-between mb-3">
                <h1 class="h4 mb-0">Feature Model Configurator</h1>
                <small class="text-muted">Phase 2 scaffold placeholder</small>
            </header>
            <p class="text-muted">
                The configurator will load the default selection from the backend and validate user toggles in Phase 5.
                The scaffold reserves the route so the validation panel and feature toggles can be added incrementally.
            </p>
        </section>
    `,
    styles: [
        `
            .feature-model-configurator {
                max-width: 960px;
            }
        `,
    ],
})
export class FeatureModelConfiguratorComponent {}
