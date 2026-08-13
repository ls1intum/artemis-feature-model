import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
    selector: 'fm-app',
    standalone: true,
    imports: [RouterOutlet, RouterLink, RouterLinkActive],
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: `
        <nav class="navbar bg-body border-bottom py-2">
            <div class="container-xxl justify-content-start gap-4">
                <a class="navbar-brand fw-semibold m-0" routerLink="/feature-model/configurator">Artemis Feature Model</a>
                <ul class="nav nav-underline">
                    <li class="nav-item">
                        <a class="nav-link" routerLink="/feature-model/explorer" routerLinkActive="active">Explorer</a>
                    </li>
                    <li class="nav-item">
                        <a class="nav-link" routerLink="/feature-model/configurator" routerLinkActive="active">Configurator</a>
                    </li>
                </ul>
            </div>
        </nav>
        <main class="container-xxl py-4">
            <router-outlet />
        </main>
    `,
    styles: [
        `
            :host {
                display: block;
                min-height: 100vh;
            }
            .nav-link {
                color: var(--bs-secondary-color);
            }
            .nav-link.active {
                font-weight: 600;
            }
        `,
    ],
})
export class AppComponent {}
