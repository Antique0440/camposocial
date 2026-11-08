package org.benjamincharles.camposocial.controller;

import org.benjamincharles.camposocial.entity.PostCategory;
import org.benjamincharles.camposocial.service.PostService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class FeedController {

    static final String CAMPUS_ADMIN = "ROLE_CAMPUS_ADMIN";

    private final PostService postService;

    public FeedController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping({"/", "/feed"})
    public String feed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "recent") String sort,
            @RequestParam(required = false) String category,
            Authentication authentication,
            Model model
    ) {
        String username = authentication.getName();
        boolean admin = isCampusAdmin(authentication);
        boolean top = "top".equals(sort);
        PostCategory selected = PostCategory.parse(category);
        String categoryParam = selected == null ? "" : selected.name();
        String sortParam = top ? "top" : "recent";

        model.addAttribute("username", username);
        model.addAttribute("feed", postService.getFeed(page, top, selected, username, admin));
        model.addAttribute("trending", postService.getTrending(5, username, admin));
        model.addAttribute("categories", PostCategory.values());
        model.addAttribute("selectedCategory", selected);
        model.addAttribute("categoryParam", categoryParam);
        model.addAttribute("sort", sortParam);
        model.addAttribute("maxPostLength", PostService.MAX_POST_LENGTH);
        model.addAttribute("maxCommentLength", PostService.MAX_COMMENT_LENGTH);
        model.addAttribute("returnTo", "/feed?page=" + Math.max(page, 0) + "&sort=" + sortParam
                + "&category=" + categoryParam);
        return "feed";
    }

    @PostMapping("/posts")
    public String createPost(
            @RequestParam String content,
            @RequestParam String category,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            postService.createPost(authentication.getName(), content, PostCategory.parse(category));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/feed";
    }

    @PostMapping("/posts/{id}/agree")
    public String agree(
            @PathVariable Long id,
            @RequestParam(required = false) String returnTo,
            Authentication authentication
    ) {
        postService.toggleAgree(authentication.getName(), id);
        return Redirects.to(returnTo, id);
    }

    @PostMapping("/posts/{id}/comments")
    public String comment(
            @PathVariable Long id,
            @RequestParam String body,
            @RequestParam(required = false) String returnTo,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            postService.addComment(authentication.getName(), id, body);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return Redirects.to(returnTo, id);
    }

    @PostMapping("/posts/{id}/pin")
    public String pin(
            @PathVariable Long id,
            @RequestParam(required = false) String returnTo,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            postService.togglePin(authentication.getName(), id, isCampusAdmin(authentication));
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return Redirects.to(returnTo, id);
    }

    @PostMapping("/posts/{id}/delete")
    public String delete(
            @PathVariable Long id,
            @RequestParam(required = false) String returnTo,
            Authentication authentication
    ) {
        postService.deletePost(authentication.getName(), id, isCampusAdmin(authentication));
        return Redirects.to(returnTo, null);
    }

    static boolean isCampusAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> CAMPUS_ADMIN.equals(authority.getAuthority()));
    }
}
