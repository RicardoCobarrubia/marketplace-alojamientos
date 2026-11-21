package co.edu.unbosque.marketplace.usuarios.aplicacion;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import co.edu.unbosque.marketplace.comun.configuracion.CacheConfig;

class SesionesEnCacheTest {

	private final CacheManager cacheManager = mock(CacheManager.class);
	private final Cache cache = mock(Cache.class);
	private final SesionesEnCache sesiones = new SesionesEnCache(cacheManager);

	@AfterEach
	void limpiar() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void dentroDeUnaTransaccionOlvidaLaCuentaSoloAlConfirmar() {
		when(cacheManager.getCache(CacheConfig.VERSIONES_SESION)).thenReturn(cache);
		TransactionSynchronizationManager.initSynchronization();

		sesiones.olvidarAlConfirmar("ana@correo.com");
		verify(cache, never()).evict("ana@correo.com");

		TransactionSynchronizationUtils.invokeAfterCommit(TransactionSynchronizationManager.getSynchronizations());
		verify(cache).evict("ana@correo.com");
	}

	@Test
	void sinTransaccionLaOlvidaDeInmediatoYToleraQueNoHayaCache() {
		when(cacheManager.getCache(CacheConfig.VERSIONES_SESION)).thenReturn(cache).thenReturn(null);

		sesiones.olvidarAlConfirmar("ana@correo.com");
		sesiones.olvidarAlConfirmar("ana@correo.com");

		verify(cache).evict("ana@correo.com");
	}
}
