package com.pstu.backend.repository;

import com.pstu.backend.domain.MoneyRequest;
import com.pstu.backend.domain.MoneyRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MoneyRequestRepository extends JpaRepository<MoneyRequest, Long> {

    List<MoneyRequest> findByRequesterAccountIdAndStatus(Long requesterAccountId, MoneyRequestStatus status);

    List<MoneyRequest> findByDebtorAccountIdAndStatus(Long debtorAccountId, MoneyRequestStatus status);

    @Query("select r from MoneyRequest r where r.debtorAccountId = :accountId and r.status = 'PENDING' order by r.createdAt desc")
    List<MoneyRequest> findPendingForDebtor(@Param("accountId") Long accountId);

    @Query("select r from MoneyRequest r where r.requesterAccountId = :accountId and r.status = 'PENDING' order by r.createdAt desc")
    List<MoneyRequest> findPendingForRequester(@Param("accountId") Long accountId);
}
