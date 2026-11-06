package com.example.bankapp.service;

import com.example.bankapp.exception.AccountNotFoundException;
import com.example.bankapp.exception.BankException;
import com.example.bankapp.exception.InsufficientBalanceException;
import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.Customer;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.repository.BankTransactionRepository;
import com.example.bankapp.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)          // default for every method: read-only transaction
public class BankService {
    private final CustomerRepository customerRepository;
    private final BankTransactionRepository transactionRepository;

    // Get all customers
    public List<Customer> getAllCustomers() {
        return customerRepository.findAllOrdered();
    }

    // Get customer by account
    public Customer getCustomer(String accountNumber) {
        return customerRepository.getByAccountNumber(accountNumber)
                .orElseThrow(() ->
                        new AccountNotFoundException(accountNumber));
    }

    // Search customers
    public List<Customer> searchCustomers(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return getAllCustomers();
        }

        return customerRepository.searchByName(keyword.trim());
    }

    // Total bank balance
    public BigDecimal getTotalBalance() {
        BigDecimal total = customerRepository.getTotalBalance();
        return total == null ? BigDecimal.ZERO : total;
    }

    // Successful transaction count
    public long getSuccessCount() {
        return transactionRepository.countByStatus(
                TransactionStatus.SUCCESS);
    }

    // Failed transaction count
    public long getFailedCount() {
        return transactionRepository.countByStatus(
                TransactionStatus.FAILED);
    }

    // Latest transactions
    public List<BankTransaction> getLatestTransactions() {
        return transactionRepository.findAllLatest();
    }

    // Add a customer
    @Transactional
    public Customer createCustomer(Customer customer) {

        if (customerRepository.countAccounts(
                customer.getAccountNumber()) > 0) {
            throw new BankException("Account number already exists");
        }

        if (customerRepository.countEmails(
                customer.getEmail()) > 0) {
            throw new BankException("Email already exists");
        }

        return customerRepository.save(customer);
    }

    // Main bank transfer
    @Transactional(rollbackFor = Exception.class)
    public BankTransaction transfer(
            String fromAccount,
            String toAccount,
            BigDecimal amount,
            boolean simulateFailure) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankException("Amount must be greater than zero");
        }

        if (fromAccount.equals(toAccount)) {
            throw new BankException(
                    "Sender and receiver accounts must be different");
        }

        // Check sender and receiver exist
        getCustomer(fromAccount);
        getCustomer(toAccount);

        // Step 1: Debit sender
        int debitResult = customerRepository.debit(
                fromAccount, amount);

        if (debitResult == 0) {
            throw new InsufficientBalanceException(fromAccount);
        }

        // Step 2: Simulate failure after withdrawal
        if (simulateFailure) {
            throw new BankException(
                    "All changes were rolled back due to simulated failure");
        }

        // Step 3: Credit receiver
        int creditResult = customerRepository.credit(
                toAccount, amount);

        if (creditResult == 0) {
            throw new AccountNotFoundException(toAccount);
        }

        // Step 4: Save successful transaction
        BankTransaction transaction = new BankTransaction(
                fromAccount,
                toAccount,
                amount,
                TransactionStatus.SUCCESS,
                "Bank transfer completed successfully"
        );

        return transactionRepository.save(transaction);
    }

    // Get account history
    public List<BankTransaction> getAccountHistory(
            String accountNumber) {

        return transactionRepository.findHistory(accountNumber);
    }

    // Deposit money
    @Transactional(rollbackFor = Exception.class)
    public BankTransaction deposit(
            String accountNumber,
            BigDecimal amount) {

        validateAmount(amount);

        int result = customerRepository.credit(
                accountNumber, amount);

        if (result == 0) {
            throw new AccountNotFoundException(accountNumber);
        }

        BankTransaction transaction = new BankTransaction(
                null,
                accountNumber,
                amount,
                TransactionStatus.SUCCESS,
                "Cash deposit"
        );

        return transactionRepository.save(transaction);
    }

    // Withdraw money
    @Transactional(rollbackFor = Exception.class)
    public BankTransaction withdraw(
            String accountNumber,
            BigDecimal amount) {

        validateAmount(amount);

        int result = customerRepository.debit(
                accountNumber, amount);

        if (result == 0) {
            Optional<Customer> customer =
                    customerRepository.getByAccountNumber(accountNumber);

            if (customer.isEmpty()) {
                throw new AccountNotFoundException(accountNumber);
            }

            throw new InsufficientBalanceException(accountNumber);
        }

        BankTransaction transaction = new BankTransaction(
                accountNumber,
                null,
                amount,
                TransactionStatus.SUCCESS,
                "Cash withdrawal"
        );

        return transactionRepository.save(transaction);
    }

    // Large transfers
    public List<BankTransaction> getLargeTransfers(
            BigDecimal minAmount) {

        return transactionRepository.findLargeTransfers(minAmount);
    }

    // Total sent by an account
    public BigDecimal getTotalSent(String accountNumber) {

        BigDecimal total = transactionRepository.getTotalSent(
                accountNumber,
                TransactionStatus.SUCCESS
        );

        return total == null ? BigDecimal.ZERO : total;
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null ||
                amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankException(
                    "Amount must be greater than zero");
        }
    }

}
