package com.mehedi.waylo.service;

import com.mehedi.waylo.dto.CreatePostRequest;
import com.mehedi.waylo.dto.PostCard;
import com.mehedi.waylo.dto.UpdateProfileRequest;
import com.mehedi.waylo.entity.*;
import com.mehedi.waylo.enums.TripStatus;
import com.mehedi.waylo.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
public class SocialService {

    private final PostRepository posts;
    private final UserRepository users;
    private final DestinationRepository destinations;
    private final TripRepository trips;
    private final PostLikeRepository likes;
    private final PostSaveRepository saves;
    private final PostCommentRepository comments;
    private final UserFollowRepository follows;
    private final MediaStorageService media;

    public SocialService(PostRepository posts, UserRepository users,
                         DestinationRepository destinations, TripRepository trips,
                         PostLikeRepository likes, PostSaveRepository saves,
                         PostCommentRepository comments, UserFollowRepository follows,
                         MediaStorageService media) {
        this.posts = posts;
        this.users = users;
        this.destinations = destinations;
        this.trips = trips;
        this.likes = likes;
        this.saves = saves;
        this.comments = comments;
        this.follows = follows;
        this.media = media;
    }

    private User requiredUser(String email) {
        if (email == null || email.isBlank()) throw new AccessDeniedException("Login required.");
        return users.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .filter(User::isEnabled)
                .orElseThrow(() -> new AccessDeniedException("Account unavailable."));
    }

    private User lockedUser(String email) {
        User user = requiredUser(email);
        return users.lockById(user.getId()).filter(User::isEnabled)
                .orElseThrow(() -> new AccessDeniedException("Account unavailable."));
    }

