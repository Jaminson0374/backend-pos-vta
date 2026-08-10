-- ============================================================
-- V98: Rediseño del PUC — Decreto 2650 de 1993 + extensiones internas POS_VTA
-- Clases 1 a 9.
-- Niveles 1–4 representan la estructura PUC utilizada por POS_VTA.
-- Nivel 5 corresponde a auxiliares internos POS_VTA.
-- Clases 8 y 9: estructura pendiente de verificación contra catálogo oficial.
--
-- Referencia: PRD_PUC_POS_VTA + PUC_REVIEW_NOTES
-- ============================================================

-- ════════════════════════════════════════════════════════════
-- 1. INTEGRIDAD ESTRUCTURAL
-- ════════════════════════════════════════════════════════════

-- Asegurar que solo cuentas de nivel 4+ reciban movimientos directos
ALTER TABLE puc_accounts
    DROP CONSTRAINT IF EXISTS puc_accounts_allows_transactions_check;

ALTER TABLE puc_accounts
    ADD CONSTRAINT puc_accounts_allows_transactions_check
    CHECK (allows_transactions = false OR level >= 4);

-- ════════════════════════════════════════════════════════════
-- 2. CORRECCIÓN DE CUENTAS EXISTENTES
--    (mismo code → nombre/padre/naturaleza corregido según PRD)
-- ════════════════════════════════════════════════════════════

UPDATE puc_accounts SET name = 'DISPONIBLE'                               WHERE code = '11';
UPDATE puc_accounts SET name = 'BANCOS'                                   WHERE code = '1110';
UPDATE puc_accounts SET name = 'Comercio al por mayor y al por menor'     WHERE code = '4135';
UPDATE puc_accounts SET name = 'DETERIORO / PROTECCIÓN DE INVENTARIOS'    WHERE code = '1499';
UPDATE puc_accounts SET name = 'Productos preparados'                     WHERE code = '143020';
UPDATE puc_accounts SET name = 'Materiales de empaque'                    WHERE code = '145505';
UPDATE puc_accounts SET name = 'Depreciación acumulada - construcciones'  WHERE code = '159205';
UPDATE puc_accounts SET name = 'Depreciación acumulada - maquinaria'      WHERE code = '159210';
UPDATE puc_accounts SET name = 'Venta de carnes y cortes frescos'         WHERE code = '413505';
UPDATE puc_accounts SET name = 'Venta de productos adobados y marinados'  WHERE code = '413515';
UPDATE puc_accounts SET name = 'Venta de productos preparados'            WHERE code = '413520';
UPDATE puc_accounts SET name = 'Venta de vísceras y subproductos'         WHERE code = '413525';
UPDATE puc_accounts SET name = 'Venta de mercancías complementarias'      WHERE code = '413530';

-- Cuenta agrupadora con auxiliares: no recibe movimientos directos
-- (regla interna POS_VTA — PRD §6, Review §5)
UPDATE puc_accounts SET allows_transactions = false WHERE code = '143505';

-- Ingresos no operacionales: alinear con PRD (mitiga riesgos R1, R2)
UPDATE puc_accounts SET name = 'FINANCIEROS'               WHERE code = '4210';
UPDATE puc_accounts SET name = 'Intereses'                 WHERE code = '421005';
UPDATE puc_accounts SET name = 'Recuperación de deterioros' WHERE code = '425005';

-- ════════════════════════════════════════════════════════════
-- 3. CATÁLOGO COMPLETO — INSERTS FALTANTES
--    ON CONFLICT (code) DO NOTHING → idempotente y seguro
-- ════════════════════════════════════════════════════════════

-- ──────────────────────────────────────────────────────────
-- CLASE 1: ACTIVO (DEBITO)
-- ──────────────────────────────────────────────────────────

-- Nivel 1 — ya existe

-- Nivel 2 — Grupos faltantes
INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
('12','INVERSIONES',                                      2,'1',1,'DEBITO',false),
('16','INTANGIBLES',                                     2,'1',1,'DEBITO',false),
('17','DIFERIDOS / PAGOS ANTICIPADOS',                   2,'1',1,'DEBITO',false),
('18','OTROS ACTIVOS',                                   2,'1',1,'DEBITO',false),
('19','VALORIZACIONES / COMPONENTES HISTÓRICOS',         2,'1',1,'DEBITO',false)
ON CONFLICT (code) DO NOTHING;

