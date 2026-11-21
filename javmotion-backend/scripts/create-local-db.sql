-- One-time setup for a locally installed MySQL 8 (instead of docker-compose).
-- Run as an admin user, e.g.:
--   mysql -u root -p -P 3300 < scripts/create-local-db.sql
-- Then start the API with DB_URL=jdbc:mysql://localhost:3300/javmotion (dev profile defaults
-- the username/password to javmotion/javmotion). Flyway creates the tables on first start.
-- Change the password if this MySQL is reachable from other machines.

CREATE DATABASE IF NOT EXISTS javmotion CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'javmotion'@'localhost' IDENTIFIED BY 'javmotion';
GRANT ALL PRIVILEGES ON javmotion.* TO 'javmotion'@'localhost';
FLUSH PRIVILEGES;
