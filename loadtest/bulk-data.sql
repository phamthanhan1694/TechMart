-- Tap du lieu cho load test: 50.000 san pham.
--
-- VI SAO CAN: voi 345 san pham cua du lieu demo, PostgreSQL chon Seq Scan va
-- BO QUA hoan toan GIN index - vi quet ca bang 345 dong con re hon dung index.
-- Do "truoc/sau khi them index" o quy mo do se ra chenh lech bang 0, va bao cao
-- no nhu mot cai thien la khong trung thuc.
--
-- Catalog that cua mot cua hang dien tu co hang chuc nghin SKU, nen 50.000 la
-- quy mo hop ly de do, khong phai con so bia ra cho dep.
--
-- Dung:
--   docker exec -i techmart-postgres psql -U techmart -d techmart < loadtest/bulk-data.sql
-- Don sach sau khi do xong:
--   docker exec -i techmart-postgres psql -U techmart -d techmart -c "\i loadtest/cleanup-bulk.sql"
--   (hoac chay khoi CLEANUP o cuoi file nay)

\timing on

-- ---------------------------------------------------------------- san pham
INSERT INTO products (category_id, brand_id, name, slug, description, thumbnail_url,
                      spec, status, created_at, updated_at)
SELECT
    cat.ids[1 + (i % array_length(cat.ids, 1))],
    br.ids[1 + (i % array_length(br.ids, 1))],
    'LoadTest ' || model.name || ' ' || i,
    'loadtest-' || lower(replace(model.name, ' ', '-')) || '-' || i,
    'San pham sinh tu dong cho load test.',
    'https://placehold.co/800x800/png?text=LT' || i,
    jsonb_build_object(
        'ram',        (ARRAY['4GB','6GB','8GB','12GB','16GB','32GB'])[1 + (i % 6)],
        'storage',    (ARRAY['64GB','128GB','256GB','512GB','1TB'])[1 + (i % 5)],
        'chipset',    (ARRAY['A17 Pro','Snapdragon 8 Gen 3','Dimensity 9300','Exynos 2400','Tensor G3'])[1 + (i % 5)],
        'screen',     (ARRAY['6.1 inch OLED','6.7 inch OLED','6.4 inch AMOLED','14 inch Retina'])[1 + (i % 4)],
        'os',         (ARRAY['iOS 17','Android 14','Windows 11','macOS Sonoma'])[1 + (i % 4)],
        'releaseYear', 2020 + (i % 6)
    ),
    'ACTIVE',
    now() - (i % 900) * interval '1 hour',
    now()
FROM generate_series(1, 50000) AS i,
     LATERAL (SELECT array_agg(id ORDER BY id) AS ids FROM categories) cat,
     LATERAL (SELECT array_agg(id ORDER BY id) AS ids FROM brands) br,
     LATERAL (SELECT (ARRAY['iPhone','Galaxy','Xiaomi','MacBook','ThinkPad','ROG','Pixel','Inspiron'])[1 + (i % 8)] AS name) model;

-- ---------------------------------------------------------------- variant
-- Moi san pham mot variant. Du de bo loc gia va inStock hoat dong dung.
INSERT INTO product_variants (product_id, sku, name, price, compare_at_price,
                              stock_quantity, reserved_quantity, attributes, version,
                              created_at, updated_at)
SELECT
    p.id,
    'LT-' || p.id,
    'Ban tieu chuan',
    (5000000 + (p.id % 40) * 1000000)::numeric(15,2),
    (7000000 + (p.id % 40) * 1000000)::numeric(15,2),
    10 + (p.id % 90),
    0,
    jsonb_build_object('color', (ARRAY['Den','Trang','Xanh','Bac'])[1 + (p.id % 4)]),
    0,
    now(), now()
FROM products p
WHERE p.slug LIKE 'loadtest-%';

ANALYZE products;
ANALYZE product_variants;

SELECT count(*) AS tong_san_pham FROM products;
SELECT count(*) AS tong_variant FROM product_variants;