-- Nivel 3 — Cuentas de nuevos grupos + ampliación grupos existentes
INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
-- Grupo 12: Inversiones
('1205','Acciones',                                      3,'12',1,'DEBITO',false),
('1210','Cuotas o partes de interés',                    3,'12',1,'DEBITO',false),
-- Grupo 13: Deudores (ampliación)
('1330','Anticipos y avances',                           3,'13',1,'DEBITO',false),
('1355','Anticipo de impuestos y contribuciones',        3,'13',1,'DEBITO',false),
('1365','Cuentas por cobrar a trabajadores',             3,'13',1,'DEBITO',false),
('1380','Deudores varios',                               3,'13',1,'DEBITO',false),
('1399','Provisiones',                                   3,'13',1,'CREDITO',false),
-- Grupo 14: Inventarios (ampliación)
('1405','MATERIAS PRIMAS',                               3,'14',1,'DEBITO',false),
('1410','PRODUCTOS EN PROCESO',                          3,'14',1,'DEBITO',false),
('1430','PRODUCTOS TERMINADOS',                          3,'14',1,'DEBITO',false),
-- Grupo 15: PP&E (ampliación)
('1504','Terrenos',                                      3,'15',1,'DEBITO',false),
('1516','Construcciones y edificaciones',                3,'15',1,'DEBITO',false),
('1520','Maquinaria y equipo',                           3,'15',1,'DEBITO',false),
('1524','Equipo de oficina',                             3,'15',1,'DEBITO',false),
('1528','Equipo de transporte',                          3,'15',1,'DEBITO',false),
-- Grupo 16: Intangibles
('1605','Crédito mercantil',                             3,'16',1,'DEBITO',false),
('1610','Marcas',                                        3,'16',1,'DEBITO',false),
('1635','Licencias',                                     3,'16',1,'DEBITO',false),
-- Grupo 17: Diferidos
('1705','Gastos pagados por anticipado',                 3,'17',1,'DEBITO',false),
-- Grupo 18: Otros activos
('1805','Bienes de arte y cultura',                      3,'18',1,'DEBITO',false),
('1895','Otros activos',                                 3,'18',1,'DEBITO',false),
-- Grupo 19: Valorizaciones
('1905','Valorizaciones',                                3,'19',1,'DEBITO',false)
ON CONFLICT (code) DO NOTHING;

-- Nivel 4 — Subcuentas
INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
-- Grupo 12: Inversiones
('120505','Acciones nacionales',                         4,'1205',1,'DEBITO',true),
('121005','Cuotas o partes de interés nacionales',       4,'1210',1,'DEBITO',true),
-- Grupo 13: Deudores (ampliación)
('133005','Anticipos a proveedores',                     4,'1330',1,'DEBITO',true),
('133010','Anticipos a contratistas',                    4,'1330',1,'DEBITO',true),
('135505','Anticipo de renta',                           4,'1355',1,'DEBITO',true),
('135515','Retención en la fuente',                      4,'1355',1,'DEBITO',true),
('136505','Préstamos a trabajadores',                    4,'1365',1,'DEBITO',true),
('138005','Otros deudores',                              4,'1380',1,'DEBITO',true),
('139905','Provisión cuentas por cobrar',                4,'1399',1,'CREDITO',true),
-- Grupo 14: Inventarios (ampliación — corregir nombres de existentes)
('140510','Insumos para producción',                     4,'1405',1,'DEBITO',true),
('140515','Condimentos y aditivos',                      4,'1405',1,'DEBITO',true),
('141010','Embutidos en proceso',                        4,'1410',1,'DEBITO',true),
('141015','Productos preparados en proceso',             4,'1410',1,'DEBITO',true),
('143025','Vísceras y subproductos',                     4,'1430',1,'DEBITO',true),
('143030','Chicharrones y derivados',                    4,'1430',1,'DEBITO',true),
('143510','Abarrotes y productos complementarios',       4,'1435',1,'DEBITO',true),
('143515','Bebidas y productos complementarios',         4,'1435',1,'DEBITO',true),
('144510','Bovinos en pie',                              4,'1445',1,'DEBITO',true),
('144515','Otros animales destinados a comercialización',4,'1445',1,'DEBITO',true),
('145515','Bandejas y recipientes',                      4,'1455',1,'DEBITO',true),
('145520','Etiquetas y material de rotulado',            4,'1455',1,'DEBITO',true),
('149910','Deterioro de mercancías',                     4,'1499',1,'CREDITO',true),
-- Grupo 15: PP&E (ampliación)
('150405','Terrenos',                                    4,'1504',1,'DEBITO',true),
('151605','Local comercial',                             4,'1516',1,'DEBITO',true),
('152005','Equipos de refrigeración',                    4,'1520',1,'DEBITO',true),
('152010','Cuartos fríos',                               4,'1520',1,'DEBITO',true),
('152015','Sierras y equipos de corte',                  4,'1520',1,'DEBITO',true),
('152020','Molinos y embutidoras',                       4,'1520',1,'DEBITO',true),
('152025','Equipos de procesamiento',                    4,'1520',1,'DEBITO',true),
('152405','Muebles y enseres',                           4,'1524',1,'DEBITO',true),
('152410','Equipos de cómputo y comunicación',           4,'1524',1,'DEBITO',true),
('152810','Motocicletas de reparto',                     4,'1528',1,'DEBITO',true),
('159215','Depreciación acumulada - equipo de oficina',  4,'1592',1,'CREDITO',true),
('159220','Depreciación acumulada - equipo de transporte',4,'1592',1,'CREDITO',true),
-- Grupo 16: Intangibles
('161005','Marcas comerciales',                          4,'1610',1,'DEBITO',true),
('163505','Licencias de software',                       4,'1635',1,'DEBITO',true),
-- Grupo 17: Diferidos
('170505','Seguros pagados por anticipado',              4,'1705',1,'DEBITO',true),
('170510','Arrendamientos pagados por anticipado',       4,'1705',1,'DEBITO',true),
-- Grupo 19: Valorizaciones
('190505','Valorización de activos',                     4,'1905',1,'DEBITO',true)
ON CONFLICT (code) DO NOTHING;

