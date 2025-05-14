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
import java.util.List;
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
    
    public Seeder() {
        this.session = CqlSession.builder()
                .addContactPoint(new InetSocketAddress("localhost", 9042))
                .withLocalDatacenter("datacenter1")
                .withKeyspace(KEYSPACE)
                .build();
        
        System.out.println("Connected to Cassandra cluster");
    }
    
    public void seedData() {
        createKeyspaceIfNotExists();
        createTables();
        seedUsers();
        seedPosts();
        seedComments();
        seedLikes();
        
        System.out.println("Data seeding completed!");
    }
    
    private void createKeyspaceIfNotExists() {
        String query = "CREATE KEYSPACE IF NOT EXISTS " + KEYSPACE + 
                " WITH REPLICATION = {'class': 'NetworkTopologyStrategy', 'datacenter1': 3}";
        session.execute(query);
        System.out.println("Keyspace created or verified");
    }
    
    private void createTables() {
        session.execute(
                "CREATE TABLE IF NOT EXISTS users (" +
                " user_id UUID PRIMARY KEY," +
                " username TEXT," +
                " email TEXT," +
                " full_name TEXT," +
                " bio TEXT," +
                " profile_picture_url TEXT," +
                " created_at TIMESTAMP," +
                " updated_at TIMESTAMP," +
                " is_active BOOLEAN" +
                ")");
        
        session.execute(
                "CREATE TABLE IF NOT EXISTS posts (" +
                " post_id UUID," +
                " user_id UUID," +
                " content TEXT," +
                " media_urls LIST<TEXT>," +
                " created_at TIMESTAMP," +
                " updated_at TIMESTAMP," +
                " is_deleted BOOLEAN," +
                " PRIMARY KEY (post_id)" +
                ")");
        
        session.execute(
                "CREATE TABLE IF NOT EXISTS comments (" +
                " comment_id UUID," +
                " post_id UUID," +
                " user_id UUID," +
                " content TEXT," +
                " created_at TIMESTAMP," +
                " updated_at TIMESTAMP," +
                " is_deleted BOOLEAN," +
                " PRIMARY KEY ((post_id), created_at, comment_id)" +
                ") WITH CLUSTERING ORDER BY (created_at DESC, comment_id ASC)");
        
        session.execute(
                "CREATE TABLE IF NOT EXISTS post_likes (" +
                " post_id UUID," +
                " user_id UUID," +
                " created_at TIMESTAMP," +
                " PRIMARY KEY ((post_id), user_id)" +
                ")");
        
        session.execute(
                "CREATE TABLE IF NOT EXISTS likes_by_user (" +
                " user_id UUID," +
                " post_id UUID," +
                " created_at TIMESTAMP," +
                " PRIMARY KEY ((user_id), post_id)" +
                ")");
        
        System.out.println("Tables created or verified");
    }
    
    private void seedUsers() {
        PreparedStatement preparedStatement = session.prepare(
                "INSERT INTO users (user_id, username, email, full_name, bio, created_at, updated_at, is_active) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
        
        AtomicInteger counter = new AtomicInteger(0);
        
        IntStream.range(0, NUM_USERS).forEach(i -> {
            UUID userId = UUID.randomUUID();
            userIds.add(userId);
            
            String username = faker.name().username();
            String email = username + "@example.com";
            String fullName = faker.name().fullName();
            String bio = faker.lorem().paragraph(1);
            Instant createdAt = Instant.now().minusSeconds(faker.number().numberBetween(86400, 2592000));
            Instant updatedAt = createdAt.plusSeconds(faker.number().numberBetween(0, 86400));
            
            session.execute(preparedStatement.bind()
                    .setUuid(0, userId)
                    .setString(1, username)
                    .setString(2, email)
                    .setString(3, fullName)
                    .setString(4, bio)
                    .setInstant(5, createdAt)
                    .setInstant(6, updatedAt)
                    .setBoolean(7, true));
            
            int current = counter.incrementAndGet();
            if (current % 10 == 0) {
                System.out.println("Seeded " + current + " users");
            }
        });
        
        System.out.println("Seeded " + NUM_USERS + " users");
    }
    
    private void seedPosts() {
        PreparedStatement preparedStatement = session.prepare(
                "INSERT INTO posts (post_id, user_id, content, media_urls, created_at, updated_at, is_deleted) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)");
        
        AtomicInteger counter = new AtomicInteger(0);
        
        IntStream.range(0, NUM_POSTS).forEach(i -> {
            UUID postId = UUID.randomUUID();
            postIds.add(postId);
            
            UUID userId = userIds.get(faker.random().nextInt(userIds.size()));
            String content = faker.lorem().paragraph(faker.random().nextInt(1, 3));
            ArrayList<String> mediaUrls = new ArrayList<>();
            // 30% chance to have media
            if (faker.random().nextInt(100) < 30) {
                int numMedia = faker.random().nextInt(1, 4);
                for (int j = 0; j < numMedia; j++) {
                    mediaUrls.add("https://example.com/media/" + UUID.randomUUID() + ".jpg");
                }
            }
            Instant createdAt = Instant.now().minusSeconds(faker.number().numberBetween(3600, 1209600)); 
            Instant updatedAt = createdAt;
            // 10% chance post was updated
            if (faker.random().nextInt(100) < 10) {
                updatedAt = createdAt.plusSeconds(faker.number().numberBetween(60, 86400)); 
            }
            
            session.execute(preparedStatement.bind()
                    .setUuid(0, postId)
                    .setUuid(1, userId)
                    .setString(2, content)
                    .setList(3, mediaUrls, String.class)
                    .setInstant(4, createdAt)
                    .setInstant(5, updatedAt)
                    .setBoolean(6, false));
            
            int current = counter.incrementAndGet();
            if (current % 20 == 0) {
                System.out.println("Seeded " + current + " posts");
            }
        });
        
        System.out.println("Seeded " + NUM_POSTS + " posts");
    }
    
    private void seedComments() {
        PreparedStatement preparedStatement = session.prepare(
                "INSERT INTO comments (comment_id, post_id, user_id, content, created_at, updated_at, is_deleted) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)");
        
        AtomicInteger counter = new AtomicInteger(0);
        
        IntStream.range(0, NUM_COMMENTS).forEach(i -> {
            UUID commentId = UUID.randomUUID();
            UUID postId = postIds.get(faker.random().nextInt(postIds.size()));
            UUID userId = userIds.get(faker.random().nextInt(userIds.size()));
            
            String content = faker.lorem().sentence(faker.random().nextInt(3, 15));
            Instant createdAt = Instant.now().minusSeconds(faker.number().numberBetween(60, 604800)); 
            Instant updatedAt = createdAt;
            // 5% chance comment was updated
            if (faker.random().nextInt(100) < 5) {
                updatedAt = createdAt.plusSeconds(faker.number().numberBetween(30, 3600)); 
            }
            
            session.execute(preparedStatement.bind()
                    .setUuid(0, commentId)
                    .setUuid(1, postId)
                    .setUuid(2, userId)
                    .setString(3, content)
                    .setInstant(4, createdAt)
                    .setInstant(5, updatedAt)
                    .setBoolean(6, false));
            
            int current = counter.incrementAndGet();
            if (current % 100 == 0) {
                System.out.println("Seeded " + current + " comments");
            }
        });
        
        System.out.println("Seeded " + NUM_COMMENTS + " comments");
    }
    
    private void seedLikes() {
        PreparedStatement likesStmt = session.prepare(
                "INSERT INTO post_likes (post_id, user_id, created_at) VALUES (?, ?, ?)");
        
        PreparedStatement likesByUserStmt = session.prepare(
                "INSERT INTO likes_by_user (user_id, post_id, created_at) VALUES (?, ?, ?)");
        
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
            
            Instant createdAt = Instant.now().minusSeconds(faker.number().numberBetween(30, 432000)); 
            
            BatchStatement batch = BatchStatement.builder(BatchType.LOGGED)
                    .addStatement(likesStmt.bind()
                            .setUuid(0, postId)
                            .setUuid(1, userId)
                            .setInstant(2, createdAt))
                    .addStatement(likesByUserStmt.bind()
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