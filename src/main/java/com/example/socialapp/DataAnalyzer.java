package com.example.socialapp;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A utility class to analyze comment and like patterns in the social media database
 */
public class DataAnalyzer {
    private static final String KEYSPACE = "social_media";
    private final CqlSession session;
    
    public DataAnalyzer() {
        this.session = CqlSession.builder()
                .addContactPoint(new InetSocketAddress("localhost", 9042))
                .withLocalDatacenter("datacenter1")
                .withKeyspace(KEYSPACE)
                .build();
        
        System.out.println("Connected to Cassandra cluster");
    }
    
    public void analyzeData() {
        System.out.println("\n*** SOCIAL MEDIA DATA ANALYSIS ***\n");
        
        countRecords();
        getMostLikedPosts();
        getMostActiveCommenters();
        getMostCommentedPosts();
        getCommentActivityByHour();
    }
    
    private void countRecords() {
        System.out.println("=== RECORD COUNTS ===");
        
        long userCount = session.execute("SELECT COUNT(*) FROM users").one().getLong(0);
        long postCount = session.execute("SELECT COUNT(*) FROM posts").one().getLong(0);
        
        long totalComments = session.execute("SELECT SUM(comment_count) FROM post_metrics").one().getLong(0);
        long totalLikes = session.execute("SELECT SUM(like_count) FROM post_metrics").one().getLong(0);
        
        System.out.println("Users: " + userCount);
        System.out.println("Posts: " + postCount);
        System.out.println("Comments: " + totalComments);
        System.out.println("Likes: " + totalLikes);
        System.out.println();
    }
    
    private void getMostLikedPosts() {
        System.out.println("=== MOST LIKED POSTS ===");
        
        List<Row> results = session.execute(
                "SELECT post_id, like_count FROM post_metrics LIMIT 10"
        ).all();
        
        Map<UUID, Integer> postLikes = new HashMap<>();
        for (Row row : results) {
            postLikes.put(row.getUuid("post_id"), (int)row.getLong("like_count"));
        }
        
        for (Map.Entry<UUID, Integer> entry : postLikes.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(5)
                .collect(Collectors.toList())) {
            
            UUID postId = entry.getKey();
            int likeCount = entry.getValue();
            
            Row postRow = session.execute(
                    "SELECT user_id, content FROM posts WHERE post_id = ?", 
                    postId
            ).one();
            
            if (postRow != null) {
                UUID userId = postRow.getUuid("user_id");
                String content = postRow.getString("content");
                
                Row userRow = session.execute(
                        "SELECT username FROM users WHERE user_id = ?", 
                        userId
                ).one();
                
                String username = userRow != null ? userRow.getString("username") : "Unknown";
                
                System.out.println("Post by " + username + " has " + likeCount + " likes");
                System.out.println("Content: " + truncate(content, 50));
                System.out.println();
            }
        }
    }
    
    private void getMostActiveCommenters() {
        System.out.println("=== MOST ACTIVE COMMENTERS ===");
        
        Map<UUID, Integer> commentsByUser = new HashMap<>();
        
        List<Row> comments = session.execute("SELECT user_id FROM comments_by_user LIMIT 1000").all();
        
        for (Row commentRow : comments) {
            UUID userId = commentRow.getUuid("user_id");
            commentsByUser.put(userId, commentsByUser.getOrDefault(userId, 0) + 1);
        }
        
        commentsByUser.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(5)
                .forEach(entry -> {
                    UUID userId = entry.getKey();
                    int commentCount = entry.getValue();
                    
                    Row userRow = session.execute(
                            "SELECT username FROM users WHERE user_id = ?", 
                            userId
                    ).one();
                    
                    String username = userRow != null ? userRow.getString("username") : "Unknown";
                    
                    System.out.println("User " + username + " made " + commentCount + " comments");
                });
        
        System.out.println();
    }
    
    private void getMostCommentedPosts() {
        System.out.println("=== MOST COMMENTED POSTS ===");
        
        Map<UUID, Long> commentsByPost = new HashMap<>();
        List<Row> posts = session.execute("SELECT post_id FROM posts LIMIT 1000").all();
        
        for (Row postRow : posts) {
            UUID postId = postRow.getUuid("post_id");
            
            Row metricsRow = session.execute(
                "SELECT comment_count FROM post_metrics WHERE post_id = ?", 
                postId
            ).one();
            
            if (metricsRow != null) {
                long commentCount = metricsRow.getLong("comment_count");
                commentsByPost.put(postId, commentCount);
            }
        }
        
        commentsByPost.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(5)
                .forEach(entry -> {
                    UUID postId = entry.getKey();
                    long commentCount = entry.getValue();
                    
                    Row postRow = session.execute(
                            "SELECT user_id, content FROM posts WHERE post_id = ?", 
                            postId
                    ).one();
                    
                    if (postRow != null) {
                        UUID userId = postRow.getUuid("user_id");
                        String content = postRow.getString("content");
                        
                        Row userRow = session.execute(
                                "SELECT username FROM users WHERE user_id = ?", 
                                userId
                        ).one();
                        
                        String username = userRow != null ? userRow.getString("username") : "Unknown";
                        
                        System.out.println("Post by " + username + " has " + commentCount + " comments");
                        System.out.println("Content: " + truncate(content, 50));
                        System.out.println();
                    }
                });
    }
    
    private void getCommentActivityByHour() {
        System.out.println("=== COMMENT ACTIVITY BY HOUR ===");
        
        Map<Integer, Integer> commentsByHour = new HashMap<>();
        for (int i = 0; i < 24; i++) {
            commentsByHour.put(i, 0);
        }
        
        List<Row> commentRows = session.execute("SELECT created_at FROM comments_by_user LIMIT 1000").all();
        
        for (Row commentRow : commentRows) {
            Instant createdAt = commentRow.getInstant("created_at");
            LocalDateTime dateTime = LocalDateTime.ofInstant(createdAt, ZoneId.systemDefault());
            int hour = dateTime.getHour();
            
            commentsByHour.put(hour, commentsByHour.get(hour) + 1);
        }
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ha");
        for (int hour = 0; hour < 24; hour++) {
            int commentCount = commentsByHour.get(hour);
            String timeLabel = LocalDateTime.of(2023, 1, 1, hour, 0).format(formatter);
            System.out.printf("%s: %s (%d comments)%n", 
                    timeLabel, 
                    "#".repeat(Math.min(50, commentCount)), 
                    commentCount);
        }
    }
    
    private String truncate(String text, int maxLength) {
        if (text == null) return "null";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
    
    public void close() {
        if (session != null && !session.isClosed()) {
            session.close();
            System.out.println("Cassandra session closed");
        }
    }
    
    public static void main(String[] args) {
        DataAnalyzer analyzer = new DataAnalyzer();
        try {
            analyzer.analyzeData();
        } finally {
            analyzer.close();
        }
    }
}