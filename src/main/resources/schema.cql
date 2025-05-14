-- Create a keyspace using NetworkTopologyStrategy for production readiness
-- Using replication factor of 3 for high availability
CREATE KEYSPACE IF NOT EXISTS social_media
WITH REPLICATION = {
    'class': 'NetworkTopologyStrategy',
    'datacenter1': 3
};

USE social_media;

-- Users table - Stores user profile information
CREATE TABLE IF NOT EXISTS users (
    user_id UUID PRIMARY KEY,
    username TEXT,
    email TEXT,
    full_name TEXT,
    bio TEXT,
    profile_picture_url TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    is_active BOOLEAN
);

-- Create a secondary index on username for login purposes
CREATE INDEX IF NOT EXISTS users_username_idx ON users (username);
-- Create a secondary index on email for login/recovery purposes
CREATE INDEX IF NOT EXISTS users_email_idx ON users (email);

-- Posts table - Store user posts
CREATE TABLE IF NOT EXISTS posts (
    post_id UUID,
    user_id UUID,
    content TEXT,
    media_urls LIST<TEXT>,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    is_deleted BOOLEAN,
    PRIMARY KEY (post_id)
);

-- User posts by time - For timeline and profile views
CREATE TABLE IF NOT EXISTS posts_by_user (
    user_id UUID,
    post_id UUID,
    created_at TIMESTAMP,
    content TEXT,
    media_urls LIST<TEXT>,
    is_deleted BOOLEAN,
    PRIMARY KEY ((user_id), created_at, post_id)
) WITH CLUSTERING ORDER BY (created_at DESC, post_id ASC);

-- Follow relationships
CREATE TABLE IF NOT EXISTS follows (
    follower_id UUID,
    followed_id UUID,
    created_at TIMESTAMP,
    PRIMARY KEY ((follower_id), followed_id)
);

-- Followers by user - Who follows me?
CREATE TABLE IF NOT EXISTS followers_by_user (
    followed_id UUID,
    follower_id UUID,
    created_at TIMESTAMP,
    PRIMARY KEY ((followed_id), follower_id)
);

-- Likes for posts
CREATE TABLE IF NOT EXISTS post_likes (
    post_id UUID,
    user_id UUID,
    created_at TIMESTAMP,
    PRIMARY KEY ((post_id), user_id)
);

-- Likes by user - Posts that a user has liked
CREATE TABLE IF NOT EXISTS likes_by_user (
    user_id UUID,
    post_id UUID,
    created_at TIMESTAMP,
    PRIMARY KEY ((user_id), post_id)
);

-- Comments on posts
CREATE TABLE IF NOT EXISTS comments (
    comment_id UUID,
    post_id UUID,
    user_id UUID,
    content TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    is_deleted BOOLEAN,
    PRIMARY KEY ((post_id), created_at, comment_id)
) WITH CLUSTERING ORDER BY (created_at DESC, comment_id ASC);


-- Tags/Hashtags table
CREATE TABLE IF NOT EXISTS tags (
    tag_name TEXT PRIMARY KEY,
    post_count COUNTER
);

-- Posts by tag for searching by hashtag
CREATE TABLE IF NOT EXISTS posts_by_tag (
    tag_name TEXT,
    post_id UUID,
    user_id UUID,
    created_at TIMESTAMP,
    content TEXT,
    media_urls LIST<TEXT>,
    PRIMARY KEY ((tag_name), created_at, post_id)
) WITH CLUSTERING ORDER BY (created_at DESC, post_id ASC);