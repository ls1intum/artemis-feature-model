import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
    selector: 'fm-feature-model-explorer',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: `
        <section class="feature-model-explorer">
            <header class="d-flex align-items-baseline justify-content-between mb-3">
                <h1 class="h4 mb-0">Feature Model Explorer</h1>
                <small class="text-muted">Phase 2 scaffold placeholder</small>
            </header>
            <p class="text-muted">
                The explorer will render the feature hierarchy here in Phase 4. The scaffold reserves the route and panel
                layout so backend wiring and tree rendering can be added without restructuring the page.
            </p>
        </section>
    `,
    styles: [
        `
            .feature-model-explorer {
                max-width: 960px;
            }
        `,
    ],
})
export class FeatureModelExplorerComponent {}
