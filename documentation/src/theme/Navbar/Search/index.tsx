import type { ReactNode } from 'react';
import { SearchModalTrigger } from '../../../components/SearchModal';

// Adapted from the Artemis documentation (https://github.com/ls1intum/Artemis, MIT License).

/**
 * Replaces the navbar search box with a button that opens the search dialog.
 */
export default function NavbarSearchWrapper(): ReactNode {
    return <SearchModalTrigger />;
}