-- ──────────────────────────────────────────────────────────
-- CLASE 2: PASIVO (CREDITO)
-- ──────────────────────────────────────────────────────────

-- Nivel 1 — ya existe

-- Nivel 2 — Grupos faltantes
INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
('21','OBLIGACIONES FINANCIERAS',                       2,'2',2,'CREDITO',false),
('24','IMPUESTOS, GRAVÁMENES Y TASAS',                  2,'2',2,'CREDITO',false),
('25','OBLIGACIONES LABORALES',                         2,'2',2,'CREDITO',false),
('26','PASIVOS ESTIMADOS Y PROVISIONES',                2,'2',2,'CREDITO',false),
('27','DIFERIDOS / INGRESOS RECIBIDOS POR ANTICIPADO',  2,'2',2,'CREDITO',false),
('28','OTROS PASIVOS',                                  2,'2',2,'CREDITO',false),
('29','BONOS Y PAPELES COMERCIALES',                    2,'2',2,'CREDITO',false)
ON CONFLICT (code) DO NOTHING;

-- Re-parent 2408 (IVA por pagar) al grupo 24 recién creado
UPDATE puc_accounts SET parent_code = '24' WHERE code = '2408';

-- Nivel 3 — Cuentas
INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
-- Grupo 21: Obligaciones financieras
('2105','Bancos nacionales',                            3,'21',2,'CREDITO',false),
('2110','Bancos del exterior',                          3,'21',2,'CREDITO',false),
-- Grupo 22: Proveedores (ampliación)
('2205','PROVEEDORES NACIONALES',                       3,'22',2,'CREDITO',false),
-- Grupo 23: Cuentas por pagar (ampliación)
('2335','Costos y gastos por pagar',                    3,'23',2,'CREDITO',false),
('2370','Retenciones y aportes de nómina',              3,'23',2,'CREDITO',false),
-- Grupo 24: Impuestos (nuevo grupo)
('2408','IMPUESTO SOBRE LAS VENTAS POR PAGAR',          3,'24',2,'CREDITO',false),
-- Grupo 25: Obligaciones laborales
('2505','Salarios por pagar',                           3,'25',2,'CREDITO',false),
('2510','Cesantías consolidadas',                       3,'25',2,'CREDITO',false),
('2515','Intereses sobre cesantías',                    3,'25',2,'CREDITO',false),
('2520','Prima de servicios',                           3,'25',2,'CREDITO',false),
('2525','Vacaciones consolidadas',                      3,'25',2,'CREDITO',false),
-- Grupo 26: Pasivos estimados
('2605','Provisiones laborales',                        3,'26',2,'CREDITO',false),
('2610','Provisiones fiscales',                         3,'26',2,'CREDITO',false),
('2695','Otras provisiones',                            3,'26',2,'CREDITO',false),
-- Grupo 27: Diferidos
('2705','Ingresos recibidos por anticipado',            3,'27',2,'CREDITO',false),
-- Grupo 28: Otros pasivos
('2805','Anticipos y avances recibidos',                3,'28',2,'CREDITO',false),
('2815','Ingresos recibidos para terceros',             3,'28',2,'CREDITO',false),
-- Grupo 29: Bonos
('2905','Bonos',                                        3,'29',2,'CREDITO',false)
ON CONFLICT (code) DO NOTHING;

