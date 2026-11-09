import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import App from './App';
import { AuthProvider } from './auth/AuthProvider';
import './styles/tokens.css';
import './styles/global.css';

const raiz = document.getElementById('root');
if (!raiz) throw new Error('No se encontró el elemento #root.');

createRoot(raiz).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
);
