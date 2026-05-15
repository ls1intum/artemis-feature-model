import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
    selector: 'fm-app',
    standalone: true,
    imports: [RouterOutlet, RouterLink, RouterLinkActive],
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: `
        <nav class="navbar navbar-expand-lg navbar-dark bg-dark px-3">
            <a class="navbar-brand" routerLink="/feature-model/explorer">Artemis Feature Model</a>
            <ul class="navbar-nav me-auto">
                <li class="nav-item">
                    <a class="nav-link" routerLink="/feature-model/explorer" routerLinkActive="active">Explorer</a>
                </li>
                <li class="nav-item">
                    <a class="nav-link" routerLink="/feature-model/configurator" routerLinkActive="active">Configurator</a>
                </li>
            </ul>
        </nav>
        <main class="container-fluid py-3">
            <router-outlet />
        </main>
    `,
    styles: [
        `
            :host {
                display: block;
                min-height: 100vh;
            }
            .nav-link.active {
                font-weight: 600;
            }
        `,
    ],
})
export class AppComponent {}