-- Nivel 4 — Subcuentas
INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
-- Grupo 21: Obligaciones financieras
('210505','Obligaciones financieras corto plazo',       4,'2105',2,'CREDITO',true),
-- Grupo 22: Proveedores
('220505','Proveedores de mercancías',                  4,'2205',2,'CREDITO',true),
('220515','Proveedores de servicios',                   4,'2205',2,'CREDITO',true),
-- Grupo 23: Cuentas por pagar
('233505','Servicios públicos por pagar',               4,'2335',2,'CREDITO',true),
('233510','Arrendamientos por pagar',                   4,'2335',2,'CREDITO',true),
('233515','Honorarios por pagar',                       4,'2335',2,'CREDITO',true),
('236520','Retención por compras',                      4,'2365',2,'CREDITO',true),
('236525','Retención por honorarios',                   4,'2365',2,'CREDITO',true),
('237005','Salud por pagar',                            4,'2370',2,'CREDITO',true),
('237010','Pensión por pagar',                          4,'2370',2,'CREDITO',true),
('237015','Riesgos laborales por pagar',                4,'2370',2,'CREDITO',true),
-- Grupo 24: Impuestos
('240805','IVA generado',                               4,'2408',2,'CREDITO',true),
('240815','IVA generado tarifa 19%',                    4,'2408',2,'CREDITO',true),
-- Grupo 25: Obligaciones laborales
('250505','Salarios',                                   4,'2505',2,'CREDITO',true),
('251005','Cesantías',                                  4,'2510',2,'CREDITO',true),
('251505','Intereses sobre cesantías',                  4,'2515',2,'CREDITO',true),
('252005','Prima de servicios',                         4,'2520',2,'CREDITO',true),
('252505','Vacaciones',                                 4,'2525',2,'CREDITO',true),
-- Grupo 27: Diferidos
('270505','Anticipos de clientes',                      4,'2705',2,'CREDITO',true),
-- Grupo 28: Otros pasivos
('280505','Anticipos de clientes',                      4,'2805',2,'CREDITO',true),
('281505','Valores recibidos de terceros',              4,'2815',2,'CREDITO',true)
ON CONFLICT (code) DO NOTHING;

-- ──────────────────────────────────────────────────────────
-- CLASE 3: PATRIMONIO (CREDITO)
-- ──────────────────────────────────────────────────────────

-- Nivel 2 — Grupos faltantes
INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
('32','SUPERÁVIT DE CAPITAL',                           2,'3',3,'CREDITO',false),
('33','RESERVAS',                                       2,'3',3,'CREDITO',false),
('34','COMPONENTES PATRIMONIALES DE TRANSICIÓN/HISTÓRICOS',2,'3',3,'CREDITO',false),
('37','RESULTADOS DE EJERCICIOS ANTERIORES',            2,'3',3,'CREDITO',false),
('38','SUPERÁVIT POR VALORIZACIONES',                   2,'3',3,'CREDITO',false)
ON CONFLICT (code) DO NOTHING;

-- Nivel 3
INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
-- Grupo 31: Capital (ampliación)
('3105','CAPITAL SUSCRITO Y PAGADO',                    3,'31',3,'CREDITO',false),
-- Grupo 32: Superávit
('3205','Prima en colocación de acciones',              3,'32',3,'CREDITO',false),
('3210','Donaciones',                                   3,'32',3,'CREDITO',false),
('3220','Otros aportes patrimoniales',                  3,'32',3,'CREDITO',false),
-- Grupo 33: Reservas
('3305','Reserva legal',                                3,'33',3,'CREDITO',false),
('3310','Reservas estatutarias',                        3,'33',3,'CREDITO',false),
('3315','Reservas ocasionales',                         3,'33',3,'CREDITO',false),
-- Grupo 34: Componentes patrimoniales
('3405','Ajustes patrimoniales históricos',             3,'34',3,'CREDITO',false),
('3410','Saldos de adopción / transición',              3,'34',3,'CREDITO',false),
-- Grupo 37: Resultados anteriores
('3705','Utilidades acumuladas',                        3,'37',3,'CREDITO',false),
('3710','Pérdidas acumuladas',                          3,'37',3,'DEBITO',false),
-- Grupo 38: Superávit por valorizaciones
('3805','Superávit por valorizaciones',                 3,'38',3,'CREDITO',false)
ON CONFLICT (code) DO NOTHING;

-- Nivel 4
INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
('310510','Capital por suscribir',                      4,'3105',3,'CREDITO',true),
('310515','Capital suscrito por cobrar',                4,'3105',3,'DEBITO',true)
ON CONFLICT (code) DO NOTHING;

-- ──────────────────────────────────────────────────────────
-- CLASE 4: INGRESOS (CREDITO)
-- ──────────────────────────────────────────────────────────

-- Nivel 3 — Cuentas nuevas/faltantes
INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
-- Grupo 41: Ingresos operacionales (ampliación)
('4140','HOTELES Y RESTAURANTES',                       3,'41',4,'CREDITO',false),
-- Grupo 42: Ingresos no operacionales (ampliación)
('4210','FINANCIEROS',                                  3,'42',4,'CREDITO',false),
('4295','DIVERSOS',                                     3,'42',4,'CREDITO',false)
ON CONFLICT (code) DO NOTHING;

