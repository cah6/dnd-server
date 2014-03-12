# Tasks schema
 
# --- !Ups

CREATE SEQUENCE user_id_seq;
CREATE TABLE users (
    id integer NOT NULL DEFAULT nextval('user_id_seq'),
    username varchar(20),
    isOnline boolean
);
 
# --- !Downs
 
DROP TABLE users;
DROP SEQUENCE user_id_seq;