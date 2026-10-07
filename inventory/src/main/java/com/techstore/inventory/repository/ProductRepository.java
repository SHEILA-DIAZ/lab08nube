package com.techstore.inventory.repository;
import com.techstore.inventory.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByStoreOrderByIdAsc(String store);
    boolean existsByNameAndStore(String name, String store);
}
