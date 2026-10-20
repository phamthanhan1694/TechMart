package dev.techmart.seed;

import dev.techmart.catalog.domain.*;
import dev.techmart.catalog.repository.BrandRepository;
import dev.techmart.catalog.repository.CategoryRepository;
import dev.techmart.catalog.repository.ProductRepository;
import dev.techmart.common.config.AppProperties;
import dev.techmart.identity.domain.Role;
import dev.techmart.identity.domain.User;
import dev.techmart.identity.repository.UserRepository;
import dev.techmart.order.domain.Order;
import dev.techmart.order.domain.OrderItem;
import dev.techmart.order.domain.OrderStatus;
import dev.techmart.order.repository.OrderRepository;
import dev.techmart.promotion.domain.Coupon;
import dev.techmart.promotion.domain.DiscountType;
import dev.techmart.flashsale.domain.FlashSale;
import dev.techmart.flashsale.domain.FlashSaleItem;
import dev.techmart.flashsale.repository.FlashSaleRepository;
import dev.techmart.flashsale.service.FlashSaleStockCounter;
import dev.techmart.promotion.repository.CouponRepository;
import dev.techmart.review.domain.Review;
import dev.techmart.review.repository.ReviewRepository;
import dev.techmart.warranty.domain.ProductSerial;
import dev.techmart.warranty.repository.ProductSerialRepository;
import dev.techmart.seed.SeedCatalog.LaptopSeed;
import dev.techmart.seed.SeedCatalog.PhoneSeed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Dựng dữ liệu demo.
 *
 * <p>Không đặt trong migration Flyway: migration là schema, chạy ở mọi môi trường kể cả
 * production, còn dữ liệu demo thì không nên có ở production. Tách ra đây để bật/tắt
 * bằng một biến môi trường (docs/08-seed-data.md mục 5).
 *
 * <p>Idempotent: đã có dữ liệu thì bỏ qua, chạy lại không nhân đôi.
 */
