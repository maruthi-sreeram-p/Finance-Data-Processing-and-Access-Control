import { useContext } from 'react';
import { ToastContext, type ToastContextValue } from '@/context/ToastContext';

export function useToast(): ToastContextValue {
  const context = useContext(ToastContext);

  if (context === null) {
    throw new Error('useToast must be used within a <ToastProvider>');
  }

  return context;
}
