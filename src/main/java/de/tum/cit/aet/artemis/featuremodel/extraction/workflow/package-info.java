/**
 * Guided-workflow capability of the extraction pipeline: extraction-time validation of the authored workflow against
 * the generated model behind {@code WorkflowStageService}, and the deliberate maintainer scaffold sync in
 * {@code GuidedWorkflowScaffoldService}. The two entry points do not call each other. Depends on
 * {@code extraction.pipeline}, {@code extraction.domain}, and the runtime contract types in {@code catalog},
 * {@code deployment}, and {@code selection}; never on the scan, model, or snapshot packages.
 */
package de.tum.cit.aet.artemis.featuremodel.extraction.workflow;
