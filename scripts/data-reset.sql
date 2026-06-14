-- ============================================================
-- DATA RESET: Truncate all transactional data
-- Preserves: users, roles, templates, PUC, company_config,
--            and catalog seed data
-- ============================================================

DO $$
DECLARE
    preserved_tables TEXT[] := ARRAY[
        'roles',
        'users',
        'flyway_schema_history',
        -- Accounting templates
        'accounting_templates',
        'accounting_template_entries',
        -- PUC (referenced by templates)
        'puc_accounts',
        -- Company config
        'company_config',
        -- Catalog / seed data
        'units_of_measure',
        'product_types',
        'product_states',
        'brands',
        'product_models',
        'product_categories',
        'product_groups',
        'warehouse_locations',
        'identification_types',
        'departments',
        'cities',
        'third_party_categories',
        'price_lists'
    ];
    r RECORD;
    truncated_count INTEGER := 0;
BEGIN
    RAISE NOTICE 'Starting data reset...';
    RAISE NOTICE 'Preserved tables: %', array_to_string(preserved_tables, ', ');

    FOR r IN
        SELECT tablename
        FROM pg_tables
        WHERE schemaname = 'public'
          AND tablename <> ALL(preserved_tables)
        ORDER BY tablename
    LOOP
        BEGIN
            EXECUTE 'TRUNCATE TABLE ' || quote_ident(r.tablename) || ' RESTART IDENTITY CASCADE';
            truncated_count := truncated_count + 1;
            RAISE NOTICE '  Truncated: %', r.tablename;
        EXCEPTION WHEN OTHERS THEN
            RAISE WARNING '  Failed to truncate %: %', r.tablename, SQLERRM;
        END;
    END LOOP;

    RAISE NOTICE 'Done. % tables truncated.', truncated_count;
END $$;
