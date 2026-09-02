package com.example.wallet.controller;

import com.example.wallet.dto.TransactionRequest;
import com.example.wallet.dto.TransactionResponse;
import com.example.wallet.service.TransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TransactionController – the entry point for HTTP requests.
 *
 * This class ONLY handles HTTP concerns:
 *   - What URL to listen on
 *   - What HTTP method (GET, POST, etc.)
 *   - Reading the request body
 *   - Returning an HTTP response
 *
 * All business logic lives in TransactionService.
 * The controller should never touch the database directly.
 *
 * @RestController = @Controller + @ResponseBody
 *   → Spring automatically converts return values to JSON
 *
 * @RequestMapping("/api/v1/transactions")
 *   → all methods in this controller are prefixed with this URL
 */
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    /** Constructor injection – Spring wires the service automatically */
    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * POST /api/v1/transactions/process
     *
     * Accepts a JSON body (TransactionRequest), delegates to the service,
     * and returns a 200 OK response with the result.
     *
     * @RequestBody tells Spring to parse the incoming JSON into a TransactionRequest object.
     *
     * Example curl:
     * curl -X POST http://localhost:8080/api/v1/transactions/process \
     *   -H "Content-Type: application/json" \
     *   -d '{"transactionId":"tx-001","userId":"user-1","amount":100.00,"type":"DEBIT"}'
     */
    @PostMapping("/process")
    public ResponseEntity<TransactionResponse> processTransaction(
            @RequestBody TransactionRequest request) {

        TransactionResponse response = transactionService.processTransaction(request);
        return ResponseEntity.ok(response);   // HTTP 200 OK + JSON body
    }
}
