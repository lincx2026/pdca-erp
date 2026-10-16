-- Recover legacy UTF-8 text decoded as Windows-1252, including undefined C1 bytes.
-- No customer/supplier records or contact details are embedded in this migration.
CREATE OR REPLACE FUNCTION pg_temp.pcba_recover_partner_text(value TEXT)
RETURNS TEXT LANGUAGE plpgsql IMMUTABLE STRICT AS $repair$
DECLARE
    windows_high TEXT := U&'\20AC\0081\201A\0192\201E\2026\2020\2021\02C6\2030\0160\2039\0152\008D\017D\008F\0090\2018\2019\201C\201D\2022\2013\2014\02DC\2122\0161\203A\0153\009D\017E\0178';
    original_bytes BYTEA := ''::BYTEA;
    letter TEXT;
    codepoint INTEGER;
    high_position INTEGER;
    recovered TEXT;
BEGIN
    -- ponytail: byte concatenation suits short metadata; batch bytes for very large text.
    FOR position_index IN 1..char_length(value) LOOP
        letter := substr(value, position_index, 1);
        codepoint := ascii(letter);
        IF codepoint > 255 THEN
            high_position := strpos(windows_high, letter);
            IF high_position = 0 THEN RETURN value; END IF;
            codepoint := high_position + 127;
        END IF;
        original_bytes := original_bytes || decode(lpad(to_hex(codepoint), 2, '0'), 'hex');
    END LOOP;
    recovered := convert_from(original_bytes, 'UTF8');
    IF recovered ~ U&'[\4E00-\9FFF]' THEN RETURN recovered; END IF;
    RETURN value;
EXCEPTION WHEN character_not_in_repertoire THEN
    -- Irrecoverable text and correctly encoded accented names must never be guessed.
    RETURN value;
END;
$repair$;

DO $repair$
DECLARE target RECORD;
BEGIN
    FOR target IN
        SELECT table_name, column_name
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name ~ '^(scm|sal|pdm|wms|fas|pmm|fin)_'
          AND data_type IN ('text', 'character varying', 'character')
          AND (
              (table_name IN ('scm_supplier', 'scm_customer') AND column_name IN (
                  'supplier_name', 'customer_name', 'short_name', 'industry', 'contact',
                  'address', 'remark', 'payment_terms', 'payee_name', 'payee_bank',
                  'payer_name', 'payer_bank', 'sales_rep_name'))
              OR column_name IN ('supplier_name', 'customer_name', 'payee_name', 'payer_name', 'payee_bank', 'payer_bank')
              OR (table_name IN ('scm_purchase_order', 'sal_sales_order') AND column_name = 'contact_name')
          )
    LOOP
        EXECUTE format(
            'UPDATE public.%I SET %I = pg_temp.pcba_recover_partner_text(%I) '
            'WHERE %I IS DISTINCT FROM pg_temp.pcba_recover_partner_text(%I)',
            target.table_name, target.column_name, target.column_name,
            target.column_name, target.column_name);
    END LOOP;
END;
$repair$;

DROP FUNCTION pg_temp.pcba_recover_partner_text(TEXT);
