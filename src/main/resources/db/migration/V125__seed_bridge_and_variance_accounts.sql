-- ============================================================
-- V125: Cuentas puente y de ajuste — Contabilidad Fase 2 (WU-36b)
--
-- Reconocimiento de inventario al asumir el control físico (recepción)
-- según NIIF. Se siembran dos subcuentas postables (nivel 4):
--
--   220510  MERCADERÍA RECIBIDA NO FACTURADA
--           Cuenta puente (pasivo transitorio) bajo 2205 PROVEEDORES NACIONALES.
--           La recepción (C6) la acredita y la factura de proveedor (C5) la debita,
--           evitando el doble reconocimiento del inventario 1435.
--
--   529515  AJUSTE DIFERENCIA EN COMPRA
--           Gasto de ajuste por diferencia de precio entre el costo de recepción
--           y el subtotal facturado. Código libre bajo 5295 DIVERSOS (clase 5).
--           (El 529510 ya está en uso por "Empaques no incorporados al costo".)
--
-- Idempotente: ON CONFLICT (code) DO NOTHING.
-- ============================================================

INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
('220510','MERCADERÍA RECIBIDA NO FACTURADA', 4,'2205',2,'CREDITO',true),
('529515','AJUSTE DIFERENCIA EN COMPRA',      4,'5295',5,'DEBITO', true)
ON CONFLICT (code) DO NOTHING;