@Component
@Profile({"dev", "demo"})
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final int HISTORY_DAYS = 90;
    private static final int HISTORICAL_ORDERS = 400;
    private static final int LOAD_TEST_USERS = 500;

    private final BrandRepository brands;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final UserRepository users;
    private final OrderRepository orders;
    private final CouponRepository coupons;
    private final FlashSaleRepository flashSales;
    private final FlashSaleStockCounter stockCounter;
    private final ProductSerialRepository serials;
    private final ReviewRepository reviews;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    @PersistenceContext
    private EntityManager em;

    private final Random random = new Random(20260901L);   // cố định seed -> dữ liệu tái lập được

    public DataSeeder(BrandRepository brands, CategoryRepository categories, ProductRepository products,
                      UserRepository users, OrderRepository orders, CouponRepository coupons,
                      FlashSaleRepository flashSales, FlashSaleStockCounter stockCounter,
                      ProductSerialRepository serials, ReviewRepository reviews,
                      PasswordEncoder passwordEncoder, AppProperties props,
                      NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.brands = brands;
        this.categories = categories;
        this.products = products;
        this.users = users;
        this.orders = orders;
        this.coupons = coupons;
        this.flashSales = flashSales;
        this.stockCounter = stockCounter;
        this.serials = serials;
        this.reviews = reviews;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!props.seed().enabled()) {
            log.info("app.seed.enabled=false -> bỏ qua seed dữ liệu demo");
            return;
        }
        if (products.count() > 0) {
            log.info("Đã có dữ liệu, bỏ qua seed");
            return;
        }
        long start = System.currentTimeMillis();

        Map<String, Brand> brandByName = seedBrands();
        Map<String, Category> categoryBySlug = seedCategories();
        List<ProductVariant> allVariants = seedProducts(brandByName, categoryBySlug);
        List<User> customers = seedUsers();
        seedCoupons();
        seedFlashSales(allVariants);
        seedHistoricalOrders(customers, allVariants);
        seedSerialsAndReviews(allVariants);
        enrichForAnalytics();

        verifyInvariants();
        log.info("Seed xong trong {} ms: {} sản phẩm, {} variant, {} user, {} đơn hàng, "
                        + "{} mã giảm giá, {} phiên flash sale, {} serial, {} đánh giá",
                System.currentTimeMillis() - start, products.count(), allVariants.size(),
                users.count(), orders.count(), coupons.count(), flashSales.count(),
                serials.count(), reviews.count());
    }

    // ---------------------------------------------------------------- catalog

    private Map<String, Brand> seedBrands() {
        Map<String, Brand> result = new LinkedHashMap<>();
        for (String name : SeedCatalog.BRANDS) {
            result.put(name, brands.save(new Brand(name, slugify(name))));
        }
        return result;
    }

    private Map<String, Category> seedCategories() {
        Map<String, Category> result = new LinkedHashMap<>();
        Category phone = categories.save(new Category("Điện thoại", "dien-thoai", null));
        Category laptop = categories.save(new Category("Laptop", "laptop", null));
        result.put("dien-thoai", phone);
        result.put("laptop", laptop);

        for (String child : List.of("Flagship", "Tầm trung", "Phổ thông")) {
            result.put(slugify("điện thoại " + child),
                    categories.save(new Category(child, slugify("dien-thoai-" + child), phone)));
        }
        for (String child : List.of("Gaming", "Văn phòng", "Đồ hoạ")) {
            result.put(slugify("laptop " + child),
                    categories.save(new Category(child, slugify("laptop-" + child), laptop)));
        }
        return result;
    }

    private List<ProductVariant> seedProducts(Map<String, Brand> brandByName, Map<String, Category> catBySlug) {
        List<ProductVariant> allVariants = new ArrayList<>();
        Set<String> usedSlugs = new HashSet<>();
        int sku = 1000;

        // Điện thoại: mỗi hạt giống là MỘT sản phẩm có thật, không nhân hậu tố.
        // Phiên bản sinh từ các mức dung lượng thật của chính máy đó.
        for (PhoneSeed seed : SeedCatalog.PHONES) {
            String slug = uniqueSlug(seed.name(), usedSlugs);
            Category category = catBySlug.get(categoryForPrice(seed.basePrice(), true));

            Product product = new Product(category, brandByName.get(seed.brand()), seed.name(), slug,
                    "Điện thoại " + seed.brand() + " " + seed.name() + ". Chip " + seed.chipset()
                            + ", màn hình " + seed.screen() + " " + seed.refreshRate()
                            + ", RAM " + seed.ram() + ", pin " + seed.battery() + ".",
                    null, SeedCatalog.phoneSpec(seed, seed.storages().get(0)));

            for (int i = 0; i < seed.storages().size(); i++) {
                String storage = seed.storages().get(i);
                String color = seed.colors().get(i % seed.colors().size());
                long price = seed.basePrice() + i * seed.storageStep();
                ProductVariant v = new ProductVariant("SKU-P-" + (sku++),
                        storage + " - " + color, money(price), money(price + listPriceGap(price)),
                        10 + random.nextInt(90), Map.of("color", color, "storage", storage));
                product.addVariant(v);
                allVariants.add(v);
            }
            products.save(product);
        }

        // Laptop: phiên bản = các cấu hình RAM/SSD thật của máy đó.
        for (LaptopSeed seed : SeedCatalog.LAPTOPS) {
            String slug = uniqueSlug(seed.name(), usedSlugs);
            Category category = catBySlug.get(categoryForLaptop(seed));

            Product product = new Product(category, brandByName.get(seed.brand()), seed.name(), slug,
                    "Laptop " + seed.brand() + " " + seed.name() + ". CPU " + seed.cpu()
                            + ", GPU " + seed.gpu() + ", màn hình " + seed.screen()
                            + ", nặng " + seed.weight() + ".",
                    null, SeedCatalog.laptopSpec(seed, seed.rams().get(0), seed.ssds().get(0)));

            int config = 0;
            for (String ram : seed.rams()) {
                for (String ssd : seed.ssds()) {
                    String color = seed.colors().get(config % seed.colors().size());
                    long price = seed.basePrice() + config * seed.configStep();
                    ProductVariant v = new ProductVariant("SKU-L-" + (sku++),
                            ram + " / " + ssd + " - " + color, money(price),
                            money(price + listPriceGap(price)),
                            5 + random.nextInt(50), Map.of("color", color, "ram", ram, "storage", ssd));
                    product.addVariant(v);
                    allVariants.add(v);
                    config++;
                }
            }
            products.save(product);
        }
        return allVariants;
    }

    // ---------------------------------------------------------------- users

    private List<User> seedUsers() {
        users.save(new User("admin@techmart.dev", passwordEncoder.encode("Admin@123"),
                "Quản trị viên", "0900000001", Role.ADMIN));
        users.save(new User("staff@techmart.dev", passwordEncoder.encode("Staff@123"),
                "Nhân viên kho", "0900000002", Role.STAFF));

        // Tài khoản này PHẢI có sẵn đơn hàng: interviewer đăng nhập, gọi GET /orders
        // mà trả mảng rỗng thì mọi endpoint phía sau đều mất ý nghĩa.
        User demo = users.save(new User("customer@techmart.dev", passwordEncoder.encode("Customer@123"),
                "Khách hàng demo", "0901234567", Role.CUSTOMER));

        users.save(new User("new@techmart.dev", passwordEncoder.encode("Customer@123"),
                "Khách hàng mới", "0901234568", Role.CUSTOMER));

        List<User> customers = new ArrayList<>();
        customers.add(demo);

        // BCrypt strength 12 tốn ~250ms mỗi lần hash. Hash riêng cho 500 user là ~2 phút,
        // quá lâu cho một bước seed - và giữ connection đủ lâu để Hikari cảnh báo leak.
        // 500 user này dùng chung một mật khẩu nên hash MỘT lần rồi dùng lại.
        // Chỉ làm vậy với dữ liệu demo: các user này sẽ chung salt, không được làm ở môi trường thật.
        String sharedHash = passwordEncoder.encode("Customer@123");
        for (int i = 1; i <= LOAD_TEST_USERS; i++) {
            customers.add(users.save(new User("buyer" + i + "@techmart.dev",
                    sharedHash, "Khách hàng " + i,
                    "09" + String.format("%08d", i), Role.CUSTOMER)));
        }
        return customers;
    }

    // ---------------------------------------------------------------- coupon

    /** Đủ mọi tình huống để demo và test được từng nhánh lỗi, không chỉ nhánh hợp lệ. */
    private void seedCoupons() {
        Instant now = clock.instant();
        Instant past = now.minus(60, ChronoUnit.DAYS);
        Instant future = now.plus(60, ChronoUnit.DAYS);

        coupons.save(new Coupon("SALE10", DiscountType.PERCENT, new BigDecimal("10.00"),
                new BigDecimal("5000000.00"), new BigDecimal("2000000.00"), 1000, 1, past, future));
        coupons.save(new Coupon("GIAM500K", DiscountType.FIXED, new BigDecimal("500000.00"),
                new BigDecimal("10000000.00"), null, 500, 2, past, future));
        coupons.save(new Coupon("FREESHIP", DiscountType.FIXED, new BigDecimal("30000.00"),
                BigDecimal.ZERO, null, null, 5, past, future));
        coupons.save(new Coupon("NEWUSER20", DiscountType.PERCENT, new BigDecimal("20.00"),
                BigDecimal.ZERO, new BigDecimal("1000000.00"), 200, 1, past, future));

        // Các mã "hỏng" có chủ đích: để demo từng nhánh lỗi
        Coupon expired = new Coupon("HETHAN", DiscountType.PERCENT, new BigDecimal("15.00"),
                BigDecimal.ZERO, null, 100, 1, past, now.minus(1, ChronoUnit.DAYS));
        coupons.save(expired);
        coupons.save(new Coupon("CHUABATDAU", DiscountType.PERCENT, new BigDecimal("25.00"),
                BigDecimal.ZERO, null, 100, 1, now.plus(7, ChronoUnit.DAYS), future));
        // usageLimit = 1: đúng cho test 50 người tranh một lượt
        coupons.save(new Coupon("CHIMOTLUOT", DiscountType.FIXED, new BigDecimal("1000000.00"),
                BigDecimal.ZERO, null, 1, 1, past, future));
        coupons.save(new Coupon("DONTOITHIEU50TR", DiscountType.PERCENT, new BigDecimal("30.00"),
                new BigDecimal("50000000.00"), new BigDecimal("5000000.00"), 50, 1, past, future));
    }

    // ---------------------------------------------------------------- flash sale

    /**
     * Ba phiên ở ba trạng thái khác nhau - để demo được cả /active lẫn /upcoming
     * mà không phải chờ đổi giờ.
     *
     * <p>Phiên đang chạy được nạp sẵn tồn kho vào Redis luôn: nếu để request đầu tiên
     * tự nạp thì đúng giây mở bán sẽ có hàng nghìn request cùng nạp - cache stampede.
     */
    /**
     * Lịch flash sale kiểu cửa hàng thật: mỗi ngày một khung giờ, không phải ba dòng cố định.
     *
     * <p><b>Lỗi của bản trước:</b> chỉ có đúng một phiên RUNNING với khung
     * {@code now-1h -> now+6h}. Sau 6 giờ kể từ lúc seed là trang flash sale trống trơn,
     * và nó không bao giờ tự đầy lại - mở demo hôm sau chỉ thấy một trang rỗng.
     *
     * <p>Bây giờ: 3 ngày đã qua (để dashboard có lịch sử), một phiên đang chạy kéo dài
     * hết ngày, và 7 ngày sắp tới theo khung 09:00-23:00 giờ Việt Nam. Muốn thêm nữa thì
     * tạo ở khu quản trị - trong hệ thống thật, khuyến mại do người làm thương mại lên
     * lịch chứ không phải do cron tự đẻ ra.
     */
    private void seedFlashSales(List<ProductVariant> allVariants) {
        Instant now = clock.instant();
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate today = now.atZone(zone).toLocalDate();

        // --- 3 ngày đã kết thúc: hàng bán gần hết, để báo cáo có số liệu thật ---
        for (int d = 3; d >= 1; d--) {
            LocalDate day = today.minusDays(d);
            FlashSale sale = new FlashSale("Flash Sale " + day.getDayOfMonth() + "/" + day.getMonthValue(),
                    day.atTime(9, 0).atZone(zone).toInstant(),
                    day.atTime(23, 0).atZone(zone).toInstant());
            addFlashItems(sale, allVariants, d * 13, 4);
            // Phiên đã kết thúc thì phải đã bán được gì đó - 70-100% số suất.
            sale.getItems().forEach(item ->
                    item.recordSold(percentOf(item.getSaleQuantity(), 70 + random.nextInt(31))));
            sale.markEnded();
            flashSales.save(sale);
        }

        // --- Đang chạy: kéo dài đến hết ngày hôm nay, không phải 6 tiếng ---
        FlashSale running = new FlashSale("Flash Sale hôm nay",
                now.minus(1, ChronoUnit.HOURS),
                today.atTime(23, 59).atZone(zone).toInstant());
        addFlashItems(running, allVariants, 5, 6);
        // Bán sẵn 20-60% để thanh tiến độ không nằm im ở 0% ngay từ đầu.
        running.getItems().forEach(item ->
                item.recordSold(percentOf(item.getSaleQuantity(), 20 + random.nextInt(41))));
        running.markRunning();
        FlashSale savedRunning = flashSales.save(running);
        // Nạp Redis sau khi đã ghi số đã bán, vì warmUp đọc remaining() = saleQuantity - soldQuantity.
        savedRunning.getItems().forEach(stockCounter::warmUp);

        // --- 7 ngày sắp tới ---
        for (int d = 1; d <= 7; d++) {
            LocalDate day = today.plusDays(d);
            FlashSale sale = new FlashSale("Flash Sale " + day.getDayOfMonth() + "/" + day.getMonthValue(),
                    day.atTime(9, 0).atZone(zone).toInstant(),
                    day.atTime(23, 0).atZone(zone).toInstant());
            addFlashItems(sale, allVariants, 40 + d * 11, 5);
            FlashSale saved = flashSales.save(sale);

            // Nạp Redis cho cả các phiên SẮP TỚI, không chỉ phiên đang chạy.
            //
            // `findActive` lọc THUẦN theo khung giờ, không theo status - nên đúng 09:00
            // ngày mai là phiên đó tự lên sóng mà không cần ai bấm gì. Nhưng nếu Redis
            // chưa được nạp thì script Lua trả NOT_WARMED_UP và mỗi lần mua đều 503
            // (fail-closed): phiên hiện ra nhưng không ai mua được.
            //
            // Key tồn kho không đặt TTL nên nạp trước bây giờ vẫn còn nguyên đến lúc mở bán.
            saved.getItems().forEach(stockCounter::warmUp);
        }
    }

    /**
     * Chọn sản phẩm cho một phiên mở bán.
     *
     * <p>Mức giảm thay đổi 12-40% chứ không phải 30% đồng loạt: khuyến mại thật không
     * bao giờ đều tăm tắp như vậy, và đều tăm tắp thì nhìn ra ngay là dữ liệu bịa.
     */
    private void addFlashItems(FlashSale sale, List<ProductVariant> allVariants, int offset, int count) {
        for (int i = 0; i < count; i++) {
            ProductVariant variant = allVariants.get((offset + i * 7) % allVariants.size());

            int discountPercent = 12 + random.nextInt(29);            // 12-40%
            long raw = variant.getPrice()
                    .multiply(BigDecimal.valueOf(100 - discountPercent))
                    .divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.HALF_UP)
                    .longValue();
            BigDecimal salePrice = money(retailRound(raw));

            // Số suất ít hơn hẳn tồn kho - đó mới là lý do phải tranh nhau.
            int saleQuantity = Math.max(5, Math.min(60, variant.availableQuantity() / 2));
            sale.addItem(new FlashSaleItem(variant, salePrice, saleQuantity, 2));
        }
    }

    private int percentOf(int total, int percent) {
        return Math.max(0, Math.min(total, total * percent / 100));
    }

    /**
     * Làm tròn về mức giá bán lẻ: 24.833.100 -> 24.790.000.
     *
     * <p>Không cửa hàng nào niêm yết 24.833.100 đ. Giá thực tế luôn kết thúc bằng
     * 90.000 hoặc 00.000 - để nguyên số lẻ sinh ra từ phép nhân phần trăm là lộ
     * ngay rằng giá do máy tính tạo ra tạo ra.
     */
    private static long retailRound(long amount) {
        long rounded = Math.round(amount / 100_000.0) * 100_000L - 10_000L;
        return Math.max(100_000L, rounded);
    }

    // ---------------------------------------------------------------- lịch sử đơn hàng

    /**
     * Đơn hàng rải theo 90 ngày, mật độ tăng dần về hiện tại, cuối tuần cao hơn.
     *
     * <p>Đây là phần hay bị bỏ sót nhất: không có dữ liệu lịch sử thì dashboard doanh thu
    thu đường thang bằng 0 và mất luôn cơ hội demo query aggregate.
     */
    private void seedHistoricalOrders(List<User> customers, List<ProductVariant> variants) {
        Instant now = clock.instant();
        User demoCustomer = customers.get(0);

        for (int i = 0; i < HISTORICAL_ORDERS; i++) {
            // Mật độ tăng TUYẾN TÍNH về hiện tại (shop đang lớn): mật độ ~ (1 - daysAgo/90).
            // Bản trước dùng random^2.2 - hàm mật độ đổ vọt lên vô hạn ở 0, nên ~13% số đơn
            // (≈52 đơn) dồn vào đúng một ngày và biểu đồ doanh thu có một cột dựng đứng giả.
            int daysAgo = (int) (HISTORY_DAYS * (1 - Math.sqrt(random.nextDouble())));
            // Giờ đặt theo nhịp mua sắm thật (trưa và tối), không rải đều 24 giờ -
            // để bản đồ nhiệt ở trang thống kê có hình dạng đáng xem.
            ZoneId zone = props.reporting().zone();
            Instant placedAt = LocalDate.ofInstant(now, zone).minusDays(daysAgo)
                    .atTime(weightedHour(), random.nextInt(60))
                    .atZone(zone).toInstant();
            if (placedAt.isAfter(now)) {
                // Giờ rơi vào tương lai của hôm nay: rải lại trong khoảng từ nửa đêm tới giờ.
                // Lùi một ngày thì mọi đơn "chiều nay" dồn sang hôm qua.
                Instant midnight = LocalDate.ofInstant(now, zone).atStartOfDay(zone).toInstant();
                long span = Math.max(1, now.toEpochMilli() - midnight.toEpochMilli());
                placedAt = midnight.plusMillis((long) (random.nextDouble() * span));
            }

            // 15 đơn đầu dành cho tài khoản demo để nó luôn có lịch sử
            User buyer = i < 15 ? demoCustomer : customers.get(random.nextInt(customers.size()));

            Order order = new Order(orderCode(placedAt, i), buyer, shippingAddress(),
                    null, "seed-" + i, placedAt);

            int lineCount = 1 + random.nextInt(3);
            for (int l = 0; l < lineCount; l++) {
                ProductVariant variant = variants.get(random.nextInt(variants.size()));
                int quantity = 1 + random.nextInt(2);
                order.addItem(new OrderItem(variant, quantity));
            }
            order.recalculateTotals(new BigDecimal("30000.00"), BigDecimal.ZERO, null);

            OrderStatus status = randomStatus();
            replayStatusChain(order, status, placedAt, buyer.getId());

            // Đơn không bị huỷ thì hàng đã rời kho -> trừ tồn cho nhất quán
            if (status != OrderStatus.CANCELLED) {
                order.getItems().forEach(item -> {
                    try {
                        item.getVariant().deduct(item.getQuantity());
                    } catch (IllegalStateException e) {
                        item.getVariant().restock(item.getQuantity());   // hết hàng thì bù lại
                    }
                });
            }
            orders.save(order);
        }
    }

    /** Ghi lại đầy đủ đường đi trạng thái để GET /orders/{code}/history demo được tử tế. */
    private void replayStatusChain(Order order, OrderStatus target, Instant placedAt, Long actorId) {
        List<OrderStatus> chain = switch (target) {
            case PENDING_PAYMENT -> List.of();
            case CANCELLED -> List.of(OrderStatus.CANCELLED);
            case PAID -> List.of(OrderStatus.PAID);
            case PACKED -> List.of(OrderStatus.PAID, OrderStatus.PACKED);
            case SHIPPED -> List.of(OrderStatus.PAID, OrderStatus.PACKED, OrderStatus.SHIPPED);
            default -> List.of(OrderStatus.PAID, OrderStatus.PACKED,
                    OrderStatus.SHIPPED, OrderStatus.DELIVERED);
        };
        order.logTransition(null, OrderStatus.PENDING_PAYMENT, actorId, "Đặt hàng", placedAt);

        OrderStatus from = OrderStatus.PENDING_PAYMENT;
        Instant at = placedAt;
        for (OrderStatus to : chain) {
            at = at.plus(4 + random.nextInt(20), ChronoUnit.HOURS);
            order.logTransition(from, to, actorId, null, at);
            order.applyStatus(to, at);
            from = to;
        }
    }

    private OrderStatus randomStatus() {
        int roll = random.nextInt(100);
        if (roll < 70) return OrderStatus.DELIVERED;
        if (roll < 80) return OrderStatus.SHIPPED;
        if (roll < 88) return OrderStatus.PAID;
        if (roll < 95) return OrderStatus.CANCELLED;
        return OrderStatus.PENDING_PAYMENT;
    }

    /** Trọng số theo giờ trong ngày (giờ Việt Nam): vắng lúc 2-5h sáng, đông lúc trưa và 19-22h. */
    private static final int[] HOUR_WEIGHTS = {
            2, 1, 1, 1, 1, 1, 2, 4, 6, 8, 9, 11, 12, 10, 8, 8, 8, 9, 10, 13, 15, 15, 11, 6};

    private int weightedHour() {
        int total = 0;
        for (int w : HOUR_WEIGHTS) total += w;
        int roll = random.nextInt(total);
        for (int h = 0; h < HOUR_WEIGHTS.length; h++) {
            roll -= HOUR_WEIGHTS[h];
            if (roll < 0) return h;
        }
        return 20;
    }

    // ---------------------------------------------------------------- làm giàu cho trang thống kê

    /**
     * Bổ sung dữ liệu mà trang thống kê cần nhưng luồng seed cũ không sinh ra: giao dịch thanh
     * toán, đơn có dùng mã giảm giá, ngày đăng ký rải theo thời gian, cùng sổ địa chỉ / yêu
     * thích / thông báo cho tài khoản demo.
     *
     * <p>Viết bằng SQL hàng loạt thay vì dựng entity: vài trăm dòng trong vài chục ms, và không
     * phải dựng lại toàn bộ luồng thanh toán chỉ để có dữ liệu quá khứ. {@code setseed} cố định
     * {@code random()} của PostgreSQL để dữ liệu tái lập được giữa các lần seed.
     *
     * <p>Tài khoản demo KHÔNG được gán mã giảm giá: người thử demo nhập SALE10 mà bị báo
     * "đã dùng hết lượt" là một trải nghiệm demo hỏng.
     */
    private void enrichForAnalytics() {
        em.flush();
        em.clear();
        var sql = jdbc.getJdbcOperations();
        sql.execute("SELECT setseed(0.20260925)");

        // 1. Mã giảm giá trên ~18% đơn, mỗi khách dùng mỗi mã tối đa một lần
        sql.update("""
                WITH picked AS (
                    SELECT o.id, o.user_id, o.subtotal,
                           (ARRAY['SALE10','GIAM500K','FREESHIP','NEWUSER20'])[1 + floor(random() * 4)::int] AS code
                      FROM orders o JOIN users u ON u.id = o.user_id
                     WHERE o.idempotency_key LIKE 'seed-%'
                       AND u.email NOT IN ('customer@techmart.dev', 'new@techmart.dev')
                       AND random() < 0.18
                ), priced AS (
                    SELECT DISTINCT ON (p.user_id, c.code) p.id, c.code,
                           CASE WHEN c.type = 'PERCENT'
                                THEN LEAST(ROUND(p.subtotal * c.value / 100, 2), COALESCE(c.max_discount, p.subtotal))
                                ELSE LEAST(c.value, p.subtotal) END AS discount
                      FROM picked p JOIN coupons c ON c.code = p.code
                     WHERE p.subtotal >= c.min_order_amount
                     ORDER BY p.user_id, c.code, p.id
                )
                UPDATE orders o
                   SET coupon_code = pr.code,
                       discount_amount = pr.discount,
                       total_amount = GREATEST(o.subtotal + o.shipping_fee - pr.discount, 0)
                  FROM priced pr
                 WHERE o.id = pr.id
                """);
        // Đơn huỷ đã được hoàn lượt mã - giống hệt luồng thật (CouponService.releaseForOrder)
        sql.update("""
                INSERT INTO coupon_redemptions (coupon_id, user_id, order_id, created_at)
                SELECT c.id, o.user_id, o.id, o.placed_at
                  FROM orders o JOIN coupons c ON c.code = o.coupon_code
                 WHERE o.idempotency_key LIKE 'seed-%' AND o.status <> 'CANCELLED'
                """);
        sql.update("UPDATE coupons c SET used_count = (SELECT COUNT(*) FROM coupon_redemptions r WHERE r.coupon_id = c.id)");

        // 2. Giao dịch thanh toán: thành công đúng lúc đơn sang PAID; thất bại cho đơn chưa trả,
        //    đơn huỷ và ~7% đơn thẻ phải thử lần hai
        sql.update("""
                INSERT INTO payments (order_id, method, status, amount, transaction_ref, idempotency_key, paid_at, created_at)
                SELECT o.id, CASE WHEN random() < 0.35 THEN 'COD' ELSE 'MOCK_CARD' END, 'SUCCEEDED',
                       o.total_amount, 'SEED-TX-' || o.id, 'seed-pay-' || o.id, h.created_at, h.created_at
                  FROM orders o
                  JOIN order_status_history h ON h.order_id = o.id AND h.to_status = 'PAID'
                 WHERE o.idempotency_key LIKE 'seed-%'
                """);
        sql.update("""
                INSERT INTO payments (order_id, method, status, amount, transaction_ref, idempotency_key,
                                      failure_reason, created_at)
                SELECT o.id, 'MOCK_CARD', 'FAILED', o.total_amount, 'SEED-TXF-' || o.id, 'seed-payf-' || o.id,
                       'Thẻ bị từ chối', o.placed_at + interval '5 minutes'
                  FROM orders o
                 WHERE o.idempotency_key LIKE 'seed-%'
                   AND (o.status IN ('PENDING_PAYMENT', 'CANCELLED') OR random() < 0.07)
                """);

        // 3. Ngày đăng ký rải trong 150 ngày, luôn TRƯỚC đơn đầu tiên của khách
        sql.update("""
                UPDATE users u
                   SET created_at = LEAST(
                           now() - (random() * 150) * interval '1 day',
                           COALESCE((SELECT MIN(o.placed_at) FROM orders o WHERE o.user_id = u.id) - interval '1 day',
                                    'infinity'::timestamptz))
                 WHERE u.role = 'CUSTOMER' AND u.email <> 'new@techmart.dev'
                """);

        // 4. Tài khoản demo: sổ địa chỉ, yêu thích, vài thông báo để header có chuông đỏ
        Long demoId = jdbc.queryForObject("SELECT id FROM users WHERE email = 'customer@techmart.dev'",
                Map.of(), Long.class);
        sql.update("""
                INSERT INTO user_addresses (user_id, label, receiver_name, phone, street, ward, district, province, is_default)
                VALUES (?, 'Nhà riêng', 'Khách hàng demo', '0901234567', '12 Nguyễn Trãi', 'Phường Bến Thành', 'Quận 1', 'TP. Hồ Chí Mình', TRUE),
                       (?, 'Công ty', 'Khách hàng demo', '0901234567', '285 Cách Mạng Tháng Tám', 'Phường 12', 'Quận 10', 'TP. Hồ Chí Mình', FALSE)
                """, demoId, demoId);
        sql.update("""
                INSERT INTO wishlist_items (user_id, product_id)
                SELECT ?, p.id FROM products p WHERE p.deleted_at IS NULL ORDER BY p.id LIMIT 4
                """, demoId);
        sql.update("""
                INSERT INTO notifications (user_id, type, title, body, link, read_at, created_at)
                SELECT ?, 'ORDER_DELIVERED', 'Giao hàng thành công',
                       'Đơn ' || o.code || ' đã giao xong. Đánh giá sản phẩm để giúp người mua sau nhé!',
                       '/orders/' || o.code,
                       CASE WHEN row_number() OVER (ORDER BY o.delivered_at DESC) > 2 THEN LEAST(o.delivered_at, now()) END,
                       -- Chuỗi trạng thái seed cộng giờ sau lúc đặt, nên đơn rất gần đây có
                       -- delivered_at ở tương lai; thông báo không được "đến từ tương lai".
                       LEAST(o.delivered_at, now())
                  FROM orders o
                 WHERE o.user_id = ? AND o.status = 'DELIVERED'
                 ORDER BY o.delivered_at DESC
                 LIMIT 5
                """, demoId, demoId);
    }

    // ---------------------------------------------------------------- serial + đánh giá

    /**
     * Serial cho một phần variant, và đánh giá cho các đơn đã giao.
     *
     * <p>Chỉ một phần variant có serial - đúng như thực tế: không phải mặt hàng nào cũng
     * được theo dõi theo từng đơn vị. Phần còn lại chỉ quản lý theo số lượng.
     */
    private void seedSerialsAndReviews(List<ProductVariant> allVariants) {
        Instant now = clock.instant();

        // 20 variant đầu tiên được theo dõi theo serial
        List<ProductVariant> tracked = allVariants.subList(0, Math.min(20, allVariants.size()));
        Map<Long, List<ProductSerial>> pool = new LinkedHashMap<>();
        for (ProductVariant variant : tracked) {
            List<ProductSerial> created = new ArrayList<>();
            for (int i = 1; i <= 15; i++) {
                created.add(new ProductSerial(variant,
                        "IMEI" + String.format("%06d", variant.getId()) + String.format("%04d", i), 12));
            }
            pool.put(variant.getId(), new ArrayList<>(serials.saveAll(created)));
        }

        // Gắn serial cho các đơn ĐÃ GIAO, và viết đánh giá cho một phần trong số đó
        int reviewCount = 0;
        for (Order order : orders.findAll()) {
            if (order.getStatus() != OrderStatus.DELIVERED) {
                continue;
            }
            for (OrderItem item : order.getItems()) {
                List<ProductSerial> available = pool.get(item.getVariant().getId());
                if (available != null) {
                    for (int i = 0; i < item.getQuantity() && !available.isEmpty(); i++) {
                        available.remove(0).assignTo(item, order.getPlacedAt().plus(2, ChronoUnit.DAYS));
                    }
                }
                // Khoảng một nửa đơn đã giao có đánh giá
                if (random.nextBoolean() && reviewCount < 250) {
                    reviews.save(new Review(item.getVariant().getProduct(), order.getUser(), item,
                            randomRating(), pick(REVIEW_COMMENTS)));
                    reviewCount++;
                }
            }
        }
        log.info("Đã tạo {} serial và {} đánh giá", serials.count(), reviewCount);
    }

    /**
     * Điểm đánh giá lệch về phía tích cực, giống dữ liệu thật: người hài lòng thì cho
     * 4-5 sao, người rất không hài lòng mới bỏ công viết 1 sao. Phân bố đều 1-5 cho ra
     * trung bình 3.0 - trông không giống một cửa hàng thật đang hoạt động.
     */
    private int randomRating() {
        int roll = random.nextInt(100);
        if (roll < 55) return 5;
        if (roll < 80) return 4;
        if (roll < 92) return 3;
        if (roll < 97) return 2;
        return 1;
    }

    private static final List<String> REVIEW_COMMENTS = List.of(
            "Máy dùng tốt, pin trâu hơn mình nghĩ.",
            "Giao hàng nhanh, đóng gói cẩn thận.",
            "Màn hình đẹp, nhưng hơi nóng khi chơi game lâu.",
            "Dùng được, giá hợp lý so với cấu hình.",
            "Âm thanh nhỏ hơn mong đợi, còn lại ổn.",
            "Rất hài lòng, sẽ quay lại mua lần sau.",
            "Hàng chính hãng, có đầy đủ phụ kiện.",
            "Sạc hơi chậm nhưng không đáng kể.");

    // ---------------------------------------------------------------- kiểm tra

    /**
     * Đối soát sau khi seed. Dữ liệu demo tự mâu thuẫn thì người tinh ý sẽ thấy ngay,
     * và đây cũng là bản nháp của job đối soát nói ở docs/01-domain-v2.md mục 5.1.
     */
    private void verifyInvariants() {
        long negative = products.findAll().stream()
                .flatMap(p -> p.getVariants().stream())
                .filter(v -> v.getStockQuantity() < 0 || v.getReservedQuantity() != 0)
                .count();
        if (negative > 0) {
            throw new IllegalStateException("Seed sai: " + negative + " variant có tồn kho âm hoặc đang bị giữ");
        }
    }

    // ---------------------------------------------------------------- tiện ích

    /**
     * Khoảng chênh giữa giá niêm yết và giá bán, để hiện "giá gạch ngang".
     *
     * <p>Dùng tỉ lệ chứ không phải số cố định: bớt 2 triệu trên máy 7 triệu là giảm
     * 29% (không hợp lý cho hàng chính hãng), còn trên máy 60 triệu thì không ai
     * để ý. Khoảng 6-9% mới giống khuyến mại thật.
     */
    private long listPriceGap(long price) {
        long percent = 6 + random.nextInt(4);          // 6-9%
        long gap = price * percent / 100;
        return Math.round(gap / 100_000.0) * 100_000L; // làm tròn về trăm nghìn
    }

    private String orderCode(Instant at, int index) {
        return "TM-" + at.toString().substring(0, 10).replace("-", "") + "-S" + String.format("%05d", index);
    }

    private Map<String, Object> shippingAddress() {
        List<String> districts = List.of("Quận 1", "Quận 3", "Quận 7", "Cầu Giấy", "Đống Đa", "Hải Châu");
        List<String> provinces = List.of("TP Hồ Chí Minh", "Hà Nội", "Đà Nẵng");
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("receiverName", "Người nhận " + (1 + random.nextInt(500)));
        address.put("phone", "09" + String.format("%08d", random.nextInt(100_000_000)));
        address.put("street", (1 + random.nextInt(200)) + " Đường số " + (1 + random.nextInt(30)));
        address.put("ward", "Phường " + (1 + random.nextInt(20)));
        address.put("district", pick(districts));
        address.put("province", pick(provinces));
        return address;
    }

    private String categoryForPrice(long price, boolean phone) {
        if (!phone) return "laptop";
        if (price >= 20_000_000L) return "dien-thoai-flagship";
        if (price >= 10_000_000L) return "dien-thoai-tam-trung";
        return "dien-thoai-pho-thong";
    }

    private String categoryForLaptop(LaptopSeed seed) {
        if (seed.gpu().contains("RTX")) return "laptop-gaming";
        if (seed.basePrice() >= 35_000_000L) return "laptop-do-hoa";
        return "laptop-van-phong";
    }

    private <T> T pick(List<T> list) {
        return list.get(random.nextInt(list.size()));
    }

    private long jitter() {
        return random.nextInt(20) * 100_000L;
    }

    private static BigDecimal money(long amount) {
        return BigDecimal.valueOf(amount).setScale(2);
    }

    private static String uniqueSlug(String name, Set<String> used) {
        String base = slugify(name);
        String slug = base;
        int n = 2;
        while (!used.add(slug)) {
            slug = base + "-" + n++;
        }
        return slug;
    }

    private static String slugify(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isEmpty() ? "item" : normalized;
    }
}