-- Nivel 4
INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
-- Grupo 41
('413595','Venta de otros productos',                   4,'4135',4,'CREDITO',true),
('414005','Actividades de restaurante',                 4,'4140',4,'CREDITO',true),
('414010','Actividades de bar',                         4,'4140',4,'CREDITO',true),
('414095','Actividades conexas',                        4,'4140',4,'CREDITO',true),
-- Grupo 42
('421005','Intereses',                                  4,'4210',4,'CREDITO',true),
('421010','Descuentos obtenidos',                       4,'4210',4,'CREDITO',true),
('425005','Recuperación de deterioros',                 4,'4250',4,'CREDITO',true),
('425010','Recuperación de costos y gastos',            4,'4250',4,'CREDITO',true),
('429505','Aprovechamientos',                           4,'4295',4,'CREDITO',true),
('429510','Otros ingresos',                             4,'4295',4,'CREDITO',true)
ON CONFLICT (code) DO NOTHING;

-- ──────────────────────────────────────────────────────────
-- CLASE 5: GASTOS (DEBITO)
-- ──────────────────────────────────────────────────────────

-- Nivel 2 — Grupos nuevos
INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
('53','GASTOS NO OPERACIONALES',                        2,'5',5,'DEBITO',false),
('54','IMPUESTO DE RENTA Y COMPLEMENTARIOS',            2,'5',5,'DEBITO',false)
ON CONFLICT (code) DO NOTHING;

-- Nivel 3 — Cuentas nuevas dentro de grupos existentes + nuevos
INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
-- Grupo 51: Admin (ampliación)
('5110','HONORARIOS',                                   3,'51',5,'DEBITO',false),
('5125','CONTRIBUCIONES Y AFILIACIONES',                3,'51',5,'DEBITO',false),
('5130','SEGUROS',                                      3,'51',5,'DEBITO',false),
('5135','SERVICIOS',                                    3,'51',5,'DEBITO',false),
('5140','GASTOS LEGALES',                               3,'51',5,'DEBITO',false),
('5150','ADECUACIÓN E INSTALACIÓN',                     3,'51',5,'DEBITO',false),
('5160','DEPRECIACIONES',                               3,'51',5,'DEBITO',false),
('5195','DIVERSOS',                                     3,'51',5,'DEBITO',false),
-- Grupo 52: Ventas (ampliación)
('5210','HONORARIOS',                                   3,'52',5,'DEBITO',false),
('5220','ARRENDAMIENTOS',                               3,'52',5,'DEBITO',false),
('5235','SERVICIOS',                                    3,'52',5,'DEBITO',false),
('5295','DIVERSOS',                                     3,'52',5,'DEBITO',false),
-- Grupo 53: No operacionales
('5305','FINANCIEROS',                                  3,'53',5,'DEBITO',false),
-- Grupo 54: Impuesto de renta
('5405','Impuesto de renta',                            3,'54',5,'DEBITO',false)
ON CONFLICT (code) DO NOTHING;

