## Client Styling and Theming

- Use SCSS for component styles.
- Prefer component-local styles over broad global styles.
- Use BEM-style class structure for custom component CSS where it improves readability.
- Do not hard-code color values in component styles, templates, or TypeScript.
- Prefer theme-aware defaults, Bootstrap utility classes, or CSS variables such as `var(--bs-body-color)` and `var(--bs-body-bg)`.
- If a custom color is unavoidable, define a named CSS variable and provide theme-aware values in the appropriate global theme file.
- Verify UI changes in light and dark themes once theme support exists.
- Do not use `::ng-deep`.
- Use responsive layouts that adapt to small screens. Prefer a `.container` or deliberate responsive layout over brittle nested grid wrappers.