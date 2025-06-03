package com.example.socialapp;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.BatchType;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Instant;

/**
 * A high-volume write load generator for Cassandra focused on comments and likes
 */
public class WriteLoadGenerator {
    private static final String KEYSPACE = "social_media";
    private static final int COMMENTS_PER_SECOND = 20;
    private static final int LIKES_PER_SECOND = 50;
    
    private final CqlSession session;
    private final Random random = new Random();
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private final AtomicInteger commentCounter = new AtomicInteger(0);
    private final AtomicInteger likeCounter = new AtomicInteger(0);
    
    private PreparedStatement insertCommentByPostStmt;
    private PreparedStatement insertCommentByUserStmt;
    private PreparedStatement insertLikeStmt;
    private PreparedStatement insertLikeByUserStmt;
    
    private List<UUID> userIds = new ArrayList<>();
    private UUID[] postIds;
    
    private Map<UUID, String> userIdToUsername = new HashMap<>();
    private Map<UUID, String> userIdToProfilePic = new HashMap<>();
    
    public WriteLoadGenerator() {
        this.session = CqlSession.builder()
                .addContactPoint(new InetSocketAddress("localhost", 9042))
                .withLocalDatacenter("datacenter1")
                .withKeyspace(KEYSPACE)
                .build();
        
        System.out.println("Connected to Cassandra cluster");
        
        prepareStatements();
        
        loadUserAndPostIds();
    }
    
