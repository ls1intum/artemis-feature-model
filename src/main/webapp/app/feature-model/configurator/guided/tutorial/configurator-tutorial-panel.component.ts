import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { ConfiguratorTutorialStep } from '../../shared/configurator-tutorial';

@Component({
    selector: 'fm-configurator-tutorial-panel',
    standalone: true,
    imports: [],
    changeDetection: ChangeDetectionStrategy.OnPush,
    templateUrl: './configurator-tutorial-panel.component.html',
    styleUrl: './configurator-tutorial-panel.component.scss',
})
export class ConfiguratorTutorialPanelComponent {
    readonly steps = input.required<ConfiguratorTutorialStep[]>();
    readonly stepIndex = input.required<number>();

    readonly previous = output<void>();
    readonly next = output<void>();
    readonly skip = output<void>();
    readonly finish = output<void>();

    readonly currentStep = computed(() => this.steps()[this.stepIndex()]);
    readonly isFirstStep = computed(() => this.stepIndex() === 0);
    readonly isLastStep = computed(() => this.stepIndex() >= this.steps().length - 1);
    readonly stepLabel = computed(() => `${this.stepIndex() + 1} of ${this.steps().length}`);
}
