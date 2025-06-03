package com.example.socialapp;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.BatchType;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.github.javafaker.Faker;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class Seeder {
    private static final int NUM_USERS = 100;
    private static final int NUM_POSTS = 200;
    private static final int NUM_COMMENTS = 1000;
    private static final int NUM_LIKES = 2000;
    private static final String KEYSPACE = "social_media";
    
    private final CqlSession session;
    private final Faker faker = new Faker();
    private final List<UUID> userIds = new ArrayList<>();
    private final List<UUID> postIds = new ArrayList<>();
    private final Map<UUID, String> userIdToUsername = new HashMap<>();
    private final Map<UUID, String> userIdToProfilePic = new HashMap<>();
    
    public Seeder() {
        this.session = CqlSession.builder()
                .addContactPoint(new InetSocketAddress("localhost", 9042))
                .withLocalDatacenter("datacenter1")
                .withKeyspace(KEYSPACE)
                .build();
        
        System.out.println("Connected to Cassandra cluster");
    }
    
    public void seedData() {
        System.out.println("Starting data seeding");
        seedUsers();
        seedPosts();
        seedComments();
        seedLikes();
        updateCounters();
        
        System.out.println("Data seeding completed!");
    }
    
    private void seedUsers() {
        PreparedStatement preparedStatement = session.prepare(
                "INSERT INTO users (user_id, username, full_name, profile_picture_url, created_at, is_active) " +
                "VALUES (?, ?, ?, ?, ?, ?)");
        
        AtomicInteger counter = new AtomicInteger(0);
        
        IntStream.range(0, NUM_USERS).forEach(i -> {
            UUID userId = UUID.randomUUID();
            userIds.add(userId);
            
            String username = faker.name().username() + faker.number().numberBetween(100, 999);
            String fullName = faker.name().fullName();
            String profilePic = "https://picsum.photos/200/200?random=" + i;
            Instant createdAt = Instant.now().minusSeconds(faker.number().numberBetween(86400, 2592000));
            
            userIdToUsername.put(userId, username);
            userIdToProfilePic.put(userId, profilePic);
            
            session.execute(preparedStatement.bind()
                    .setUuid(0, userId)
                    .setString(1, username)
                    .setString(2, fullName)
                    .setString(3, profilePic)
                    .setInstant(4, createdAt)
                    .setBoolean(5, true));
            
            int current = counter.incrementAndGet();
            if (current % 10 == 0) {
                System.out.println("Seeded " + current + " users");
            }
        });
        
        System.out.println("Seeded " + NUM_USERS + " users");
    }
    
    private void seedPosts() {
        System.out.println("Seeding posts...");
        PreparedStatement insertPostStmt = session.prepare(
            "INSERT INTO posts (post_id, user_id, content, created_at, is_deleted) VALUES (?, ?, ?, ?, ?)");
        PreparedStatement updatePostMetricsStmt = session.prepare(
            "UPDATE post_metrics SET comment_count = comment_count + ?, like_count = like_count + ? WHERE post_id = ?");

        for (int i = 0; i < 1000; i++) {
            UUID postId = UUID.randomUUID();
            UUID userId = userIds.get(faker.random().nextInt(userIds.size()));
            String content = faker.lorem().paragraph(faker.random().nextInt(1, 5));
            Instant createdAt = Instant.now().minusSeconds(faker.number().numberBetween(300, 2592000));
            boolean isDeleted = false;

            session.execute(insertPostStmt.bind(
                postId,
                userId,
                content,
                createdAt,
                isDeleted
            ));

            session.execute(updatePostMetricsStmt.bind(0L, 0L, postId));

            postIds.add(postId);
        }
        System.out.println("Seeded " + postIds.size() + " posts");
    }
    
    private void seedComments() {
        PreparedStatement commentsByPostStmt = session.prepare(
                "INSERT INTO comments_by_post (post_id, comment_id, user_id, username, user_profile_pic, content, created_at, updated_at, is_deleted) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
        
        PreparedStatement commentsByUserStmt = session.prepare(
                "INSERT INTO comments_by_user (user_id, comment_id, post_id, content, created_at, is_deleted) " +
                "VALUES (?, ?, ?, ?, ?, ?)");
        
        AtomicInteger counter = new AtomicInteger(0);
        
        IntStream.range(0, NUM_COMMENTS).forEach(i -> {
            UUID commentId = UUID.randomUUID();
            UUID postId = postIds.get(faker.random().nextInt(postIds.size()));
            UUID userId = userIds.get(faker.random().nextInt(userIds.size()));
            
            String username = userIdToUsername.get(userId);
            String userProfilePic = userIdToProfilePic.get(userId);
            String content = faker.lorem().sentence(faker.random().nextInt(5, 20));
            Instant createdAt = Instant.now().minusSeconds(faker.number().numberBetween(60, 604800));
            Instant updatedAt = createdAt;
            
            if (faker.random().nextInt(100) < 5) {
                updatedAt = createdAt.plusSeconds(faker.number().numberBetween(30, 3600));
            }
            
            BatchStatement batch = BatchStatement.builder(BatchType.LOGGED)
                    .addStatement(commentsByPostStmt.bind()
                            .setUuid(0, postId)
                            .setUuid(1, commentId)
                            .setUuid(2, userId)
                            .setString(3, username)
                            .setString(4, userProfilePic)
                            .setString(5, content)
                            .setInstant(6, createdAt)
                            .setInstant(7, updatedAt)
                            .setBoolean(8, false))
                    .addStatement(commentsByUserStmt.bind()
                            .setUuid(0, userId)
                            .setUuid(1, commentId)
                            .setUuid(2, postId)
                            .setString(3, content)
                            .setInstant(4, createdAt)
                            .setBoolean(5, false))
                    .build();
            
            session.execute(batch);
            
            int current = counter.incrementAndGet();
            if (current % 100 == 0) {
                System.out.println("Seeded " + current + " comments");
            }
        });
        
        System.out.println("Seeded " + NUM_COMMENTS + " comments");
    }
    
    private void seedLikes() {
        PreparedStatement postLikesStmt = session.prepare(
                "INSERT INTO post_likes (post_id, user_id, username, created_at) VALUES (?, ?, ?, ?)");
        
        PreparedStatement postLikesByUserStmt = session.prepare(
                "INSERT INTO post_likes_by_user (user_id, post_id, created_at) VALUES (?, ?, ?)");
        
        AtomicInteger counter = new AtomicInteger(0);
        List<String> alreadyLiked = new ArrayList<>();
        
        IntStream.range(0, NUM_LIKES).forEach(i -> {
            UUID postId = postIds.get(faker.random().nextInt(postIds.size()));
            UUID userId = userIds.get(faker.random().nextInt(userIds.size()));
            
            String likeKey = userId.toString() + "-" + postId.toString();
            if (alreadyLiked.contains(likeKey)) {
                return;
            }
            alreadyLiked.add(likeKey);
            
            String username = userIdToUsername.get(userId);
            Instant createdAt = Instant.now().minusSeconds(faker.number().numberBetween(30, 432000));
            
            BatchStatement batch = BatchStatement.builder(BatchType.LOGGED)
                    .addStatement(postLikesStmt.bind()
                            .setUuid(0, postId)
                            .setUuid(1, userId)
                            .setString(2, username)
                            .setInstant(3, createdAt))
                    .addStatement(postLikesByUserStmt.bind()
                            .setUuid(0, userId)
                            .setUuid(1, postId)
                            .setInstant(2, createdAt))
                    .build();
            
            session.execute(batch);
            
            int current = counter.incrementAndGet();
            if (current % 200 == 0) {
                System.out.println("Seeded " + current + " likes");
            }
        });
        
        System.out.println("Seeded " + counter.get() + " likes");
    }
    
    private void updateCounters() {
        System.out.println("Updating post counters...");
        
        Map<UUID, Integer> commentCounts = new HashMap<>();
        session.execute("SELECT post_id, COUNT(*) as comment_count FROM comments_by_post GROUP BY post_id ALLOW FILTERING")
                .forEach(row -> {
                    UUID postId = row.getUuid("post_id");
                    Long count = row.getLong("comment_count");
                    commentCounts.put(postId, count.intValue());
                });
        
        Map<UUID, Integer> likeCounts = new HashMap<>();
        session.execute("SELECT post_id, COUNT(*) as like_count FROM post_likes GROUP BY post_id ALLOW FILTERING")
                .forEach(row -> {
                    UUID postId = row.getUuid("post_id");
                    Long count = row.getLong("like_count");
                    likeCounts.put(postId, count.intValue());
                });
        
        // Update counters in post_metrics table
        PreparedStatement updateCommentCount = session.prepare(
                "UPDATE post_metrics SET comment_count = comment_count + ? WHERE post_id = ?");
        PreparedStatement updateLikeCount = session.prepare(
                "UPDATE post_metrics SET like_count = like_count + ? WHERE post_id = ?");
        
        commentCounts.forEach((postId, count) -> {
            session.execute(updateCommentCount.bind(count.longValue(), postId));
        });
        
        likeCounts.forEach((postId, count) -> {
            session.execute(updateLikeCount.bind(count.longValue(), postId));
        });
        
        System.out.println("Updated counters for " + commentCounts.size() + " posts with comments and " + 
                          likeCounts.size() + " posts with likes");
    }
    
    public void close() {
        if (session != null) {
            session.close();
            System.out.println("Cassandra session closed");
        }
    }
    
    public static void main(String[] args) {
        Seeder seeder = new Seeder();
        try {
            seeder.seedData();
        } finally {
            seeder.close();
        }
    }
}