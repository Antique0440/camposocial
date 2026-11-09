package org.benjamincharles.camposocial.controller;

import org.benjamincharles.camposocial.service.MessageService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Adds data shown in the navbar on every page. */
@ControllerAdvice
class NavigationModelAdvice {

    private final MessageService messageService;

    NavigationModelAdvice(MessageService messageService) {
        this.messageService = messageService;
    }

    /** Number of unread direct messages for the logged-in student, or 0 for anonymous visitors. */
    @ModelAttribute("unreadMessages")
    long unreadMessages() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return 0;
        }
        return messageService.unreadCount(authentication.getName());
    }
}

