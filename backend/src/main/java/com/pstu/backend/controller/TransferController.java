package com.pstu.backend.controller;

import com.pstu.backend.dto.request.TransferRequest;
import com.pstu.backend.dto.response.TransactionResponse;
import com.pstu.backend.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> transfer(@Valid @RequestBody TransferRequest request) {
        return ResponseEntity.ok(transferService.transfer(request));
    }

    @GetMapping("/{referenceId}")
    public ResponseEntity<TransactionResponse> getByReference(@PathVariable String referenceId) {
        return ResponseEntity.ok(transferService.getByReference(referenceId));
    }
}
