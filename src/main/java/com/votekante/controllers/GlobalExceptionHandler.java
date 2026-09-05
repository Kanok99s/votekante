package com.votekante.controllers;

import com.votekante.services.VoteException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Renders domain errors (and anything unexpected) as a friendly page
 * instead of a raw stack trace.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(VoteException.class)
    public String voteException(VoteException e, Model model) {
        model.addAttribute("message", e.getMessage());
        return "error";
    }

    @ExceptionHandler(Exception.class)
    public String unexpected(Exception e, Model model) {
        model.addAttribute("message", "Something went wrong. Please try again.");
        return "error";
    }
}