    @Transactional(readOnly = true)
    public Post ownPost(Long id, String email) {
        Post post = requirePost(id);
        if (!post.getUser().getId().equals(requiredUser(email).getId()))
            throw new AccessDeniedException("Only the author can edit this story.");
        return post;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void editPost(Long id, String email, String caption, MultipartFile photo) {
        Post post = ownPost(id, email);
        if (caption == null || caption.isBlank() || caption.strip().length() > 2000)
            throw new IllegalArgumentException("Caption must be 1–2000 characters.");
        var uploaded = media.store(photo, MediaStorageService.PhotoKind.POST);
        String oldId = post.getImagePublicId(), oldUrl = post.getImageUrl();
        post.setCaption(caption.strip());
        if (uploaded != null) { post.setImageUrl(uploaded.url()); post.setImagePublicId(uploaded.publicId()); }
        posts.saveAndFlush(post);
        if (uploaded != null) media.deleteAfterCommit(oldId, oldUrl);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void changeComment(Long postId, Long commentId, String email, String body, boolean delete) {
        requirePost(postId);
        User user = requiredUser(email);
        PostComment comment = comments.findById(commentId).filter(c -> !c.isDeleted() && !c.isHidden())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!comment.getPost().getId().equals(postId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!comment.getUser().getId().equals(user.getId())) throw new AccessDeniedException("Only the comment author can change it.");
        if (delete) comment.setDeleted(true);
        else {
            if (body == null || body.isBlank() || body.strip().length() > 1000)
                throw new IllegalArgumentException("Comment must be 1–1000 characters.");
            comment.setBody(body.strip());
        }
    }

    private Long optionalViewerId(String email) {
        if (email == null || email.isBlank()) return null;
        return users.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .filter(User::isEnabled).map(User::getId).orElse(null);
    }

    private Post requirePost(Long id) {
        return posts.findById(id).filter(p -> !p.isHidden() && !p.isDeleted()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found."));
    }

    private PostCard card(Post post, Long viewerId) {
        long likesCount = likes.countByPostId(post.getId());
        long commentsCount = comments.countByPostIdAndHiddenFalseAndDeletedFalse(post.getId());
        boolean liked = viewerId != null && likes.existsByPostIdAndUserId(post.getId(), viewerId);
        boolean saved = viewerId != null && saves.existsByPostIdAndUserId(post.getId(), viewerId);
        return new PostCard(post, likesCount, commentsCount, liked, saved);
    }

    @Transactional(readOnly = true)
    public Page<PostCard> feed(int page, String viewerEmail) {
        Long viewerId = optionalViewerId(viewerEmail);
        return posts.findByHiddenFalseAndDeletedFalseOrderByCreatedAtDescIdDesc(
                PageRequest.of(Math.max(0, Math.min(page, 10000)), 12))
                .map(post -> card(post, viewerId));
    }

    @Transactional(readOnly = true)
    public Page<PostCard> userPosts(Long userId, int page, String viewerEmail) {
        Long viewerId = optionalViewerId(viewerEmail);
        return posts.findByUserIdAndHiddenFalseAndDeletedFalseOrderByCreatedAtDescIdDesc(
                userId, PageRequest.of(Math.max(0, Math.min(page, 10000)), 12))
                .map(post -> card(post, viewerId));
    }

    @Transactional(readOnly = true)
    public Page<PostCard> savedPosts(String email, int page) {
        Long viewerId = requiredUser(email).getId();
        return saves.findByUserIdAndPostHiddenFalseAndPostDeletedFalseOrderByIdDesc(viewerId,
                        PageRequest.of(Math.max(0, Math.min(page, 10000)), 12))
                .map(save -> card(save.getPost(), viewerId));
    }

    @Transactional(readOnly = true)
    public PostCard post(Long id, String viewerEmail) {
        Post post = posts.findWithAuthorAndTagsById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found."));
        return card(post, optionalViewerId(viewerEmail));
    }

    @Transactional(readOnly = true)
    public Page<PostComment> comments(Long id, int page) {
        requirePost(id);
        return comments.findByPostIdAndHiddenFalseAndDeletedFalseOrderByCreatedAtAscIdAsc(id,
                PageRequest.of(Math.max(0, Math.min(page, 10000)), 30));
    }

    @Transactional(readOnly = true)
    public User profile(Long userId) {
        return users.findById(userId).filter(User::isEnabled).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found."));
    }

    @Transactional(readOnly = true)
    public User myProfile(String email) { return requiredUser(email); }

    @Transactional(readOnly = true)
    public long postCount(Long userId) { return posts.countByUserIdAndHiddenFalseAndDeletedFalse(userId); }

    @Transactional(readOnly = true)
    public long followerCount(Long userId) { return follows.countByFollowedId(userId); }

    @Transactional(readOnly = true)
    public long followingCount(Long userId) { return follows.countByFollowerId(userId); }

    @Transactional(readOnly = true)
    public boolean following(String email, Long profileId) {
        Long viewer = optionalViewerId(email);
        return viewer != null && follows.existsByFollowerIdAndFollowedId(viewer, profileId);
    }

    @Transactional(readOnly = true)
    public List<Destination> destinations() { return destinations.findAll(Sort.by("name")); }

    @Transactional(readOnly = true)
    public List<Trip> publishedTrips() { return trips.findByStatus(TripStatus.PUBLISHED); }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Post createPost(String email, CreatePostRequest request, MultipartFile photo) {
        User author = requiredUser(email);
        String caption = request.getCaption() == null ? "" : request.getCaption().trim();
        if (caption.isBlank() || caption.length() > 2000) {
            throw new IllegalArgumentException("Caption is required (up to 2000 characters).");
        }
        Post post = new Post();
        post.setUser(author);
        post.setCaption(caption);
        Trip selectedTrip = null;
        if (request.getTripId() != null) {
            selectedTrip = trips.findByIdAndStatus(request.getTripId(), TripStatus.PUBLISHED)
                    .orElseThrow(() -> new IllegalArgumentException("Choose a published trip."));
            post.setTrip(selectedTrip);
        }
        if (request.getDestinationId() != null) {
            Destination destination = destinations.findById(request.getDestinationId())
                    .orElseThrow(() -> new IllegalArgumentException("Destination not found."));
            if (selectedTrip != null && !selectedTrip.getDestination().getId().equals(destination.getId())) {
                throw new IllegalArgumentException("Destination and tagged trip must match.");
            }
            post.setDestination(destination);
        } else if (selectedTrip != null) {
            post.setDestination(selectedTrip.getDestination());
        }
        MediaStorageService.StoredPhoto uploaded = media.store(
                photo, MediaStorageService.PhotoKind.POST);
        if (uploaded != null) {
            post.setImageUrl(uploaded.url());
            post.setImagePublicId(uploaded.publicId());
        }
        // MediaStorageService removes this new asset automatically on DB rollback.
        return posts.saveAndFlush(post);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public boolean toggleLike(Long id, String email) {
        User user = lockedUser(email);
        Post post = requirePost(id);
        if (likes.existsByPostIdAndUserId(id, user.getId())) {
            likes.deleteByPostIdAndUserId(id, user.getId());
            return false;
        }
        PostLike like = new PostLike();
        like.setUser(user);
        like.setPost(post);
        likes.saveAndFlush(like);
        return true;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public boolean toggleSave(Long id, String email) {
        User user = lockedUser(email);
        Post post = requirePost(id);
        if (saves.existsByPostIdAndUserId(id, user.getId())) {
            saves.deleteByPostIdAndUserId(id, user.getId());
            return false;
        }
        PostSave save = new PostSave();
        save.setPost(post);
        save.setUser(user);
        saves.saveAndFlush(save);
        return true;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void comment(Long postId, String email, String text) {
        User author = requiredUser(email);
        String body = text == null ? "" : text.trim();
        if (body.isBlank() || body.length() > 1000) {
            throw new IllegalArgumentException("Comment must be between 1 and 1000 characters.");
        }
        PostComment comment = new PostComment();
        comment.setPost(requirePost(postId));
        comment.setUser(author);
        comment.setBody(body);
        comments.save(comment);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void deletePost(Long id, String email) {
        User user = requiredUser(email);
        Post post = requirePost(id);
        if (!post.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Only the author may delete this post.");
        }
        post.setDeleted(true);
        posts.saveAndFlush(post);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public boolean toggleFollow(Long userId, String email) {
        User viewer = lockedUser(email);
        User target = profile(userId);
        if (viewer.getId().equals(target.getId())) {
            throw new IllegalArgumentException("You cannot follow yourself.");
        }
        if (follows.existsByFollowerIdAndFollowedId(viewer.getId(), target.getId())) {
            follows.deleteByFollowerIdAndFollowedId(viewer.getId(), target.getId());
            return false;
        }
        UserFollow follow = new UserFollow();
        follow.setFollower(viewer);
        follow.setFollowed(target);
        follows.saveAndFlush(follow);
        return true;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void updateProfile(String email, UpdateProfileRequest request, MultipartFile avatar) {
        User user = requiredUser(email);
        String name = request.getFullName() == null ? "" : request.getFullName().trim();
        if (name.isBlank() || name.length() > 100) {
            throw new IllegalArgumentException("Enter your full name (up to 100 characters).");
        }
        if (request.getBio() != null && request.getBio().length() > 500) {
            throw new IllegalArgumentException("Bio must be 500 characters or fewer.");
        }
        if (request.getProfileLocation() != null && request.getProfileLocation().length() > 150) {
            throw new IllegalArgumentException("Location must be 150 characters or fewer.");
        }
        MediaStorageService.StoredPhoto uploaded = media.store(
                avatar, MediaStorageService.PhotoKind.PROFILE);
        String oldId = user.getAvatarPublicId();
        String oldUrl = user.getAvatarUrl();
        user.setFullName(name);
        user.setBio(request.getBio() == null ? null : request.getBio().trim());
        user.setProfileLocation(request.getProfileLocation() == null ? null : request.getProfileLocation().trim());
        if (uploaded != null) {
            user.setAvatarUrl(uploaded.url());
            user.setAvatarPublicId(uploaded.publicId());
        }
        users.saveAndFlush(user);
        if (uploaded != null) media.deleteAfterCommit(oldId, oldUrl);
    }
}
