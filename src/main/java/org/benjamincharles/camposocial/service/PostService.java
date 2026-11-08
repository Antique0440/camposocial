package org.benjamincharles.camposocial.service;

import org.benjamincharles.camposocial.entity.Comment;
import org.benjamincharles.camposocial.entity.Post;
import org.benjamincharles.camposocial.entity.PostCategory;
import org.benjamincharles.camposocial.entity.Reaction;
import org.benjamincharles.camposocial.entity.User;
import org.benjamincharles.camposocial.repository.CommentRepository;
import org.benjamincharles.camposocial.repository.PostRepository;
import org.benjamincharles.camposocial.repository.ReactionRepository;
import org.benjamincharles.camposocial.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional
public class PostService {

    public static final int PAGE_SIZE = 10;
    public static final int MAX_POST_LENGTH = 500;
    public static final int MAX_COMMENT_LENGTH = 300;

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ReactionRepository reactionRepository;
    private final UserRepository userRepository;

    public PostService(
            PostRepository postRepository,
            CommentRepository commentRepository,
            ReactionRepository reactionRepository,
            UserRepository userRepository
    ) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.reactionRepository = reactionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<PostView> getFeed(int page, boolean byEngagement, PostCategory category,
                                  String username, boolean admin) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE);
        Page<Post> posts;
        if (byEngagement) {
            posts = category == null
                    ? postRepository.findAllByEngagement(pageable)
                    : postRepository.findByCategoryByEngagement(category, pageable);
        } else {
            posts = category == null
                    ? postRepository.findAllByOrderByCreatedAtDesc(pageable)
                    : postRepository.findByCategoryOrderByCreatedAtDesc(category, pageable);
        }
        return posts.map(post -> toView(post, username, admin));
    }

    /** Most-agreed posts, ignoring posts nobody has agreed with yet. */
    @Transactional(readOnly = true)
    public List<PostView> getTrending(int limit, String username, boolean admin) {
        return postRepository.findAllByEngagement(PageRequest.of(0, limit))
                .map(post -> toView(post, username, admin))
                .getContent().stream()
                .filter(view -> view.agreeCount() > 0)
                .toList();
    }

    public Post createPost(String username, String content, PostCategory category) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Post cannot be empty.");
        }
        if (text.length() > MAX_POST_LENGTH) {
            throw new IllegalArgumentException("Post cannot exceed " + MAX_POST_LENGTH + " characters.");
        }
        if (category == null) {
            throw new IllegalArgumentException("Please choose a valid category.");
        }
        return postRepository.save(new Post(findUser(username), text, category));
    }

    public Comment addComment(String username, Long postId, String body) {
        String text = body == null ? "" : body.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Comment cannot be empty.");
        }
        if (text.length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException("Comment cannot exceed " + MAX_COMMENT_LENGTH + " characters.");
        }
        return commentRepository.save(new Comment(findPost(postId), findUser(username), text));
    }

    /** Adds the user's "agree" to the post, or removes it if already given. */
    public void toggleAgree(String username, Long postId) {
        reactionRepository.findByPostIdAndUserUsername(postId, username).ifPresentOrElse(
                reactionRepository::delete,
                () -> reactionRepository.save(new Reaction(findPost(postId), findUser(username)))
        );
    }

    public void deletePost(String username, Long postId, boolean admin) {
        Post post = findPost(postId);
        if (!admin && !post.getAuthor().getUsername().equals(username)) {
            throw new AccessDeniedException("Only the author or a campus admin can delete this post.");
        }
        postRepository.delete(post);
    }

    private PostView toView(Post post, String username, boolean admin) {
        return new PostView(
                post,
                reactionRepository.countByPostId(post.getId()),
                reactionRepository.existsByPostIdAndUserUsername(post.getId(), username),
                admin || post.getAuthor().getUsername().equals(username),
                List.copyOf(post.getComments())
        );
    }

    private Post findPost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }
}

