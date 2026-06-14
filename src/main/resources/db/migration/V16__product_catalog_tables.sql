-- ============================================================
-- V16: Tablas catálogo para módulo de Productos
-- Tipos, estados, marcas, modelos, categorías, grupos,
-- unidades de medida, listas de precios, ubicaciones en bodega
-- ============================================================

-- 1. Tipos de artículo (Producto, Servicio, Insumo, Combo, Fórmula-Receta, etc.)
CREATE TABLE product_types (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code       VARCHAR(20)  NOT NULL UNIQUE,
    name       VARCHAR(100) NOT NULL UNIQUE,
    active     BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 2. Estados del artículo (Semielaborado, Terminado, etc.)
CREATE TABLE product_states (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code       VARCHAR(20)  NOT NULL UNIQUE,
    name       VARCHAR(100) NOT NULL UNIQUE,
    active     BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 3. Marcas
CREATE TABLE brands (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(100) NOT NULL UNIQUE,
    active     BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 4. Modelos (pertenecen a una marca, opcional)
CREATE TABLE product_models (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(100) NOT NULL,
    brand_id   UUID         REFERENCES brands(id) ON DELETE SET NULL,
    active     BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (name, brand_id)
);

CREATE INDEX idx_product_models_brand ON product_models(brand_id);

-- 5. Categorías de productos (clasificación de primer nivel)
CREATE TABLE product_categories (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(150) NOT NULL UNIQUE,
    active     BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 6. Grupos de productos (clasificación de segundo nivel, dentro de una categoría)
CREATE TABLE product_groups (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(150) NOT NULL,
    category_id UUID         REFERENCES product_categories(id) ON DELETE SET NULL,
    active      BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (name, category_id)
);

CREATE INDEX idx_product_groups_category ON product_groups(category_id);

-- 7. Unidades de medida (catálogo dinámico, reemplaza el enum anterior)
CREATE TABLE units_of_measure (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code       VARCHAR(20)  NOT NULL UNIQUE,
    name       VARCHAR(100) NOT NULL UNIQUE,
    base_unit  VARCHAR(20),   -- para conversiones futuras: kg, unit, l, m, etc.
    active     BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 8. Listas de precios — extender tabla existente de V8 con columna code
ALTER TABLE price_lists ADD COLUMN IF NOT EXISTS code VARCHAR(20);
-- Renombrar is_active → active si existe (V8 usó is_active)
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='price_lists' AND column_name='is_active') THEN
    ALTER TABLE price_lists RENAME COLUMN is_active TO active;
  END IF;
END $$;

-- 9. Ubicaciones dentro de bodegas (estanterías, rieles, zonas)
CREATE TABLE warehouse_locations (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    warehouse_id UUID         NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
    name         VARCHAR(100) NOT NULL,
    description  VARCHAR(255),
    active       BOOLEAN      NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (warehouse_id, name)
);

CREATE INDEX idx_wh_locations_warehouse ON warehouse_locations(warehouse_id);
