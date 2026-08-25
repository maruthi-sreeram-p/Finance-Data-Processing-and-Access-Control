import { useContext } from 'react';
import { AuthContext, type AuthContextValue } from '@/context/AuthContext';

/**
 * Access to the authentication state.
 *
 * Throws when used outside the provider rather than returning null. A silent null
 * would surface far from the cause as "cannot read property user of null"; this
 * names the actual mistake at the point it happens.
 */
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);

  if (context === null) {
    throw new Error('useAuth must be used within an <AuthProvider>');
  }

  return context;
}
