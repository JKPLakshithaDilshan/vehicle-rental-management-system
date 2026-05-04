package com.rental.repository;

import com.rental.model.Seller;
import java.util.List;
import java.util.Optional;

public interface SellerRepository {
    Seller save(Seller seller);
    Optional<Seller> findById(Long id);
    Optional<Seller> findByEmail(String email);
    List<Seller> findAll();
    List<Seller> findByName(String name);
    List<Seller> findByLocation(String location);
    void deleteById(Long id);
}