-- Nivel 4 — Subcuentas (ampliación masiva de gastos)
INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
-- Grupo 51: Admin — Personal (ampliación)
('510515','Comisiones',                                 4,'5105',5,'DEBITO',true),
('510520','Auxilio de transporte',                      4,'5105',5,'DEBITO',true),
('510525','Cesantías',                                  4,'5105',5,'DEBITO',true),
('510530','Intereses sobre cesantías',                  4,'5105',5,'DEBITO',true),
('510535','Prima de servicios',                         4,'5105',5,'DEBITO',true),
('510540','Vacaciones',                                 4,'5105',5,'DEBITO',true),
('510545','Aportes a salud',                            4,'5105',5,'DEBITO',true),
('510550','Aportes a pensión',                          4,'5105',5,'DEBITO',true),
('510555','Riesgos laborales',                          4,'5105',5,'DEBITO',true),
-- Grupo 51: Admin — Honorarios
('511005','Honorarios profesionales',                   4,'5110',5,'DEBITO',true),
('511010','Asesoría contable',                          4,'5110',5,'DEBITO',true),
('511015','Asesoría jurídica',                          4,'5110',5,'DEBITO',true),
-- Grupo 51: Admin — Arrendamientos (ampliación)
('512010','Arrendamiento de bodega',                    4,'5120',5,'DEBITO',true),
('512015','Arrendamiento de equipos',                   4,'5120',5,'DEBITO',true),
-- Grupo 51: Admin — Contribuciones
('512505','Contribuciones',                             4,'5125',5,'DEBITO',true),
('512510','Afiliaciones',                               4,'5125',5,'DEBITO',true),
-- Grupo 51: Admin — Seguros
('513005','Seguros',                                    4,'5130',5,'DEBITO',true),
('513010','Pólizas',                                    4,'5130',5,'DEBITO',true),
-- Grupo 51: Admin — Servicios
('513505','Aseo y vigilancia',                          4,'5135',5,'DEBITO',true),
('513510','Servicios temporales',                       4,'5135',5,'DEBITO',true),
('513515','Asistencia técnica',                         4,'5135',5,'DEBITO',true),
('513520','Procesamiento electrónico de datos',         4,'5135',5,'DEBITO',true),
('513525','Acueducto y alcantarillado',                 4,'5135',5,'DEBITO',true),
('513530','Energía eléctrica',                          4,'5135',5,'DEBITO',true),
('513535','Telefonía e internet',                       4,'5135',5,'DEBITO',true),
-- Grupo 51: Admin — Legales
('514005','Notariales',                                 4,'5140',5,'DEBITO',true),
('514010','Registro mercantil',                         4,'5140',5,'DEBITO',true),
('514015','Otros gastos legales',                       4,'5140',5,'DEBITO',true),
-- Grupo 51: Admin — Adecuación
('515005','Instalaciones eléctricas',                   4,'5150',5,'DEBITO',true),
('515010','Reparaciones locativas',                     4,'5150',5,'DEBITO',true),
-- Grupo 51: Admin — Depreciaciones
('516005','Construcciones',                             4,'5160',5,'DEBITO',true),
('516010','Maquinaria',                                 4,'5160',5,'DEBITO',true),
('516015','Equipo de oficina',                          4,'5160',5,'DEBITO',true),
('516020','Equipo de cómputo',                          4,'5160',5,'DEBITO',true),
('516025','Equipo de transporte',                       4,'5160',5,'DEBITO',true),
-- Grupo 51: Admin — Diversos
('519505','Elementos de aseo',                          4,'5195',5,'DEBITO',true),
('519510','Papelería',                                  4,'5195',5,'DEBITO',true),
('519515','Cafetería',                                  4,'5195',5,'DEBITO',true),
('519520','Transporte',                                 4,'5195',5,'DEBITO',true),
('519525','Gastos no recuperables',                     4,'5195',5,'DEBITO',true),
-- Grupo 52: Ventas — Personal
('520505','Sueldos y salarios',                         4,'5205',5,'DEBITO',true),
('520510','Comisiones de ventas',                       4,'5205',5,'DEBITO',true),
('520515','Prestaciones sociales',                      4,'5205',5,'DEBITO',true),
-- Grupo 52: Ventas — Honorarios
('521005','Honorarios comerciales',                     4,'5210',5,'DEBITO',true),
-- Grupo 52: Ventas — Arrendamientos
('522005','Arrendamiento punto de venta',               4,'5220',5,'DEBITO',true),
-- Grupo 52: Ventas — Servicios
('523505','Publicidad',                                 4,'5235',5,'DEBITO',true),
('523510','Vigilancia',                                 4,'5235',5,'DEBITO',true),
('523515','Transporte de mercancías',                   4,'5235',5,'DEBITO',true),
-- Grupo 52: Ventas — Publicidad
('524505','Publicidad',                                 4,'5245',5,'DEBITO',true),
('524510','Promociones',                                4,'5245',5,'DEBITO',true),
-- Grupo 52: Ventas — Diversos
('529505','Gastos de venta',                            4,'5295',5,'DEBITO',true),
('529510','Empaques no incorporados al costo',          4,'5295',5,'DEBITO',true),
-- Grupo 53: No operacionales
('530505','Gastos bancarios',                           4,'5305',5,'DEBITO',true),
('530510','Intereses',                                  4,'5305',5,'DEBITO',true),
('530515','Comisiones bancarias',                       4,'5305',5,'DEBITO',true),
-- Grupo 54: Impuesto de renta
('540505','Impuesto de renta corriente',                4,'5405',5,'DEBITO',true)
ON CONFLICT (code) DO NOTHING;

-- ──────────────────────────────────────────────────────────
-- CLASE 6: COSTOS DE VENTAS (DEBITO)
-- ──────────────────────────────────────────────────────────

-- Nivel 2 — Grupo nuevo: COMPRAS
INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
('62','COMPRAS',                                        2,'6',6,'DEBITO',false)
ON CONFLICT (code) DO NOTHING;

-- Nivel 3
INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
('6205','DE MERCANCÍAS',                                3,'62',6,'DEBITO',false),
('6210','DE MATERIAS PRIMAS',                           3,'62',6,'DEBITO',false),
('6215','DE MATERIALES INDIRECTOS',                     3,'62',6,'DEBITO',false),
('6225','DEVOLUCIONES, REBAJAS Y DESCUENTOS EN COMPRAS',3,'62',6,'CREDITO',false)
ON CONFLICT (code) DO NOTHING;

