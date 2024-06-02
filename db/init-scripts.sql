create extension hstore;
create schema users;
create table if not exists users."User" ("user_id" BIGSERIAL NOT NULL PRIMARY KEY,"name" VARCHAR NOT NULL,"email" VARCHAR NOT NULL,"password" VARCHAR NOT NULL, "role" VARCHAR NOT NULL);
