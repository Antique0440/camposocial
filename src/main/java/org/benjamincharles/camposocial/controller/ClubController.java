package org.benjamincharles.camposocial.controller;

import org.benjamincharles.camposocial.entity.Club;
import org.benjamincharles.camposocial.entity.ClubCategory;
import org.benjamincharles.camposocial.entity.PostCategory;
import org.benjamincharles.camposocial.service.ClubService;
import org.benjamincharles.camposocial.service.ClubView;
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
public class ClubController {

    private final ClubService clubService;
    private final PostService postService;

    public ClubController(ClubService clubService, PostService postService) {
        this.clubService = clubService;
        this.postService = postService;
    }

    @GetMapping("/clubs")
    public String directory(
            @RequestParam(required = false) String category,
            Authentication authentication,
            Model model
    ) {
        ClubCategory selected = ClubCategory.parse(category);
        model.addAttribute("username", authentication.getName());
        model.addAttribute("clubs", clubService.getDirectory(selected, authentication.getName(),
                FeedController.isCampusAdmin(authentication)));
        model.addAttribute("categories", ClubCategory.values());
        model.addAttribute("selectedCategory", selected);
        model.addAttribute("maxNameLength", ClubService.MAX_NAME_LENGTH);
        model.addAttribute("maxDescriptionLength", ClubService.MAX_DESCRIPTION_LENGTH);
        model.addAttribute("returnTo", selected == null ? "/clubs" : "/clubs?category=" + selected.name());
        return "clubs";
    }

    @PostMapping("/clubs")
    public String createClub(
            @RequestParam String name,
            @RequestParam(defaultValue = "") String description,
            @RequestParam String category,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Club club = clubService.createClub(authentication.getName(), name, description,
                    ClubCategory.parse(category));
            return "redirect:/clubs/" + club.getId();
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/clubs";
        }
    }

    @GetMapping("/clubs/{id}")
    public String profile(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            Authentication authentication,
            Model model
    ) {
        String username = authentication.getName();
        boolean admin = FeedController.isCampusAdmin(authentication);
        ClubView view = clubService.getClub(id, username, admin);

        model.addAttribute("username", username);
        model.addAttribute("clubView", view);
        model.addAttribute("members", clubService.getMembers(id));
        model.addAttribute("feed", postService.getClubFeed(id, page, username, admin));
        model.addAttribute("categories", PostCategory.values());
        model.addAttribute("maxPostLength", PostService.MAX_POST_LENGTH);
        model.addAttribute("maxCommentLength", PostService.MAX_COMMENT_LENGTH);
        model.addAttribute("returnTo", "/clubs/" + id);
        return "club";
    }

    @PostMapping("/clubs/{id}/join")
    public String join(
            @PathVariable Long id,
            @RequestParam(required = false) String returnTo,
            Authentication authentication
    ) {
        clubService.join(authentication.getName(), id);
        return Redirects.to(returnTo == null ? "/clubs/" + id : returnTo, null);
    }

    @PostMapping("/clubs/{id}/leave")
    public String leave(
            @PathVariable Long id,
            @RequestParam(required = false) String returnTo,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            clubService.leave(authentication.getName(), id);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return Redirects.to(returnTo == null ? "/clubs/" + id : returnTo, null);
    }

    @PostMapping("/clubs/{id}/posts")
    public String createClubPost(
            @PathVariable Long id,
            @RequestParam String content,
            @RequestParam String category,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        try {
            postService.createPost(authentication.getName(), content, PostCategory.parse(category), id);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/clubs/" + id;
    }

    @PostMapping("/clubs/{id}/members/{membershipId}/promote")
    public String promote(
            @PathVariable Long id,
            @PathVariable Long membershipId,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        return manage(id, redirectAttributes, () -> clubService.promote(
                authentication.getName(), id, membershipId, FeedController.isCampusAdmin(authentication)));
    }

    @PostMapping("/clubs/{id}/members/{membershipId}/demote")
    public String demote(
            @PathVariable Long id,
            @PathVariable Long membershipId,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        return manage(id, redirectAttributes, () -> clubService.demote(
                authentication.getName(), id, membershipId, FeedController.isCampusAdmin(authentication)));
    }

    @PostMapping("/clubs/{id}/members/{membershipId}/remove")
    public String remove(
            @PathVariable Long id,
            @PathVariable Long membershipId,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        return manage(id, redirectAttributes, () -> clubService.removeMember(
                authentication.getName(), id, membershipId, FeedController.isCampusAdmin(authentication)));
    }

    /** Runs a member-management action, turning rule violations into a flash error on the club page. */
    private String manage(Long clubId, RedirectAttributes redirectAttributes, Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/clubs/" + clubId;
    }
}
