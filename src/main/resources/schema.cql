-- Using NetworkTopologyStrategy for production readiness
-- Using replication factor of 3 for high availability
CREATE KEYSPACE IF NOT EXISTS social_media
WITH REPLICATION = {
    'class': 'NetworkTopologyStrategy',
    'datacenter1': 3
};

USE social_media;

-- Users table 
CREATE TABLE IF NOT EXISTS users (
    user_id UUID PRIMARY KEY,
    username TEXT,
    full_name TEXT,
    profile_picture_url TEXT,
    created_at TIMESTAMP,
    is_active BOOLEAN
);

-- Create a secondary index on username for lookups
CREATE INDEX IF NOT EXISTS users_username_idx ON users (username);

-- Posts table (regular columns only)
CREATE TABLE IF NOT EXISTS posts (
    post_id UUID PRIMARY KEY,
    user_id UUID,
    content TEXT,
    created_at TIMESTAMP,
    is_deleted BOOLEAN
);

-- Post metrics table (counter columns only)
CREATE TABLE IF NOT EXISTS post_metrics (
    post_id UUID PRIMARY KEY,
    comment_count COUNTER,
    like_count COUNTER
);

-- Comments table 
CREATE TABLE IF NOT EXISTS comments_by_post (
    post_id UUID,
    comment_id UUID,
    user_id UUID,
    username TEXT,          
    user_profile_pic TEXT,
    content TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    is_deleted BOOLEAN,
    PRIMARY KEY ((post_id), created_at, comment_id)
) WITH CLUSTERING ORDER BY (created_at DESC, comment_id ASC)
   AND gc_grace_seconds = 864000;  

-- Comments by user
CREATE TABLE IF NOT EXISTS comments_by_user (
    user_id UUID,
    comment_id UUID,
    post_id UUID,
    content TEXT,
    created_at TIMESTAMP,
    is_deleted BOOLEAN,
    PRIMARY KEY ((user_id), created_at, comment_id)
) WITH CLUSTERING ORDER BY (created_at DESC, comment_id ASC);

-- Likes for posts
CREATE TABLE IF NOT EXISTS post_likes (
    post_id UUID,
    user_id UUID,
    username TEXT,         
    created_at TIMESTAMP,
    PRIMARY KEY ((post_id), user_id)
);

-- Likes by user
CREATE TABLE IF NOT EXISTS post_likes_by_user (
    user_id UUID,
    post_id UUID,
    created_at TIMESTAMP,
    PRIMARY KEY ((user_id), created_at, post_id)
) WITH CLUSTERING ORDER BY (created_at DESC, post_id ASC);