-- Nivel 4
INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
-- Grupo 61: Costo de ventas (ampliación)
('613595','Costo de ventas - otros productos',          4,'6135',6,'DEBITO',true),
-- Grupo 62: Compras
('620505','Compras de mercancías para la venta',        4,'6205',6,'DEBITO',true),
('621005','Compras de materias primas cárnicas',        4,'6210',6,'DEBITO',true),
('621010','Compras de insumos de producción',           4,'6210',6,'DEBITO',true),
('621505','Compras de materiales indirectos',           4,'6215',6,'DEBITO',true),
('622505','Devoluciones en compras',                    4,'6225',6,'CREDITO',true),
('622510','Descuentos en compras',                      4,'6225',6,'CREDITO',true)
ON CONFLICT (code) DO NOTHING;

-- ──────────────────────────────────────────────────────────
-- CLASE 7: COSTOS DE PRODUCCIÓN (DEBITO)
-- ──────────────────────────────────────────────────────────

-- Nivel 2 — Grupo nuevo
INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
('74','CONTRATOS DE SERVICIOS',                         2,'7',7,'DEBITO',false)
ON CONFLICT (code) DO NOTHING;

-- Nivel 3
INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
-- Grupo 71: Materia prima (renombrar existente 7105)
('7105','Materias primas',                              3,'71',7,'DEBITO',false),
-- Grupo 73: Costos indirectos (ampliación)
('7320','Mantenimiento',                                3,'73',7,'DEBITO',false),
('7330','Depreciación de producción',                   3,'73',7,'DEBITO',false),
-- Grupo 74: Contratos de servicios
('7405','Servicios contratados para producción',        3,'74',7,'DEBITO',false)
ON CONFLICT (code) DO NOTHING;

-- Nivel 4
INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
-- Grupo 71: Materia prima (ampliación + renombrar)
('710510','Cerdos en pie destinados a producción',      4,'7105',7,'DEBITO',true),
('710515','Bovinos / carne en canal',                   4,'7105',7,'DEBITO',true),
('710520','Materias primas para embutidos',             4,'7105',7,'DEBITO',true),
('710525','Condimentos y aditivos de producción',       4,'7105',7,'DEBITO',true),
-- Grupo 72: Mano de obra (ampliación)
('720510','Operarios de producción',                    4,'7205',7,'DEBITO',true),
('720515','Elaboración de embutidos',                   4,'7205',7,'DEBITO',true),
('720520','Prestaciones y cargas sociales de producción',4,'7205',7,'DEBITO',true),
-- Grupo 73: Costos indirectos (ampliación)
('730505','Empaques utilizados en producción',          4,'7305',7,'DEBITO',true),
('732005','Mantenimiento de maquinaria',                4,'7320',7,'DEBITO',true),
('732010','Mantenimiento de cuartos fríos',             4,'7320',7,'DEBITO',true),
('733005','Depreciación de maquinaria',                 4,'7330',7,'DEBITO',true),
('733010','Depreciación de equipos de refrigeración',   4,'7330',7,'DEBITO',true),
-- Grupo 74: Contratos de servicios
('740505','Servicios de maquila',                       4,'7405',7,'DEBITO',true),
('740510','Servicios de procesamiento',                 4,'7405',7,'DEBITO',true)
ON CONFLICT (code) DO NOTHING;

-- Corregir nombre de 7105 (ya existe, se renombra)
UPDATE puc_accounts SET name = 'Materias primas' WHERE code = '7105';

-- ──────────────────────────────────────────────────────────
-- CLASE 8: CUENTAS DE ORDEN DEUDORAS (DEBITO)
-- ⚠ ESTRUCTURA PENDIENTE DE VERIFICACIÓN contra catálogo oficial.
-- Solo se incluyen niveles 1–3 (estructura). Las subcuentas (nivel 4)
-- deben validarse contra el Decreto 2650 antes de insertarse.
-- ──────────────────────────────────────────────────────────

INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
-- Nivel 1 — Clase
('8','CUENTAS DE ORDEN DEUDORAS',                       1,NULL, 8,'DEBITO',false)
ON CONFLICT (code) DO NOTHING;

-- Nivel 2
INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
('81','DERECHOS CONTINGENTES',                          2,'8',8,'DEBITO',false),
('82','DEUDORAS FISCALES',                              2,'8',8,'DEBITO',false),
('83','DEUDORAS DE CONTROL',                            2,'8',8,'DEBITO',false),
('84','DERECHOS CONTINGENTES POR CONTRA',               2,'8',8,'CREDITO',false),
('85','DEUDORAS FISCALES POR CONTRA',                   2,'8',8,'CREDITO',false),
('86','DEUDORAS DE CONTROL POR CONTRA',                 2,'8',8,'CREDITO',false)
ON CONFLICT (code) DO NOTHING;

