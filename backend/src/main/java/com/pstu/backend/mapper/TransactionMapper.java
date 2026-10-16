package com.pstu.backend.mapper;

import com.pstu.backend.domain.Transaction;
import com.pstu.backend.dto.response.TransactionResponse;
import com.pstu.backend.repository.UserRepository;

/**
 * Single source of truth for turning a {@link Transaction} into a
 * {@link TransactionResponse}. Transactions store <em>account</em> ids, so the
 * owning username must be resolved via {@code UserRepository.findByAccountId}
 * (never {@code findById}, which expects a <em>user</em> id).
 */
public final class TransactionMapper {

    private TransactionMapper() {
    }

    public static TransactionResponse toResponse(Transaction tx, UserRepository userRepository) {
        String senderUsername = userRepository.findByAccountId(tx.getSenderAccountId())
                .map(user -> user.getUsername())
                .orElse(null);
        String receiverUsername = userRepository.findByAccountId(tx.getReceiverAccountId())
                .map(user -> user.getUsername())
                .orElse(null);
        return new TransactionResponse(
                tx.getReferenceId(),
                tx.getType().name(),
                tx.getStatus().name(),
                tx.getSenderAccountId(),
                tx.getReceiverAccountId(),
                senderUsername,
                receiverUsername,
                tx.getAmount(),
                tx.getCurrency(),
                tx.getDescription(),
                tx.getCreatedAt());
    }
}
