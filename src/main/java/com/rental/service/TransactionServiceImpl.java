package com.rental.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

import com.rental.model.Transaction;

@Service
public class TransactionServiceImpl implements TransactionService {

	private final Map<Long, Transaction> storage = new ConcurrentHashMap<>();
	private final AtomicLong idGenerator = new AtomicLong(1);

	@Override
	public Transaction createTransaction(Transaction transaction) {
		if (transaction == null) {
			throw new IllegalArgumentException("Transaction is required.");
		}

		if (transaction.getId() == null) {
			transaction.setId(idGenerator.getAndIncrement());
		}

		if (transaction.getCreatedAt() == null) {
			transaction.setCreatedAt(LocalDateTime.now());
		}
		transaction.setUpdatedAt(LocalDateTime.now());

		storage.put(transaction.getId(), transaction);
		return transaction;
	}

	@Override
	public Optional<Transaction> getTransactionById(Long id) {
		if (id == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(storage.get(id));
	}

	@Override
	public List<Transaction> getTransactionsByBuyer(Long buyerId) {
		List<Transaction> result = new ArrayList<>();
		for (Transaction tx : storage.values()) {
			if (buyerId != null && buyerId.equals(tx.getBuyerId())) {
				result.add(tx);
			}
		}
		return result;
	}

	@Override
	public List<Transaction> getTransactionsBySeller(Long sellerId) {
		List<Transaction> result = new ArrayList<>();
		for (Transaction tx : storage.values()) {
			if (sellerId != null && sellerId.equals(tx.getSellerId())) {
				result.add(tx);
			}
		}
		return result;
	}

	@Override
	public List<Transaction> getAllTransactions() {
		return new ArrayList<>(storage.values());
	}

	@Override
	public Transaction updateTransaction(Transaction transaction) {
		if (transaction == null || transaction.getId() == null) {
			throw new IllegalArgumentException("Transaction id is required for update.");
		}
		if (!storage.containsKey(transaction.getId())) {
			throw new IllegalArgumentException("Transaction not found.");
		}

		if (transaction.getCreatedAt() == null) {
			transaction.setCreatedAt(LocalDateTime.now());
		}
		transaction.setUpdatedAt(LocalDateTime.now());

		storage.put(transaction.getId(), transaction);
		return transaction;
	}

	@Override
	public void deleteTransaction(Long id) {
		if (id == null) {
			return;
		}
		storage.remove(id);
	}
}

