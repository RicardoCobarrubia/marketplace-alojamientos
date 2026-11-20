package co.edu.unbosque.marketplace.usuarios.aplicacion;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import co.edu.unbosque.marketplace.comun.configuracion.CacheConfig;

/**
 * La caché de 60 s con la que el filtro de seguridad lee las cuentas (ADR-010). Cuando una
 * cuenta cambia, esta réplica la olvida en cuanto se confirma la transacción: la siguiente
 * petición vuelve a leerla de la base de datos. Las demás réplicas la ven en máximo 60 s.
 */
@Component
public class SesionesEnCache {

	private final CacheManager cacheManager;

	public SesionesEnCache(CacheManager cacheManager) {
		this.cacheManager = cacheManager;
	}

	/** Olvida la cuenta cuando la transacción en curso se confirme; si no hay transacción, ya. */
	public void olvidarAlConfirmar(String email) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			olvidar(email);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				olvidar(email);
			}
		});
	}

	private void olvidar(String email) {
		Cache cache = cacheManager.getCache(CacheConfig.VERSIONES_SESION);
		if (cache != null) {
			cache.evict(email);
		}
	}
}