    private void prepareStatements() {
        insertCommentByPostStmt = session.prepare(
                "INSERT INTO comments_by_post (post_id, comment_id, user_id, username, user_profile_pic, content, created_at, updated_at, is_deleted) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
        
        insertCommentByUserStmt = session.prepare(
                "INSERT INTO comments_by_user (user_id, comment_id, post_id, content, created_at, is_deleted) " +
                "VALUES (?, ?, ?, ?, ?, ?)");
        
        insertLikeStmt = session.prepare(
                "INSERT INTO post_likes (post_id, user_id, username, created_at) VALUES (?, ?, ?, ?)");
        
        insertLikeByUserStmt = session.prepare(
                "INSERT INTO post_likes_by_user (user_id, post_id, created_at) VALUES (?, ?, ?)");
        
        System.out.println("Prepared statements");
    }
    
    private void loadUserAndPostIds() {
        ResultSet userResults = session.execute("SELECT user_id, username, profile_picture_url FROM users LIMIT 1000");
        userIds = new ArrayList<>();
        for (Row row : userResults) {
            UUID userId = row.getUuid("user_id");
            userIds.add(userId);
            userIdToUsername.put(userId, row.getString("username"));
            userIdToProfilePic.put(userId, row.getString("profile_picture_url"));
        }
        
        ResultSet postResults = session.execute("SELECT post_id FROM posts LIMIT 1000");
        postIds = postResults.all().stream()
                .map(row -> row.getUuid("post_id"))
                .toArray(UUID[]::new);
        
        if (userIds.isEmpty() || postIds.length == 0) {
            throw new RuntimeException("No users or posts found in database. Run the seeder first.");
        }
        
        System.out.println("Loaded " + userIds.size() + " users and " + postIds.length + " posts");
    }
    
    public void startLoadGeneration(Duration duration) {
        System.out.println("Starting write load generation for " + duration.getSeconds() + " seconds");
        
        executor.scheduleAtFixedRate(
                this::generateComment,
                0,
                1000 / COMMENTS_PER_SECOND,
                TimeUnit.MILLISECONDS);
        
        executor.scheduleAtFixedRate(
                this::generateLike,
                0,
                1000 / LIKES_PER_SECOND,
                TimeUnit.MILLISECONDS);
        
        executor.schedule(this::stopLoadGeneration, duration.getSeconds(), TimeUnit.SECONDS);
        
        executor.scheduleAtFixedRate(
                this::reportStats,
                1,
                5,
                TimeUnit.SECONDS);
    }
    
    private void generateComment() {
        try {
            UUID commentId = UUID.randomUUID();
            UUID postId = postIds[random.nextInt(postIds.length)];
            UUID userId = userIds.get(random.nextInt(userIds.size()));
            
            String username = userIdToUsername.get(userId);
            String userProfilePic = userIdToProfilePic.get(userId);
            
            String[] commentTemplates = {
                "Great post!",
                "I agree with this",
                "Interesting perspective",
                "Thanks for sharing",
                "I'm not sure I agree",
                "This changed my perspective",
                "Looking forward to more content like this",
                "Have you considered the alternative view?",
                "This reminds me of something I read recently",
                "I had a similar experience"
            };
            
            String content = commentTemplates[random.nextInt(commentTemplates.length)];
            Instant createdAt = Instant.now();
            
            BatchStatement batch = BatchStatement.builder(BatchType.LOGGED)
                .addStatement(insertCommentByPostStmt.bind()
                    .setUuid(0, postId)
                    .setUuid(1, commentId)
                    .setUuid(2, userId)
                    .setString(3, username)
                    .setString(4, userProfilePic)
                    .setString(5, content)
                    .setInstant(6, createdAt)
                    .setInstant(7, createdAt)
                    .setBoolean(8, false))
                .addStatement(insertCommentByUserStmt.bind()
                    .setUuid(0, userId)
                    .setUuid(1, commentId)
                    .setUuid(2, postId)
                    .setString(3, content)
                    .setInstant(4, createdAt)
                    .setBoolean(5, false))
                .build();
            
            session.executeAsync(batch);
            session.executeAsync("UPDATE post_metrics SET comment_count = comment_count + 1 WHERE post_id = ?", postId);
            
            commentCounter.incrementAndGet();
        } catch (Exception e) {
            System.err.println("Error generating comment: " + e.getMessage());
        }
    }
    
    private void generateLike() {
        try {
            UUID postId = postIds[random.nextInt(postIds.length)];
            UUID userId = userIds.get(random.nextInt(userIds.size()));
            String username = userIdToUsername.get(userId);
            Instant createdAt = Instant.now();
            
            BatchStatement batch = BatchStatement.builder(BatchType.LOGGED)
                .addStatement(insertLikeStmt.bind()
                    .setUuid(0, postId)
                    .setUuid(1, userId)
                    .setString(2, username)
                    .setInstant(3, createdAt))
                .addStatement(insertLikeByUserStmt.bind()
                    .setUuid(0, userId)
                    .setUuid(1, postId)
                    .setInstant(2, createdAt))
                .build();
            
            session.executeAsync(batch);
            session.executeAsync("UPDATE post_metrics SET like_count = like_count + 1 WHERE post_id = ?", postId);
            
            likeCounter.incrementAndGet();
        } catch (Exception e) {
            System.err.println("Error generating like: " + e.getMessage());
        }
    }
    
    private void reportStats() {
        System.out.println("Total comments: " + commentCounter.get() + 
                ", Total likes: " + likeCounter.get() +
                " (current rates: ~" + COMMENTS_PER_SECOND + " comments/sec, ~" + 
                LIKES_PER_SECOND + " likes/sec)");
    }
    
    private void stopLoadGeneration() {
        System.out.println("Stopping load generation");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        System.out.println("Final stats - Total comments: " + commentCounter.get() + 
                ", Total likes: " + likeCounter.get());
        close();
    }
    
    public void close() {
        if (session != null && !session.isClosed()) {
            session.close();
            System.out.println("Cassandra session closed");
        }
    }
    
    public static void main(String[] args) {
        WriteLoadGenerator generator = new WriteLoadGenerator();
        Duration runDuration = Duration.ofMinutes(5);
        
        if (args.length > 0) {
            try {
                runDuration = Duration.ofSeconds(Integer.parseInt(args[0]));
            } catch (NumberFormatException e) {
                System.err.println("Invalid duration specified, using default of 5 minutes");
            }
        }
        
        generator.startLoadGeneration(runDuration);
    }
}