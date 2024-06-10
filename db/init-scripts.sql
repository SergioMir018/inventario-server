-- Extensión y esquemas ya definidos
create extension hstore;

create schema users;
create schema products;
create schema orders;

-- Tabla de Usuarios
create table if not exists users."User" (
    "user_id" UUID NOT NULL PRIMARY KEY,
    "name" VARCHAR NOT NULL,
    "email" VARCHAR NOT NULL,
    "password" VARCHAR NOT NULL,
    "role" VARCHAR NOT NULL
);

-- Tabla de Categorías
CREATE TABLE products."Category" (
    "category_id" UUID NOT NULL PRIMARY KEY,
    "category_name" VARCHAR NOT NULL
);

-- Tabla de Productos
CREATE TABLE products."Product" (
    "product_id" UUID NOT NULL PRIMARY KEY,
    "name" VARCHAR NOT NULL,
    "short_desc" VARCHAR NOT NULL,
    "desc" VARCHAR NOT NULL,
    "price" FLOAT NOT NULL,
    "photo" VARCHAR,
    "category_id" UUID,
    CONSTRAINT fk_category
      FOREIGN KEY ("category_id")
      REFERENCES products."Category"("category_id")
);

-- Tabla de Órdenes
create table if not exists orders."Order" (
    "order_id" UUID NOT NULL PRIMARY KEY,
    "status" VARCHAR NOT NULL,
    "name" VARCHAR NOT NULL,
    "client_id" UUID NOT NULL REFERENCES users."User"("user_id"),
    "creation_date" DATE NOT NULL,
    "total_payment" FLOAT NOT NULL
);

-- Tabla de Relación entre Órdenes y Productos
create table if not exists orders."OrderProduct" (
    "order_id" UUID NOT NULL,
    "product_id" UUID NOT NULL,
    "quantity" INT NOT NULL,
    PRIMARY KEY ("order_id", "product_id"),
    FOREIGN KEY ("order_id") REFERENCES orders."Order"("order_id"),
    FOREIGN KEY ("product_id") REFERENCES products."Product"("product_id")
);

-- Tabla de Detalles de Orden para el Envío
create table if not exists orders."OrderDetails" (
    "order_id" UUID NOT NULL PRIMARY KEY,
    "shipping_address" VARCHAR NOT NULL,
    "billing_address" VARCHAR NOT NULL,
    "phone_number" VARCHAR NOT NULL,
    FOREIGN KEY ("order_id") REFERENCES orders."Order"("order_id")
);
