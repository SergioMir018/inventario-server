create extension hstore;
create schema users;
create schema products;
create table if not exists users."User" (
    "user_id" UUID NOT NULL PRIMARY KEY,
    "name" VARCHAR NOT NULL,
    "email" VARCHAR NOT NULL,
    "password" VARCHAR NOT NULL,
    "role" VARCHAR NOT NULL
);

create table if not exists products."Product" (
    "product_id" UUID NOT NULL PRIMARY KEY,
    "name" VARCHAR NOT NULL,
    "short_desc" VARCHAR NOT NULL,
    "desc" VARCHAR NOT NULL,
    "price" FLOAT NOT NULL,
    "photo" VARCHAR
);
