import type { CSSProperties, ReactNode } from 'react';
import { CALLOUT_STYLE_CONFIG, CalloutVariant, type CalloutProps } from './Callout.types';

// Adapted from the Artemis documentation (https://github.com/ls1intum/Artemis, MIT License).

const ICON_STYLE: CSSProperties = {
    fontSize: '1.2em',
    alignSelf: 'center',
    flexShrink: 0,
};

const CONTENT_STYLE: CSSProperties = {
    flex: 1,
    minWidth: 0,
};

/**
 * A banner that sets a note, tip, or warning apart from the surrounding text.
 */
export default function Callout({ children, variant = CalloutVariant.success }: CalloutProps): ReactNode {
    const currentStyle = CALLOUT_STYLE_CONFIG[variant];

    const bannerStyle: CSSProperties = {
        backgroundColor: currentStyle.backgroundColor,
        borderLeft: `5px solid ${currentStyle.borderColor}`,
        padding: '15px',
        borderRadius: '5px',
        display: 'flex',
        alignItems: 'center',
        gap: '12px',
        margin: '0.5rem',
    };

    return (
        <aside style={bannerStyle} role="note" className="callout">
            <span style={ICON_STYLE} aria-hidden="true">
                {currentStyle.icon}
            </span>
            <div style={CONTENT_STYLE} className="callout-content">
                <span className="sr-only">{currentStyle.label}: </span>
                {children}
            </div>
        </aside>
    );
}
