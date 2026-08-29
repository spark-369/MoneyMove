package com.pstu.backend.controller;

import com.pstu.backend.dto.request.MoneyRequestCreateRequest;
import com.pstu.backend.dto.response.MoneyRequestResponse;
import com.pstu.backend.dto.response.TransactionResponse;
import com.pstu.backend.service.MoneyRequestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/requests")
public class MoneyRequestController {

    private final MoneyRequestService moneyRequestService;

    public MoneyRequestController(MoneyRequestService moneyRequestService) {
        this.moneyRequestService = moneyRequestService;
    }

    @PostMapping
    public ResponseEntity<MoneyRequestResponse> create(@Valid @RequestBody MoneyRequestCreateRequest request) {
        return ResponseEntity.ok(moneyRequestService.createRequest(request));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<TransactionResponse> accept(@PathVariable Long id) {
        return ResponseEntity.ok(moneyRequestService.acceptRequest(id));
    }

    @PostMapping("/{id}/decline")
    public ResponseEntity<MoneyRequestResponse> decline(@PathVariable Long id) {
        return ResponseEntity.ok(moneyRequestService.declineRequest(id));
    }

    @GetMapping("/incoming")
    public ResponseEntity<List<MoneyRequestResponse>> incoming() {
        return ResponseEntity.ok(moneyRequestService.listIncoming());
    }

    @GetMapping("/outgoing")
    public ResponseEntity<List<MoneyRequestResponse>> outgoing() {
        return ResponseEntity.ok(moneyRequestService.listOutgoing());
    }
}
