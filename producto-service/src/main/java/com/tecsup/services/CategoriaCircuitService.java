package com.tecsup.services;

import com.tecsup.client.CategoriaClient;
import com.tecsup.dto.CategoriaDTO;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import io.github.resilience4j.retry.annotation.Retry;

@Service
public class CategoriaCircuitService {

    @Autowired
    private CategoriaClient categoriaClient;

    @Bulkhead(
            name = "categoriaBulkhead",
            fallbackMethod = "categoriaFallback"
    )
    @Retry(
            name = "categoriaRetry",
            fallbackMethod = "categoriaFallback"
    )
    @CircuitBreaker(
            name = "categoriaService",
            fallbackMethod = "categoriaFallback"
    )
    public CategoriaDTO obtenerCategoria(Long id) {
        System.out.println("Llamando a categoria-service...");
        return categoriaClient.obtenerCategoria(id);
    }

    public CategoriaDTO categoriaFallback(Long id, Exception ex) {
        System.out.println("Fallback ejecutado");

        CategoriaDTO dto = new CategoriaDTO();
        dto.setId(id);
        dto.setNombre("Categoría temporal no disponible");
        return dto;
    }
}