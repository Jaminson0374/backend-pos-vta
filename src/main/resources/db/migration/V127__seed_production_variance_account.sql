-- ============================================================
-- V126: Cuenta de variación/costo de producción — Contabilidad Fase 2 (WU-36c)
--
-- La producción por fórmula (FormulaProductionUseCase) reconoce el producto terminado
-- a su costo total (materia prima + mano de obra + CIF) y da salida a los componentes
-- consumidos a su costo. La diferencia —el costo de conversión agregado— no tenía una
-- cuenta postable en el PUC y la producción quedaba sin asiento.
--
--   710595  VARIACIÓN DE INVENTARIOS DE PRODUCCIÓN
--           Subcuenta postable (nivel 4) bajo 7105 MATERIAS PRIMAS (clase 7).
--           Absorbe la diferencia entre el costo del producto terminado y el de los
--           componentes consumidos cuando no coinciden.
--
-- Idempotente: ON CONFLICT (code) DO NOTHING.
-- ============================================================

INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
('710595','VARIACIÓN DE INVENTARIOS DE PRODUCCIÓN',4,'7105',7,'DEBITO',true)
ON CONFLICT (code) DO NOTHING;
