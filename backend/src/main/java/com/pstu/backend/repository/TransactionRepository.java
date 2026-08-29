package com.pstu.backend.repository;

import com.pstu.backend.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByReferenceId(String referenceId);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    List<Transaction> findBySenderAccountIdOrReceiverAccountId(Long senderId, Long receiverId);

    @Query("select t from Transaction t where (t.senderAccountId = :accountId or t.receiverAccountId = :accountId) order by t.createdAt desc")
    List<Transaction> findHistoryForAccount(@Param("accountId") Long accountId);
}
