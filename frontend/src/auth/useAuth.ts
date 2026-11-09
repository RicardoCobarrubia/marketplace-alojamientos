import { useContext } from 'react';
import { AuthContext, type ValorAuth } from './contexto';

export function useAuth(): ValorAuth {
  const valor = useContext(AuthContext);
  if (!valor) throw new Error('useAuth debe usarse dentro de <AuthProvider>.');
  return valor;
}
