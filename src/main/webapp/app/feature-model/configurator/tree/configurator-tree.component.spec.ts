import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { Feature, FeatureTreeNode, IncomingRelation } from '../../core/feature-model.types';
import { ConfiguratorTreeComponent } from './configurator-tree.component';

function feature(id: string, name: string, kind: 'root' | 'group' | 'feature' | 'sub-feature', selectable: boolean): Feature {
    return {
        id,
        name,
        kind,
        selectable,
        description: null,
        defaultState: selectable ? 'disabled' : 'not_applicable',
        source: null,
        category: kind === 'root' ? 'derived' : 'technical',
        visibleTo: ['maintainer'],
        configurableBy: selectable ? ['maintainer'] : [],
        requiresCapabilities: [],
        artifactMappings: [],
        extraction: null,
    };
}

function incoming(
    parentId: string,
    childId: string,
    relationType: 'mandatory' | 'optional' | 'group',
    groupType: 'alternative' | null = null,
): IncomingRelation {
    return { parentId, childId, relationType, groupType, order: 1 };
}

function technicalTree(): FeatureTreeNode {
    const mysql: FeatureTreeNode = {
        feature: feature('mysql', 'MySQL', 'feature', true),
        incomingRelation: incoming('database', 'mysql', 'optional'),
        children: [],
    };
    const postgresql: FeatureTreeNode = {
        feature: feature('postgresql', 'PostgreSQL', 'feature', true),
        incomingRelation: incoming('database', 'postgresql', 'optional'),
        children: [],
    };
    const database: FeatureTreeNode = {
        feature: feature('database', 'Database', 'group', false),
        incomingRelation: incoming('artemis', 'database', 'group', 'alternative'),
        children: [mysql, postgresql],
    };
    const repositories: FeatureTreeNode = {
        feature: {
            ...feature('localvc/vcs/repositories', 'Repositories', 'sub-feature', false),
            category: 'derived',
            source: { configKey: null, springProfile: 'localvc', clientConstant: null, serverConditionClass: null, usageLabel: 'vcs/repositories', evidence: ['LocalVCResource.java:40'] },
        },
        incomingRelation: incoming('localvc', 'localvc/vcs/repositories', 'mandatory'),
        children: [],
    };
    const localvc: FeatureTreeNode = {
        feature: feature('localvc', 'Local Version Control', 'feature', true),
        incomingRelation: incoming('artemis', 'localvc', 'mandatory'),
        children: [repositories],
    };
    return {
        feature: feature('artemis', 'Artemis', 'root', false),
        incomingRelation: null,
        children: [database, localvc],
    };
}

describe('ConfiguratorTreeComponent', () => {
    let fixture: ComponentFixture<ConfiguratorTreeComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({ imports: [ConfiguratorTreeComponent] });
        fixture = TestBed.createComponent(ConfiguratorTreeComponent);
        fixture.componentRef.setInput('tree', technicalTree());
        fixture.componentRef.setInput('selectedFeatureIds', new Set(['mysql', 'localvc']));
        fixture.componentRef.setInput('selectableFeatureIds', new Set(['mysql', 'postgresql', 'localvc']));
        fixture.componentRef.setInput('violationIds', new Set<string>());
        fixture.componentRef.setInput('warningIds', new Set<string>());
        fixture.componentRef.setInput('localizedViolations', []);
        fixture.componentRef.setInput('localizedWarnings', []);
        fixture.componentRef.setInput('guidedOptions', []);
        fixture.componentRef.setInput('featureAvailabilityById', new Map());
        fixture.componentRef.setInput('validationLoading', false);
        fixture.componentRef.setInput('hasValidationResult', true);
        fixture.componentRef.setInput('isValid', true);
        fixture.detectChanges();
    });

    it('selects an alternative radio-style and deselects its sibling', () => {
        const emittedSelections: ReadonlySet<string>[] = [];
        fixture.componentInstance.selectionChange.subscribe((selection) => emittedSelections.push(selection));
        fixture.componentInstance.onSelectFeature('postgresql');
        fixture.detectChanges();

        const toggle = fixture.nativeElement.querySelector('[data-testid="tree-details-toggle"]') as HTMLInputElement;
        expect(toggle.type).toBe('radio');
        expect(toggle.checked).toBe(false);
        toggle.click();

        expect(emittedSelections).toHaveLength(1);
        expect([...emittedSelections[0]]).toEqual(['localvc', 'postgresql']);
    });

    it('does not allow the last selected alternative to be cleared', () => {
        const emitSpy = vi.spyOn(fixture.componentInstance.selectionChange, 'emit');
        fixture.componentInstance.onSelectFeature('mysql');
        fixture.detectChanges();

        const toggle = fixture.nativeElement.querySelector('[data-testid="tree-details-toggle"]') as HTMLInputElement;
        expect(toggle.type).toBe('radio');
        expect(toggle.checked).toBe(true);
        expect(toggle.disabled).toBe(true);

        fixture.componentInstance.onToggleSelection('mysql');
        expect(emitSpy).not.toHaveBeenCalled();
    });

    it('hides sub-feature nodes and their badge from the configurator tree', () => {
        fixture.componentInstance.onExpandAll();
        fixture.detectChanges();

        const ids = Array.from(fixture.nativeElement.querySelectorAll('.diagram-node') as NodeListOf<Element>).map((node) => node.getAttribute('data-feature-id'));
        expect(ids).toEqual(['artemis', 'database', 'localvc', 'mysql', 'postgresql']);
        expect(fixture.nativeElement.querySelector('[data-testid="sub-feature-badge"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('.diagram-toggle[data-feature-id="localvc"]')).toBeNull();
        expect(fixture.componentInstance.expandableIds()).toEqual(['artemis', 'database']);
        expect(fixture.componentInstance.treeToggleableFeatureIds().has('localvc/vcs/repositories')).toBe(false);

        fixture.componentInstance.onSearchInput('vcs/');
        fixture.detectChanges();
        expect(fixture.componentInstance.matchCount()).toBe(0);
    });

    it('renders a selected mandatory technical leaf as locked', () => {
        fixture.componentInstance.onSelectFeature('localvc');
        fixture.detectChanges();

        const toggle = fixture.nativeElement.querySelector('[data-testid="tree-details-toggle"]') as HTMLInputElement;
        expect(toggle.type).toBe('checkbox');
        expect(toggle.checked).toBe(true);
        expect(toggle.disabled).toBe(true);
        expect(fixture.nativeElement.textContent).toContain('Local Version Control is mandatory');
        expect(fixture.componentInstance.treeToggleableFeatureIds().has('localvc')).toBe(false);
    });
});
