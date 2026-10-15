import type { ComponentType, CSSProperties, ReactElement, SVGProps } from 'react';

// Adapted from the Artemis documentation (https://github.com/ls1intum/Artemis, MIT License).

export enum ImageSize {
    small = 'small',
    medium = 'medium',
    large = 'large',
}

interface ImageProps {
    src: string | ComponentType<SVGProps<SVGSVGElement>>;
    alt: string;
    size?: ImageSize;
    style?: CSSProperties;
    hideBorder?: boolean;
    caption?: string;
    inline?: boolean;
}

const SIZE_STYLES: Record<ImageSize, CSSProperties> = {
    [ImageSize.small]: { maxWidth: '300px' },
    [ImageSize.medium]: { maxWidth: '600px' },
    [ImageSize.large]: { maxWidth: '100%' },
};

/**
 * A framed image with an optional caption, used for screenshots.
 *
 * @param props.src the imported image, or an SVG component imported through SVGR
 * @param props.alt the alternative text
 * @param props.size the maximum width of the image (default: medium)
 * @param props.style styles that override the default styles
 * @param props.hideBorder whether to omit the frame
 * @param props.caption a caption shown below the image
 * @param props.inline whether to render the image inside a line of text
 * @returns the rendered image
 */
export default function Image({ src, alt, size = ImageSize.medium, style, hideBorder, caption, inline, ...rest }: ImageProps): ReactElement {
    const defaultImageStyles: CSSProperties = {
        ...SIZE_STYLES[size],
        width: 'auto',
        height: 'auto',
        objectFit: 'contain',
        border: hideBorder || inline ? 'none' : '1px solid var(--ifm-color-emphasis-300)',
        borderRadius: inline ? '0' : '8px',
        margin: '0',
        padding: inline ? '0 0.25rem' : '0.5rem',
        display: inline ? 'inline' : 'block',
        verticalAlign: inline ? 'middle' : undefined,
    };
    const combinedImageStyles: CSSProperties = { ...defaultImageStyles, ...style };

    const renderImage = (): ReactElement => {
        if (typeof src === 'string') {
            return <img src={src} alt={alt} style={combinedImageStyles} {...rest} />;
        }
        const SvgComponent = src;
        return <SvgComponent role="img" aria-label={alt} style={combinedImageStyles} />;
    };

    if (inline) {
        return renderImage();
    }

    return (
        <figure style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '0.125rem', margin: '1.5rem 0', width: 'fit-content' }}>
            {renderImage()}
            {caption && <figcaption style={{ fontSize: '0.75rem', fontWeight: 'bold', textAlign: 'center' }}>{caption}</figcaption>}
        </figure>
    );
}
