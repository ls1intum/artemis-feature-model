import type { ReactNode } from 'react';
import { SearchModalProvider } from '../components/SearchModal';

interface RootProps {
    children: ReactNode;
}

/**
 * Wraps the whole site so that the navbar search button can open the search dialog on every page.
 */
export default function Root({ children }: RootProps): ReactNode {
    return <SearchModalProvider>{children}</SearchModalProvider>;
}
