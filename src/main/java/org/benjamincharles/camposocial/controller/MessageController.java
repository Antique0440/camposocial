package org.benjamincharles.camposocial.controller;

import org.benjamincharles.camposocial.entity.Conversation;
import org.benjamincharles.camposocial.service.MessageService;
import org.benjamincharles.camposocial.service.ThreadView;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/messages")
    public String inbox(Authentication authentication, Model model) {
        model.addAttribute("username", authentication.getName());
        model.addAttribute("inbox", messageService.getInbox(authentication.getName()));
        return "messages";
    }

    @PostMapping("/messages/new")
    public String startConversation(
            @RequestParam String username,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Conversation conversation = messageService.startConversation(authentication.getName(), username);
            return "redirect:/messages/" + conversation.getId();
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/messages";
        }
    }

    @GetMapping("/messages/{id}")
    public String conversation(@PathVariable Long id, Authentication authentication, Model model) {
        ThreadView thread = messageService.openConversation(id, authentication.getName());
        model.addAttribute("username", authentication.getName());
        model.addAttribute("thread", thread);
        model.addAttribute("maxMessageLength", MessageService.MAX_MESSAGE_LENGTH);
        // Opening the thread marked its messages as seen, so refresh the navbar badge computed before this handler ran.
        model.addAttribute("unreadMessages", messageService.unreadCount(authentication.getName()));
        return "conversation";
    }

    @PostMapping("/messages/{id}")
    public String send(
            @PathVariable Long id,
            @RequestParam String body,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            messageService.send(authentication.getName(), id, body);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/messages/" + id;
    }
}

