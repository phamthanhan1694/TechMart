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
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Dung du lieu demo.
 *
 * <p>Khong dat trong migration Flyway: migration la schema, chay o moi moi truong ke ca
 * production, con du lieu demo thi khong nen co o production. Tach ra day de bat/tat
 * bang mot bien moi truong (docs/08-seed-data.md muc 5).
 *
 * <p>Idempotent: da co du lieu thi bo qua, chay lai khong nhan doi.
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
    private final Clock clock;

    private final Random random = new Random(20260901L);   // co dinh seed -> du lieu tai lap duoc

    public DataSeeder(BrandRepository brands, CategoryRepository categories, ProductRepository products,
                      UserRepository users, OrderRepository orders, CouponRepository coupons,
                      FlashSaleRepository flashSales, FlashSaleStockCounter stockCounter,
                      ProductSerialRepository serials, ReviewRepository reviews,
                      PasswordEncoder passwordEncoder, AppProperties props, Clock clock) {
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
            log.info("app.seed.enabled=false -> bo qua seed du lieu demo");
            return;
        }
        if (products.count() > 0) {
            log.info("Da co du lieu, bo qua seed");
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

        verifyInvariants();
        log.info("Seed xong trong {} ms: {} san pham, {} variant, {} user, {} don hang, "
                        + "{} ma giam gia, {} phien flash sale, {} serial, {} danh gia",
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
        Category phone = categories.save(new Category("Dien thoai", "dien-thoai", null));
        Category laptop = categories.save(new Category("Laptop", "laptop", null));
        result.put("dien-thoai", phone);
        result.put("laptop", laptop);

        for (String child : List.of("Flagship", "Tam trung", "Pho thong")) {
            result.put(slugify("dien thoai " + child),
                    categories.save(new Category(child, slugify("dien-thoai-" + child), phone)));
        }
        for (String child : List.of("Gaming", "Van phong", "Do hoa")) {
            result.put(slugify("laptop " + child),
                    categories.save(new Category(child, slugify("laptop-" + child), laptop)));
        }
        return result;
    }

    private List<ProductVariant> seedProducts(Map<String, Brand> brandByName, Map<String, Category> catBySlug) {
        List<ProductVariant> allVariants = new ArrayList<>();
        Set<String> usedSlugs = new HashSet<>();
        int sku = 1000;

        // Dien thoai: 15 hat giong x cac hau to -> ~180 san pham
        for (PhoneSeed seed : SeedCatalog.PHONES) {
            for (String suffix : SeedCatalog.VARIANT_SUFFIXES.subList(0, 12)) {
                String name = seed.name() + suffix;
                String slug = uniqueSlug(name, usedSlugs);
                Category category = catBySlug.get(categoryForPrice(seed.basePrice(), true));

                String ram = pick(SeedCatalog.PHONE_RAM);
                Product product = new Product(category, brandByName.get(seed.brand()), name, slug,
                        "Dien thoai " + seed.brand() + " " + name + ". Chip " + seed.chipset()
                                + ", man hinh " + seed.screen() + ", pin " + seed.battery() + ".",
                        imageUrl(name), SeedCatalog.phoneSpec(seed, ram, SeedCatalog.PHONE_STORAGE.get(1)));

                for (int i = 0; i < 2 + random.nextInt(2); i++) {
                    String storage = SeedCatalog.PHONE_STORAGE.get(i % SeedCatalog.PHONE_STORAGE.size());
                    String color = pick(SeedCatalog.PHONE_COLORS);
                    long price = seed.basePrice() + i * 3_000_000L + jitter();
                    ProductVariant v = new ProductVariant("SKU-P-" + (sku++),
                            storage + " - " + color, money(price), money(price + 2_000_000L),
                            10 + random.nextInt(90), Map.of("color", color, "storage", storage));
                    product.addVariant(v);
                    allVariants.add(v);
                }
                addImages(product, name);
                products.save(product);
            }
        }

        // Laptop: 15 hat giong x cac hau to -> ~165 san pham
        for (LaptopSeed seed : SeedCatalog.LAPTOPS) {
            for (String suffix : SeedCatalog.VARIANT_SUFFIXES.subList(0, 11)) {
                String name = seed.name() + suffix;
                String slug = uniqueSlug(name, usedSlugs);
                Category category = catBySlug.get(categoryForLaptop(seed));

                String ram = pick(SeedCatalog.LAPTOP_RAM);
                Product product = new Product(category, brandByName.get(seed.brand()), name, slug,
                        "Laptop " + seed.brand() + " " + name + ". CPU " + seed.cpu()
                                + ", GPU " + seed.gpu() + ", man hinh " + seed.screen() + ".",
                        imageUrl(name), SeedCatalog.laptopSpec(seed, ram, SeedCatalog.LAPTOP_SSD.get(1)));

                for (int i = 0; i < 2 + random.nextInt(2); i++) {
                    String ssd = SeedCatalog.LAPTOP_SSD.get(i % SeedCatalog.LAPTOP_SSD.size());
                    String color = pick(SeedCatalog.LAPTOP_COLORS);
                    long price = seed.basePrice() + i * 5_000_000L + jitter();
                    ProductVariant v = new ProductVariant("SKU-L-" + (sku++),
                            ram + " / " + ssd + " - " + color, money(price), money(price + 3_000_000L),
                            5 + random.nextInt(50), Map.of("color", color, "ram", ram, "storage", ssd));
                    product.addVariant(v);
                    allVariants.add(v);
                }
                addImages(product, name);
                products.save(product);
            }
        }
        return allVariants;
    }

    // ---------------------------------------------------------------- users

    private List<User> seedUsers() {
        users.save(new User("admin@techmart.dev", passwordEncoder.encode("Admin@123"),
                "Quan tri vien", "0900000001", Role.ADMIN));
        users.save(new User("staff@techmart.dev", passwordEncoder.encode("Staff@123"),
                "Nhan vien kho", "0900000002", Role.STAFF));

        // Tai khoan nay PHAI co san don hang: interviewer dang nhap, goi GET /orders
        // ma tra mang rong thi moi endpoint phia sau deu mat y nghia.
        User demo = users.save(new User("customer@techmart.dev", passwordEncoder.encode("Customer@123"),
                "Khach hang demo", "0901234567", Role.CUSTOMER));

        users.save(new User("new@techmart.dev", passwordEncoder.encode("Customer@123"),
                "Khach hang moi", "0901234568", Role.CUSTOMER));

        List<User> customers = new ArrayList<>();
        customers.add(demo);

        // BCrypt strength 12 ton ~250ms moi lan hash. Hash rieng cho 500 user la ~2 phut,
        // qua lau cho mot buoc seed - va giu connection du lau de Hikari canh bao leak.
        // 500 user nay dung chung mot mat khau nen hash MOT lan roi dung lai.
        // Chi lam vay voi du lieu demo: cac user nay se chung salt, khong duoc lam o that.
        String sharedHash = passwordEncoder.encode("Customer@123");
        for (int i = 1; i <= LOAD_TEST_USERS; i++) {
            customers.add(users.save(new User("buyer" + i + "@techmart.dev",
                    sharedHash, "Khach hang " + i,
                    "09" + String.format("%08d", i), Role.CUSTOMER)));
        }
        return customers;
    }

    // ---------------------------------------------------------------- coupon

    /** Du moi tinh huong de demo va test duoc tung nhanh loi, khong chi nhanh hop le. */
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

        // Cac ma "hong" co chu dich: de demo tung nhanh loi
        Coupon expired = new Coupon("HETHAN", DiscountType.PERCENT, new BigDecimal("15.00"),
                BigDecimal.ZERO, null, 100, 1, past, now.minus(1, ChronoUnit.DAYS));
        coupons.save(expired);
        coupons.save(new Coupon("CHUABATDAU", DiscountType.PERCENT, new BigDecimal("25.00"),
                BigDecimal.ZERO, null, 100, 1, now.plus(7, ChronoUnit.DAYS), future));
        // usageLimit = 1: dung cho test 50 nguoi tranh mot luot
        coupons.save(new Coupon("CHIMOTLUOT", DiscountType.FIXED, new BigDecimal("1000000.00"),
                BigDecimal.ZERO, null, 1, 1, past, future));
        coupons.save(new Coupon("DONTOITHIEU50TR", DiscountType.PERCENT, new BigDecimal("30.00"),
                new BigDecimal("50000000.00"), new BigDecimal("5000000.00"), 50, 1, past, future));
    }

    // ---------------------------------------------------------------- flash sale

    /**
     * Ba phien o ba trang thai khac nhau - de demo duoc ca /active lan /upcoming
     * ma khong phai cho doi gio.
     *
     * <p>Phien dang chay duoc nap san ton kho vao Redis luon: neu de request dau tien
     * tu nap thi dung giay mo ban se co hang nghin request cung nap - cache stampede.
     */
    private void seedFlashSales(List<ProductVariant> allVariants) {
        Instant now = clock.instant();

        FlashSale ended = new FlashSale("Flash Sale cuoi tuan truoc",
                now.minus(9, ChronoUnit.DAYS), now.minus(8, ChronoUnit.DAYS));
        addFlashItems(ended, allVariants, 0, 3, 50);
        ended.markEnded();
        flashSales.save(ended);

        FlashSale running = new FlashSale("Flash Sale dang dien ra",
                now.minus(1, ChronoUnit.HOURS), now.plus(6, ChronoUnit.HOURS));
        addFlashItems(running, allVariants, 10, 4, 50);
        running.markRunning();
        FlashSale savedRunning = flashSales.save(running);
        savedRunning.getItems().forEach(stockCounter::warmUp);

        FlashSale upcoming = new FlashSale("Flash Sale 9/9 sap toi",
                now.plus(2, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS).plus(6, ChronoUnit.HOURS));
        addFlashItems(upcoming, allVariants, 30, 5, 100);
        flashSales.save(upcoming);
    }

    private void addFlashItems(FlashSale sale, List<ProductVariant> allVariants,
                               int offset, int count, int quantity) {
        for (int i = 0; i < count; i++) {
            ProductVariant variant = allVariants.get((offset + i * 7) % allVariants.size());
            // Giam 30% so voi gia niem yet
            BigDecimal salePrice = variant.getPrice()
                    .multiply(new BigDecimal("0.70"))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            int saleQuantity = Math.min(quantity, Math.max(1, variant.availableQuantity()));
            sale.addItem(new FlashSaleItem(variant, salePrice, saleQuantity, 2));
        }
    }

    // ---------------------------------------------------------------- lich su don hang

    /**
     * Don hang rai theo 90 ngay, mat do tang dan ve hien tai, cuoi tuan cao hon.
     *
     * <p>Day la phan hay bi bo sot nhat: khong co du lieu lich su thi dashboard doanh thu
     * ve ra duong thang bang 0 va mat luon co hoi demo query aggregate.
     */
    private void seedHistoricalOrders(List<User> customers, List<ProductVariant> variants) {
        Instant now = clock.instant();
        User demoCustomer = customers.get(0);

        for (int i = 0; i < HISTORICAL_ORDERS; i++) {
            // Mat do tang dan ve hien tai (shop dang lon).
            // So mu > 1 keo gia tri ve 0 -> daysAgo nho -> don gan hien tai nhieu hon.
            // (So mu < 1 se cho ket qua NGUOC lai: don don ve qua khu.)
            double bias = Math.pow(random.nextDouble(), 2.2);
            int daysAgo = (int) (bias * HISTORY_DAYS);
            Instant placedAt = now.minus(daysAgo, ChronoUnit.DAYS)
                    .minus(random.nextInt(24 * 60), ChronoUnit.MINUTES);

            // 15 don dau danh cho tai khoan demo de no luon co lich su
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

            // Don khong bi huy thi hang da roi kho -> tru ton cho nhat quan
            if (status != OrderStatus.CANCELLED) {
                order.getItems().forEach(item -> {
                    try {
                        item.getVariant().deduct(item.getQuantity());
                    } catch (IllegalStateException e) {
                        item.getVariant().restock(item.getQuantity());   // het hang thi bu lai
                    }
                });
            }
            orders.save(order);
        }
    }

    /** Ghi lai day du duong di trang thai de GET /orders/{code}/history demo duoc tu te. */
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
        order.logTransition(null, OrderStatus.PENDING_PAYMENT, actorId, "Dat hang", placedAt);

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

    // ---------------------------------------------------------------- serial + danh gia

    /**
     * Serial cho mot phan variant, va danh gia cho cac don da giao.
     *
     * <p>Chi mot phan variant co serial - dung nhu thuc te: khong phai mat hang nao cung
     * duoc theo doi theo tung don vi. Phan con lai chi quan ly theo so luong.
     */
    private void seedSerialsAndReviews(List<ProductVariant> allVariants) {
        Instant now = clock.instant();

        // 20 variant dau tien duoc theo doi theo serial
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

        // Gan serial cho cac don DA GIAO, va viet danh gia cho mot phan trong so do
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
                // Khoang mot nua don da giao co danh gia
                if (random.nextBoolean() && reviewCount < 250) {
                    reviews.save(new Review(item.getVariant().getProduct(), order.getUser(), item,
                            1 + random.nextInt(5), pick(REVIEW_COMMENTS)));
                    reviewCount++;
                }
            }
        }
        log.info("Da tao {} serial va {} danh gia", serials.count(), reviewCount);
    }

    private static final List<String> REVIEW_COMMENTS = List.of(
            "May dung tot, pin trau hon minh nghi.",
            "Giao hang nhanh, dong goi can than.",
            "Man hinh dep, nhung hoi nong khi choi game lau.",
            "Dung duoc, gia hop ly so voi cau hinh.",
            "Am thanh nho hon mong doi, con lai on.",
            "Rat hai long, se quay lai mua lan sau.",
            "Hang chinh hang, co day du phu kien.",
            "Sac hoi cham nhung khong dang ke.");

    // ---------------------------------------------------------------- kiem tra

    /**
     * Doi soat sau khi seed. Du lieu demo tu mau thuan thi nguoi tinh y se thay ngay,
     * va day cung la ban nhap cua job doi soat noi o docs/01-domain-v2.md muc 5.1.
     */
    private void verifyInvariants() {
        long negative = products.findAll().stream()
                .flatMap(p -> p.getVariants().stream())
                .filter(v -> v.getStockQuantity() < 0 || v.getReservedQuantity() != 0)
                .count();
        if (negative > 0) {
            throw new IllegalStateException("Seed sai: " + negative + " variant co ton kho am hoac dang bi giu");
        }
    }

    // ---------------------------------------------------------------- tien ich

    private void addImages(Product product, String name) {
        for (int i = 0; i < 4; i++) {
            product.addImage(new ProductImage(imageUrl(name + " " + (i + 1)), i));
        }
    }

    private static String imageUrl(String text) {
        return "https://placehold.co/800x800/png?text=" + text.replace(' ', '+');
    }

    private String orderCode(Instant at, int index) {
        return "TM-" + at.toString().substring(0, 10).replace("-", "") + "-S" + String.format("%05d", index);
    }

    private Map<String, Object> shippingAddress() {
        List<String> districts = List.of("Quan 1", "Quan 3", "Quan 7", "Cau Giay", "Dong Da", "Hai Chau");
        List<String> provinces = List.of("TP Ho Chi Minh", "Ha Noi", "Da Nang");
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("receiverName", "Nguoi nhan " + (1 + random.nextInt(500)));
        address.put("phone", "09" + String.format("%08d", random.nextInt(100_000_000)));
        address.put("street", (1 + random.nextInt(200)) + " Duong so " + (1 + random.nextInt(30)));
        address.put("ward", "Phuong " + (1 + random.nextInt(20)));
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
