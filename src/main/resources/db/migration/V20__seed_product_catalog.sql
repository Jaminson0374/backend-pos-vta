-- ============================================================
-- V20: Datos semilla del catálogo de productos
-- Tipos, estados, unidades de medida, listas de precios,
-- categorías y grupos (Tabla 1 del PDF)
-- ============================================================

-- ──────────────────────────────────────────────────────────
-- 1. Tipos de artículo
-- ──────────────────────────────────────────────────────────
INSERT INTO product_types (code, name) VALUES
('PRODUCTO',   'Producto'),
('SERVICIO',   'Servicio'),
('INSUMO',     'Insumo'),
('COMBO',      'Combo'),
('FORMULA',    'Fórmula / Receta'),
('MATERIA_P',  'Materia Prima'),
('SEMOVIENTE', 'Semoviente / Animal en Pie');

-- ──────────────────────────────────────────────────────────
-- 2. Estados del artículo
-- ──────────────────────────────────────────────────────────
INSERT INTO product_states (code, name) VALUES
('SEMIELABORADO', 'Semielaborado'),
('TERMINADO',     'Terminado'),
('EN_PROCESO',    'En Proceso'),
('DESCONTINUADO', 'Descontinuado');

-- ──────────────────────────────────────────────────────────
-- 3. Unidades de medida (las más comunes en Colombia)
-- ──────────────────────────────────────────────────────────
INSERT INTO units_of_measure (code, name, base_unit) VALUES
('KG',   'Kilogramo',        'kg'),
('G',    'Gramo',            'kg'),
('LB',   'Libra',            'kg'),
('TON',  'Tonelada',         'kg'),
('UND',  'Unidad',           'unit'),
('PAR',  'Par',              'unit'),
('CAJ',  'Caja',             'unit'),
('PAQ',  'Paquete',          'unit'),
('BOL',  'Bolsa',            'unit'),
('TAR',  'Tarro',            'unit'),
('L',    'Litro',            'l'),
('ML',   'Mililitro',        'l'),
('GAL',  'Galón',            'l'),
('M',    'Metro',            'm'),
('CM',   'Centímetro',       'm'),
('M2',   'Metro cuadrado',   'm2'),
('M3',   'Metro cúbico',     'm3'),
('HRS',  'Hora',             'h'),
('PORCIONES', 'Porción',     'unit');

-- ──────────────────────────────────────────────────────────
-- 4. Listas de precios (Pv1, Pv2, Pv3 del PDF)
-- ──────────────────────────────────────────────────────────
INSERT INTO price_lists (code, name, description) VALUES
('PV1', 'Normal (Detal)',    'Precio público general, venta al detal en mostrador'),
('PV2', 'Mayorista',         'Precio para clientes mayoristas y restaurantes con volumen'),
('PV3', 'Crédito',           'Precio para clientes con cupo de crédito aprobado')
ON CONFLICT (name) DO UPDATE SET code = EXCLUDED.code, description = EXCLUDED.description;

-- ──────────────────────────────────────────────────────────
-- 5. Categorías de productos (Tabla 1 del PDF)
-- ──────────────────────────────────────────────────────────
INSERT INTO product_categories (name) VALUES
('Carne de Cerdo (Cortes Primarios)'),
('Productos Transformados (Embutidos y Charcutería)'),
('Productos Listos para Cocinar (Adobados/Apanados)'),
('Línea de Chicharrón y Fritos'),
('Subproductos y Varios');

-- ──────────────────────────────────────────────────────────
-- 6. Grupos de productos (Tabla 1 del PDF)
-- ──────────────────────────────────────────────────────────
INSERT INTO product_groups (name, category_id)
SELECT 'Cortes de Lomo',        id FROM product_categories WHERE name = 'Carne de Cerdo (Cortes Primarios)'
UNION ALL
SELECT 'Cortes de Pierna',      id FROM product_categories WHERE name = 'Carne de Cerdo (Cortes Primarios)'
UNION ALL
SELECT 'Cortes de Brazo',       id FROM product_categories WHERE name = 'Carne de Cerdo (Cortes Primarios)'
UNION ALL
SELECT 'Costillas',             id FROM product_categories WHERE name = 'Carne de Cerdo (Cortes Primarios)'
UNION ALL
SELECT 'Tocinería',             id FROM product_categories WHERE name = 'Carne de Cerdo (Cortes Primarios)'
UNION ALL
SELECT 'Chorizos',              id FROM product_categories WHERE name = 'Productos Transformados (Embutidos y Charcutería)'
UNION ALL
SELECT 'Rellenas y Morcillas',  id FROM product_categories WHERE name = 'Productos Transformados (Embutidos y Charcutería)'
UNION ALL
SELECT 'Madurados',             id FROM product_categories WHERE name = 'Productos Transformados (Embutidos y Charcutería)'
UNION ALL
SELECT 'Carnes Frías de la Casa', id FROM product_categories WHERE name = 'Productos Transformados (Embutidos y Charcutería)'
UNION ALL
SELECT 'Porciones Adobadas',    id FROM product_categories WHERE name = 'Productos Listos para Cocinar (Adobados/Apanados)'
UNION ALL
SELECT 'Apanados',              id FROM product_categories WHERE name = 'Productos Listos para Cocinar (Adobados/Apanados)'
UNION ALL
SELECT 'Brochetas / Pinchos',   id FROM product_categories WHERE name = 'Productos Listos para Cocinar (Adobados/Apanados)'
UNION ALL
SELECT 'Chicharrón',            id FROM product_categories WHERE name = 'Línea de Chicharrón y Fritos'
UNION ALL
SELECT 'Snacks',                id FROM product_categories WHERE name = 'Línea de Chicharrón y Fritos'
UNION ALL
SELECT 'Vísceras',              id FROM product_categories WHERE name = 'Subproductos y Varios'
UNION ALL
SELECT 'Huesos y Grasa',        id FROM product_categories WHERE name = 'Subproductos y Varios'
UNION ALL
SELECT 'Otros',                 id FROM product_categories WHERE name = 'Subproductos y Varios';
