-- Enable TimescaleDB extension
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- Create hypertable for news content (time-series data)
-- This will be created after the application creates the base news_content table
-- Run after first migration: SELECT create_hypertable('news_content', 'created_at', if_not_exists => TRUE);

-- Create indexes for better query performance on time-series data
-- These will be created after tables are established by migrations
