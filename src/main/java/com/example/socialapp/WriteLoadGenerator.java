package com.example.socialapp;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;
import java.util.Random;
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
    
    private PreparedStatement insertCommentStmt;
    private PreparedStatement insertLikeStmt;
    private PreparedStatement insertLikeByUserStmt;
    
    private UUID[] userIds;
    private UUID[] postIds;
    
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
        insertCommentStmt = session.prepare(
                "INSERT INTO comments (comment_id, post_id, user_id, content, created_at, updated_at, is_deleted) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)");
        
        insertLikeStmt = session.prepare(
                "INSERT INTO post_likes (post_id, user_id, created_at) VALUES (?, ?, ?)");
        
        insertLikeByUserStmt = session.prepare(
                "INSERT INTO likes_by_user (user_id, post_id, created_at) VALUES (?, ?, ?)");
        
        System.out.println("Prepared statements");
    }
    
    private void loadUserAndPostIds() {
        ResultSet userResults = session.execute("SELECT user_id FROM users LIMIT 1000");
        userIds = userResults.all().stream()
                .map(row -> row.getUuid("user_id"))
                .toArray(UUID[]::new);
        
        ResultSet postResults = session.execute("SELECT post_id FROM posts LIMIT 1000");
        postIds = postResults.all().stream()
                .map(row -> row.getUuid("post_id"))
                .toArray(UUID[]::new);
        
        if (userIds.length == 0 || postIds.length == 0) {
            throw new RuntimeException("No users or posts found in database. Run the seeder first.");
        }
        
        System.out.println("Loaded " + userIds.length + " users and " + postIds.length + " posts");
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
            UUID userId = userIds[random.nextInt(userIds.length)];
            
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
            
            session.executeAsync(insertCommentStmt.bind()
                    .setUuid(0, commentId)
                    .setUuid(1, postId)
                    .setUuid(2, userId)
                    .setString(3, content)
                    .setInstant(4, createdAt)
                    .setInstant(5, createdAt)
                    .setBoolean(6, false));
            
            commentCounter.incrementAndGet();
        } catch (Exception e) {
            System.err.println("Error generating comment: " + e.getMessage());
        }
    }
    
    private void generateLike() {
        try {
            UUID postId = postIds[random.nextInt(postIds.length)];
            UUID userId = userIds[random.nextInt(userIds.length)];
            Instant createdAt = Instant.now();
            
            session.executeAsync(insertLikeStmt.bind()
                    .setUuid(0, postId)
                    .setUuid(1, userId)
                    .setInstant(2, createdAt));
            
            session.executeAsync(insertLikeByUserStmt.bind()
                    .setUuid(0, userId)
                    .setUuid(1, postId)
                    .setInstant(2, createdAt));
            
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