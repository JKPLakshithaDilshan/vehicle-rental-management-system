package com.rental.service;

import com.rental.model.Transaction;
import java.util.List;
import java.util.Optional;

public interface TransactionService {
    Transaction createTransaction(Transaction transaction);
    Optional<Transaction> getTransactionById(Long id);
    List<Transaction> getTransactionsByBuyer(Long buyerId);
    List<Transaction> getTransactionsBySeller(Long sellerId);
    List<Transaction> getAllTransactions();
    Transaction updateTransaction(Transaction transaction);
    void deleteTransaction(Long id);
}
