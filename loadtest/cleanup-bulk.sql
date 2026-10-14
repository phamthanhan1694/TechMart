-- Xoa tap du lieu load test, tra DB ve dung du lieu demo.
--
-- Dung:
--   docker exec -i techmart-postgres psql -U techmart -d techmart < loadtest/cleanup-bulk.sql
--
-- An toan: chi dong nao co slug bat dau bang 'loadtest-' moi bi xoa, nen du lieu demo
-- (345 san pham that) khong bi anh huong. Cac dong nay chua tung duoc dat hang nen
-- khong co order_items tham chieu toi - day la ly do xoa cung duoc o day, khac voi
-- san pham that phai xoa mem.

\timing on

DELETE FROM product_variants v
 USING products p
 WHERE v.product_id = p.id
   AND p.slug LIKE 'loadtest-%';

DELETE FROM product_images i
 USING products p
 WHERE i.product_id = p.id
   AND p.slug LIKE 'loadtest-%';

DELETE FROM products WHERE slug LIKE 'loadtest-%';

VACUUM ANALYZE products;
VACUUM ANALYZE product_variants;

SELECT count(*) AS san_pham_con_lai FROM products;
SELECT count(*) AS variant_con_lai FROM product_variants;