-- Nivel 3
INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
('8105','BIENES Y VALORES ENTREGADOS EN CUSTODIA',      3,'81',8,'DEBITO',false),
('8110','BIENES Y VALORES ENTREGADOS EN GARANTÍA',      3,'81',8,'DEBITO',false),
('8205','Derechos fiscales',                             3,'82',8,'DEBITO',false),
('8305','BIENES Y VALORES RECIBIDOS EN CUSTODIA',       3,'83',8,'DEBITO',false),
('8310','ACTIVOS TOTALMENTE DEPRECIADOS',                3,'83',8,'DEBITO',false),
('8405','Derechos contingentes por contra',              3,'84',8,'CREDITO',false),
('8505','Deudoras fiscales por contra',                  3,'85',8,'CREDITO',false),
('8605','Deudoras de control por contra',                3,'86',8,'CREDITO',false)
ON CONFLICT (code) DO NOTHING;

-- Subcuentas nivel 4: PENDIENTES DE VERIFICACIÓN (Review §8)
-- INSERTAR solo después de validar contra Decreto 2650.

-- ──────────────────────────────────────────────────────────
-- CLASE 9: CUENTAS DE ORDEN ACREEDORAS (CREDITO)
-- ⚠ ESTRUCTURA PENDIENTE DE VERIFICACIÓN contra catálogo oficial.
-- Solo se incluyen niveles 1–3 (estructura). Las subcuentas (nivel 4)
-- deben validarse contra el Decreto 2650 antes de insertarse.
-- ──────────────────────────────────────────────────────────

INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
-- Nivel 1 — Clase
('9','CUENTAS DE ORDEN ACREEDORAS',                     1,NULL, 9,'CREDITO',false)
ON CONFLICT (code) DO NOTHING;

-- Nivel 2
INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
('91','RESPONSABILIDADES CONTINGENTES',                 2,'9',9,'CREDITO',false),
('92','ACREEDORAS FISCALES',                            2,'9',9,'CREDITO',false),
('93','ACREEDORAS DE CONTROL',                          2,'9',9,'CREDITO',false),
('94','RESPONSABILIDADES CONTINGENTES POR CONTRA',      2,'9',9,'DEBITO',false),
('95','ACREEDORAS FISCALES POR CONTRA',                 2,'9',9,'DEBITO',false),
('96','ACREEDORAS DE CONTROL POR CONTRA',               2,'9',9,'DEBITO',false)
ON CONFLICT (code) DO NOTHING;

-- Nivel 3
INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
('9115','BIENES Y VALORES RECIBIDOS DE TERCEROS',       3,'91',9,'CREDITO',false),
('9120','LITIGIOS Y/O DEMANDAS',                        3,'91',9,'CREDITO',false),
('9135','CUENTAS EN PARTICIPACIÓN',                     3,'91',9,'CREDITO',false),
('9205','Acreedoras fiscales',                          3,'92',9,'CREDITO',false),
('9305','Bienes y valores recibidos de terceros',       3,'93',9,'CREDITO',false),
('9405','Responsabilidades contingentes por contra',    3,'94',9,'DEBITO',false),
('9505','Acreedoras fiscales por contra',               3,'95',9,'DEBITO',false),
('9605','Acreedoras de control por contra',             3,'96',9,'DEBITO',false)
ON CONFLICT (code) DO NOTHING;

-- Subcuentas nivel 4: PENDIENTES DE VERIFICACIÓN (Review §8)
-- INSERTAR solo después de validar contra Decreto 2650.

-- ════════════════════════════════════════════════════════════
-- 4. AUXILIARES INTERNOS — Nivel 5 (POS_VTA)
--    Estos son extensiones internas, NO oficiales del PUC.
--    No sustituyen la tabla de productos.
-- ════════════════════════════════════════════════════════════

INSERT INTO puc_accounts (code,name,level,parent_code,account_class,account_nature,allows_transactions) VALUES
-- Auxiliares bajo 143505 (Mercancías para la venta)
('14350501','Carnes de res',                             5,'143505',1,'DEBITO',true),
('14350502','Carnes de cerdo',                           5,'143505',1,'DEBITO',true),
('14350503','Pollo',                                     5,'143505',1,'DEBITO',true),
('14350504','Embutidos',                                 5,'143505',1,'DEBITO',true),
('14350505','Complementarios',                           5,'143505',1,'DEBITO',true)
ON CONFLICT (code) DO NOTHING;

-- ════════════════════════════════════════════════════════════
-- FIN V98
-- ════════════════════════════════════════════════════════════
