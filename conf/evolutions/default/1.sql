# Tasks schema
 
# --- !Ups

CREATE SEQUENCE user_id_seq;
CREATE TABLE core (
    id integer NOT NULL DEFAULT nextval('user_id_seq'),
    name varchar(20),
    class varchar(20),
    x int,
    y int
);
 
# --- !Downs
 
DROP TABLE core;
DROP SEQUENCE user_id_seq;