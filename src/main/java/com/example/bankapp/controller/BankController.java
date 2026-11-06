package com.example.bankapp.controller;

import com.example.bankapp.dto.TransferRequest;
import com.example.bankapp.exception.BankException;
import com.example.bankapp.model.Customer;
import com.example.bankapp.service.BankService;
import com.example.bankapp.service.TransactionAuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;

@Controller
@RequiredArgsConstructor
public class BankController {

    private final BankService bankService;
    private final TransactionAuditService auditService;

    // Dashboard
    @GetMapping("/")
    public String dashboard(
            @RequestParam(required = false) String keyword,
            Model model) {

        model.addAttribute("customers",
                bankService.searchCustomers(keyword));

        model.addAttribute("allCustomers",
                bankService.getAllCustomers());

        model.addAttribute("transactions",
                bankService.getLatestTransactions());

        model.addAttribute("totalCustomers",
                bankService.getAllCustomers().size());

        model.addAttribute("totalBalance",
                bankService.getTotalBalance());

        model.addAttribute("successCount",
                bankService.getSuccessCount());

        model.addAttribute("failedCount",
                bankService.getFailedCount());

        model.addAttribute("keyword", keyword);

        if (!model.containsAttribute("transferRequest")) {
            model.addAttribute("transferRequest",
                    new TransferRequest());
        }

        if (!model.containsAttribute("customer")) {
            model.addAttribute("customer", new Customer());
        }

        return "bank";
    }

    // Add customer
    @PostMapping("/customers")
    public String addCustomer(
            @Valid @ModelAttribute("customer") Customer customer,
            BindingResult result,
            RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute(
                    "org.springframework.validation.BindingResult.customer",
                    result);

            redirectAttributes.addFlashAttribute("customer", customer);

            return "redirect:/";
        }

        try {
            bankService.createCustomer(customer);

            redirectAttributes.addFlashAttribute(
                    "success", "Customer added successfully!");

        } catch (BankException ex) {
            redirectAttributes.addFlashAttribute(
                    "error", ex.getMessage());
        }

        return "redirect:/";
    }

    // Transfer money
    @PostMapping("/transfer")
    public String transfer(
            @Valid @ModelAttribute("transferRequest")
            TransferRequest request,
            BindingResult result,
            RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute(
                    "error", "Please enter valid transfer details.");

            return "redirect:/";
        }

        try {
            bankService.transfer(
                    request.getFromAccount(),
                    request.getToAccount(),
                    request.getAmount(),
                    request.isSimulateFailure()
            );

            redirectAttributes.addFlashAttribute(
                    "success", "Transfer completed successfully!");

        } catch (Exception ex) {

            // This executes after the transfer transaction has rolled back.
            try {
                auditService.recordFailure(
                        request.getFromAccount(),
                        request.getToAccount(),
                        request.getAmount(),
                        ex.getMessage()
                );
            } catch (Exception auditException) {
                redirectAttributes.addFlashAttribute(
                        "error", ex.getMessage()
                                + ". Failed to save audit record.");
                return "redirect:/";
            }

            redirectAttributes.addFlashAttribute(
                    "error", ex.getMessage());
        }

        return "redirect:/";
    }

    // Account history
    @GetMapping("/accounts/{accountNumber}/history")
    public String history(
            @PathVariable String accountNumber,
            Model model) {

        Customer customer = bankService.getCustomer(accountNumber);

        model.addAttribute("customer", customer);

        model.addAttribute("transactions",
                bankService.getAccountHistory(accountNumber));

        model.addAttribute("totalSent",
                bankService.getTotalSent(accountNumber));

        return "history";
    }

    // Deposit page
    @GetMapping("/deposit")
    public String depositPage(Model model) {

        model.addAttribute("customers",
                bankService.getAllCustomers());

        return "deposit";
    }

    // Deposit money
    @PostMapping("/deposit")
    public String deposit(
            @RequestParam String accountNumber,
            @RequestParam BigDecimal amount,
            RedirectAttributes redirectAttributes) {

        try {
            bankService.deposit(accountNumber, amount);

            redirectAttributes.addFlashAttribute(
                    "success", "Deposit completed successfully!");

        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute(
                    "error", ex.getMessage());
        }

        return "redirect:/deposit";
    }

    // Withdraw page
    @GetMapping("/withdraw")
    public String withdrawPage(Model model) {

        model.addAttribute("customers",
                bankService.getAllCustomers());

        return "withdraw";
    }

    // Withdraw money
    @PostMapping("/withdraw")
    public String withdraw(
            @RequestParam String accountNumber,
            @RequestParam BigDecimal amount,
            RedirectAttributes redirectAttributes) {

        try {
            bankService.withdraw(accountNumber, amount);

            redirectAttributes.addFlashAttribute(
                    "success", "Withdrawal completed successfully!");

        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute(
                    "error", ex.getMessage());
        }

        return "redirect:/withdraw";
    }

    // Large transfers page
    @GetMapping("/large-transfers")
    public String largeTransfers(
            @RequestParam(defaultValue = "10000") BigDecimal min,
            Model model) {

        model.addAttribute("transfers",
                bankService.getLargeTransfers(min));

        model.addAttribute("min", min);

        return "large-transfers";
    }
}