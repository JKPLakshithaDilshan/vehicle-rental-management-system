package com.rental.service;

import com.rental.model.Seller;
import java.util.List;
import java.util.Optional;

public interface SellerService {
    Seller registerSeller(Seller seller);
    Optional<Seller> getSellerById(Long id);
    Optional<Seller> getSellerByEmail(String email);
    List<Seller> searchSellers(String name, String location);
    Seller updateSeller(Seller seller);
    void deleteSeller(Long id);
    // Admin approval methods
    Seller approveSeller(Long id);
    Seller rejectSeller(Long id);
    List<Seller> getPendingSellers();
}
