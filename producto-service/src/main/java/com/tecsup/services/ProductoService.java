package com.tecsup.services;

import com.tecsup.dto.CategoriaDTO;
import com.tecsup.exception.ResourceNotFoundException;
import com.tecsup.model.Producto;
import com.tecsup.repository.ProductoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductoService {

    @Autowired
    private ProductoRepository repo;

    @Autowired
    private CategoriaCircuitService categoriaCircuitService;

    public List<Producto> listar() {
        List<Producto> productos = repo.findAll();
        productos.forEach(p -> {
            CategoriaDTO categoria =
                    categoriaCircuitService.obtenerCategoria(p.getCategoriaId());
            p.setCategoria(categoria);
        });
        return productos;
    }

    public Producto guardar(Producto p) {
        Producto producto = repo.save(p);
        CategoriaDTO categoria =
                categoriaCircuitService.obtenerCategoria(producto.getCategoriaId());
        producto.setCategoria(categoria);
        return producto;
    }

    public Producto obtener(Long id) {
        Producto producto = repo.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Producto no encontrado con id: " + id));
        CategoriaDTO categoria =
                categoriaCircuitService.obtenerCategoria(producto.getCategoriaId());
        producto.setCategoria(categoria);
        return producto;
    }

    public void eliminar(Long id) {
        Producto producto = repo.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Producto no encontrado con id: " + id));
        repo.delete(producto);
    }
}