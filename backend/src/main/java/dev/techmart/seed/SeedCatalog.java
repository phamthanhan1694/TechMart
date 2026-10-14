package dev.techmart.seed;

import java.util.List;
import java.util.Map;

/**
 * Catalog demo: sản phẩm CO THAT, thong so trả từ trang chu hàng.
 *
 * <p><b>Không con bo sinh to hop.</b> Ban trước nhận 30 hat giong với 15 hau to
 * (" Pro", " Max", " Lite", " Neo"...) để bom len 345 dòng, để ra nhưng ten vô nghĩa
 * kieu "iPhone 15 Pro Max Pro" hay "iPhone 15 Pro Max Lite". Ai biet dien thoai la
 * nhận ra ngay, và một catalog nhu vay làm hong đó tin của ca ban demo.
 *
 * <p>So luồng dòng không phải lý do để giữ bo sinh đó: khi cần quy mo thật để đó
 * hiệu năng, dùng {@code loadtest/bulk-data.sql} (50.000 sản phẩm) - đó là duong
 * riêng, không tron vào dữ liệu demo.
 *
 * <p><b>Gia là giả tham khao thì truong VN tai thời điểm viet</b>, không phải giả
 * cập nhật theo ngay. Muc dich là để con so nhin hop ly, không phải để báo giả.
 *
 * <p>Spec của cùng một danh mục dùng CHUNG bo key. Nếu key không nhat quan thì bộ lọc
 * {@code spec.ram=16GB} sẽ trả thieu kết quả (docs/08-seed-data.md mục 3).
 */
final class SeedCatalog {

    /**
     * Một dòng dien thoai. {@code storages} là các mục dùng luồng CO THAT của may đó;
     * giả mọi mục = {@code basePrice + i * storageStep}.
     */
    record PhoneSeed(String brand, String name, String chipset, String screen, String refreshRate,
                     String camera, String battery, String os, String ram, int year,
                     long basePrice, long storageStep,
                     List<String> storages, List<String> colors) {}

    /** Một dòng laptop. {@code configs} là các câu hinh RAM/SSD có thật của may đó. */
    record LaptopSeed(String brand, String name, String cpu, String gpu, String screen,
                      String battery, String weight, String os, int year,
                      long basePrice, long configStep,
                      List<String> rams, List<String> ssds, List<String> colors) {}

    static final List<String> BRANDS = List.of(
            "Apple", "Samsung", "Xiaomi", "OPPO", "Google", "Sony",
            "Dell", "HP", "Lenovo", "ASUS", "Acer", "MSI");

    // ------------------------------------------------------------------ dien thoai

    static final List<PhoneSeed> PHONES = List.of(
            // --- Apple ---
            new PhoneSeed("Apple", "iPhone 15 Pro Max", "Apple A17 Pro", "6.7 inch OLED", "120Hz",
                    "48MP + 12MP + 12MP", "4441mAh", "iOS 17", "8GB", 2023,
                    34_990_000L, 6_000_000L, List.of("256GB", "512GB", "1TB"),
                    List.of("Titan tu nhien", "Titan den", "Titan trang")),
            new PhoneSeed("Apple", "iPhone 15 Pro", "Apple A17 Pro", "6.1 inch OLED", "120Hz",
                    "48MP + 12MP + 12MP", "3274mAh", "iOS 17", "8GB", 2023,
                    28_990_000L, 3_500_000L, List.of("128GB", "256GB", "512GB"),
                    List.of("Titan tu nhien", "Titan xanh", "Titan den")),
            new PhoneSeed("Apple", "iPhone 15 Plus", "Apple A16 Bionic", "6.7 inch OLED", "60Hz",
                    "48MP + 12MP", "4383mAh", "iOS 17", "6GB", 2023,
                    25_990_000L, 3_000_000L, List.of("128GB", "256GB", "512GB"),
                    List.of("Hong", "Xanh duong", "Den")),
            new PhoneSeed("Apple", "iPhone 15", "Apple A16 Bionic", "6.1 inch OLED", "60Hz",
                    "48MP + 12MP", "3349mAh", "iOS 17", "6GB", 2023,
                    22_990_000L, 3_000_000L, List.of("128GB", "256GB", "512GB"),
                    List.of("Hong", "Vang", "Den")),
            new PhoneSeed("Apple", "iPhone 14", "Apple A15 Bionic", "6.1 inch OLED", "60Hz",
                    "12MP + 12MP", "3279mAh", "iOS 16", "6GB", 2022,
                    17_990_000L, 3_000_000L, List.of("128GB", "256GB"),
                    List.of("Xanh duong", "Trang", "Den")),
            new PhoneSeed("Apple", "iPhone 13", "Apple A15 Bionic", "6.1 inch OLED", "60Hz",
                    "12MP + 12MP", "3240mAh", "iOS 15", "4GB", 2021,
                    14_490_000L, 2_500_000L, List.of("128GB", "256GB"),
                    List.of("Hong", "Xanh la", "Den")),

            // --- Samsung ---
            new PhoneSeed("Samsung", "Galaxy S24 Ultra", "Snapdragon 8 Gen 3", "6.8 inch Dynamic AMOLED 2X", "120Hz",
                    "200MP + 50MP + 12MP + 10MP", "5000mAh", "Android 14", "12GB", 2024,
                    33_990_000L, 4_000_000L, List.of("256GB", "512GB", "1TB"),
                    List.of("Titan xam", "Titan den", "Titan tim")),
            new PhoneSeed("Samsung", "Galaxy S24+", "Exynos 2400", "6.7 inch Dynamic AMOLED 2X", "120Hz",
                    "50MP + 12MP + 10MP", "4900mAh", "Android 14", "12GB", 2024,
                    26_990_000L, 3_000_000L, List.of("256GB", "512GB"),
                    List.of("Xam", "Den", "Tim")),
            new PhoneSeed("Samsung", "Galaxy S24", "Exynos 2400", "6.2 inch Dynamic AMOLED 2X", "120Hz",
                    "50MP + 12MP + 10MP", "4000mAh", "Android 14", "8GB", 2024,
                    22_990_000L, 3_000_000L, List.of("128GB", "256GB", "512GB"),
                    List.of("Vang", "Den", "Tim")),
            new PhoneSeed("Samsung", "Galaxy Z Fold5", "Snapdragon 8 Gen 2", "7.6 inch Dynamic AMOLED 2X", "120Hz",
                    "50MP + 12MP + 10MP", "4400mAh", "Android 13", "12GB", 2023,
                    40_990_000L, 4_500_000L, List.of("256GB", "512GB"),
                    List.of("Kem", "Den", "Xanh")),
            new PhoneSeed("Samsung", "Galaxy Z Flip5", "Snapdragon 8 Gen 2", "6.7 inch Dynamic AMOLED 2X", "120Hz",
                    "12MP + 12MP", "3700mAh", "Android 13", "8GB", 2023,
                    24_990_000L, 3_000_000L, List.of("256GB", "512GB"),
                    List.of("Kem", "Tim", "Xanh mint")),
            new PhoneSeed("Samsung", "Galaxy S23 FE", "Exynos 2200", "6.4 inch Dynamic AMOLED 2X", "120Hz",
                    "50MP + 12MP + 8MP", "4500mAh", "Android 13", "8GB", 2023,
                    14_490_000L, 2_000_000L, List.of("128GB", "256GB"),
                    List.of("Tim", "Xanh mint", "Do")),
            new PhoneSeed("Samsung", "Galaxy A55 5G", "Exynos 1480", "6.6 inch Super AMOLED", "120Hz",
                    "50MP + 12MP + 5MP", "5000mAh", "Android 14", "8GB", 2024,
                    9_990_000L, 1_500_000L, List.of("128GB", "256GB"),
                    List.of("Xanh navy", "Tim nhat", "Den")),
            new PhoneSeed("Samsung", "Galaxy A35 5G", "Exynos 1380", "6.6 inch Super AMOLED", "120Hz",
                    "50MP + 8MP + 5MP", "5000mAh", "Android 14", "8GB", 2024,
                    8_290_000L, 1_200_000L, List.of("128GB", "256GB"),
                    List.of("Xanh nhat", "Tim", "Den")),

            // --- Xiaomi ---
            new PhoneSeed("Xiaomi", "Xiaomi 14 Ultra", "Snapdragon 8 Gen 3", "6.73 inch AMOLED", "120Hz",
                    "50MP + 50MP + 50MP + 50MP", "5300mAh", "Android 14", "16GB", 2024,
                    29_990_000L, 3_000_000L, List.of("512GB", "1TB"),
                    List.of("Den", "Trang")),
            new PhoneSeed("Xiaomi", "Xiaomi 14", "Snapdragon 8 Gen 3", "6.36 inch AMOLED", "120Hz",
                    "50MP + 50MP + 50MP", "4610mAh", "Android 14", "12GB", 2024,
                    22_990_000L, 2_000_000L, List.of("256GB", "512GB"),
                    List.of("Den", "Xanh la", "Trang")),
            new PhoneSeed("Xiaomi", "Redmi Note 13 Pro+ 5G", "Dimensity 7200 Ultra", "6.67 inch AMOLED", "120Hz",
                    "200MP + 8MP + 2MP", "5000mAh", "Android 13", "12GB", 2024,
                    10_990_000L, 1_500_000L, List.of("256GB", "512GB"),
                    List.of("Den", "Tim", "Trang")),
            new PhoneSeed("Xiaomi", "Redmi Note 13 Pro", "Snapdragon 7s Gen 2", "6.67 inch AMOLED", "120Hz",
                    "200MP + 8MP + 2MP", "5100mAh", "Android 13", "8GB", 2024,
                    7_490_000L, 1_000_000L, List.of("128GB", "256GB"),
                    List.of("Den", "Xanh", "Tim")),
            new PhoneSeed("Xiaomi", "POCO X6 Pro 5G", "Dimensity 8300 Ultra", "6.67 inch AMOLED", "120Hz",
                    "64MP + 8MP + 2MP", "5000mAh", "Android 14", "12GB", 2024,
                    8_990_000L, 1_200_000L, List.of("256GB", "512GB"),
                    List.of("Den", "Vang", "Xam")),

            // --- OPPO ---
            new PhoneSeed("OPPO", "Find X7 Ultra", "Snapdragon 8 Gen 3", "6.82 inch AMOLED", "120Hz",
                    "50MP + 50MP + 50MP + 50MP", "5000mAh", "Android 14", "16GB", 2024,
                    26_990_000L, 3_000_000L, List.of("256GB", "512GB"),
                    List.of("Den", "Nau")),
            new PhoneSeed("OPPO", "Reno11 F 5G", "Dimensity 7050", "6.7 inch AMOLED", "120Hz",
                    "64MP + 8MP + 2MP", "5000mAh", "Android 14", "8GB", 2024,
                    8_490_000L, 1_000_000L, List.of("256GB"),
                    List.of("Xanh ngoc", "Tim")),
            new PhoneSeed("OPPO", "Reno11 5G", "Dimensity 8200", "6.7 inch AMOLED", "120Hz",
                    "50MP + 32MP + 8MP", "4800mAh", "Android 14", "12GB", 2024,
                    11_990_000L, 1_500_000L, List.of("256GB", "512GB"),
                    List.of("Xanh", "Xam")),
            new PhoneSeed("OPPO", "OPPO A79 5G", "Dimensity 6020", "6.72 inch LCD", "90Hz",
                    "50MP + 2MP", "5000mAh", "Android 13", "8GB", 2023,
                    6_490_000L, 800_000L, List.of("128GB", "256GB"),
                    List.of("Tim", "Den")),

            // --- Google ---
            new PhoneSeed("Google", "Pixel 8 Pro", "Google Tensor G3", "6.7 inch LTPO OLED", "120Hz",
                    "50MP + 48MP + 48MP", "5050mAh", "Android 14", "12GB", 2023,
                    24_990_000L, 3_000_000L, List.of("128GB", "256GB", "512GB"),
                    List.of("Den", "Xanh nhat", "Trang")),
            new PhoneSeed("Google", "Pixel 8", "Google Tensor G3", "6.2 inch OLED", "120Hz",
                    "50MP + 12MP", "4575mAh", "Android 14", "8GB", 2023,
                    17_990_000L, 2_500_000L, List.of("128GB", "256GB"),
                    List.of("Den", "Hong", "Xanh")),
            new PhoneSeed("Google", "Pixel 8a", "Google Tensor G3", "6.1 inch OLED", "120Hz",
                    "64MP + 13MP", "4492mAh", "Android 14", "8GB", 2024,
                    12_990_000L, 2_000_000L, List.of("128GB", "256GB"),
                    List.of("Den", "Xanh mint", "Trang")),

            // --- Sony ---
            new PhoneSeed("Sony", "Xperia 1 VI", "Snapdragon 8 Gen 3", "6.5 inch OLED", "120Hz",
                    "48MP + 12MP + 12MP", "5000mAh", "Android 14", "12GB", 2024,
                    29_990_000L, 3_000_000L, List.of("256GB", "512GB"),
                    List.of("Den", "Bac", "Xanh")),
            new PhoneSeed("Sony", "Xperia 5 V", "Snapdragon 8 Gen 2", "6.1 inch OLED", "120Hz",
                    "52MP + 12MP", "5000mAh", "Android 13", "8GB", 2023,
                    22_990_000L, 2_500_000L, List.of("128GB", "256GB"),
                    List.of("Den", "Xanh la", "Bac")));

    // ------------------------------------------------------------------ laptop

    static final List<LaptopSeed> LAPTOPS = List.of(
            // --- Apple ---
            new LaptopSeed("Apple", "MacBook Air 13 M3", "Apple M3", "GPU 8 nhan", "13.6 inch Liquid Retina",
                    "52.6Wh", "1.24kg", "macOS Sonoma", 2024,
                    27_990_000L, 5_500_000L, List.of("8GB", "16GB"), List.of("256GB SSD", "512GB SSD"),
                    List.of("Xam", "Bac", "Xanh dem")),
            new LaptopSeed("Apple", "MacBook Air 15 M3", "Apple M3", "GPU 10 nhan", "15.3 inch Liquid Retina",
                    "66.5Wh", "1.51kg", "macOS Sonoma", 2024,
                    32_990_000L, 5_500_000L, List.of("8GB", "16GB"), List.of("256GB SSD", "512GB SSD"),
                    List.of("Xam", "Bac", "Vang dong")),
            new LaptopSeed("Apple", "MacBook Air 13 M2", "Apple M2", "GPU 8 nhan", "13.6 inch Liquid Retina",
                    "52.6Wh", "1.24kg", "macOS Ventura", 2022,
                    22_990_000L, 5_000_000L, List.of("8GB", "16GB"), List.of("256GB SSD", "512GB SSD"),
                    List.of("Xam", "Bac")),
            new LaptopSeed("Apple", "MacBook Pro 14 M3", "Apple M3", "GPU 10 nhan", "14.2 inch Liquid Retina XDR",
                    "70Wh", "1.55kg", "macOS Sonoma", 2023,
                    39_990_000L, 6_000_000L, List.of("8GB", "16GB"), List.of("512GB SSD", "1TB SSD"),
                    List.of("Xam", "Bac")),
            new LaptopSeed("Apple", "MacBook Pro 14 M3 Pro", "Apple M3 Pro", "GPU 14 nhan", "14.2 inch Liquid Retina XDR",
                    "70Wh", "1.61kg", "macOS Sonoma", 2023,
                    49_990_000L, 7_000_000L, List.of("18GB", "36GB"), List.of("512GB SSD", "1TB SSD"),
                    List.of("Den", "Bac")),
            new LaptopSeed("Apple", "MacBook Pro 16 M3 Pro", "Apple M3 Pro", "GPU 18 nhan", "16.2 inch Liquid Retina XDR",
                    "100Wh", "2.14kg", "macOS Sonoma", 2023,
                    62_990_000L, 8_000_000L, List.of("18GB", "36GB"), List.of("512GB SSD", "1TB SSD"),
                    List.of("Den", "Bac")),

            // --- Dell ---
            new LaptopSeed("Dell", "XPS 13 Plus 9320", "Intel Core i7-1360P", "Intel Iris Xe", "13.4 inch OLED 3.5K",
                    "55Wh", "1.26kg", "Windows 11", 2023,
                    34_990_000L, 5_000_000L, List.of("16GB", "32GB"), List.of("512GB SSD", "1TB SSD"),
                    List.of("Bac", "Den")),
            new LaptopSeed("Dell", "XPS 15 9530", "Intel Core i7-13700H", "NVIDIA RTX 4050", "15.6 inch OLED 3.5K",
                    "86Wh", "1.86kg", "Windows 11", 2023,
                    45_990_000L, 6_000_000L, List.of("16GB", "32GB"), List.of("512GB SSD", "1TB SSD"),
                    List.of("Bac")),
            new LaptopSeed("Dell", "Inspiron 15 3530", "Intel Core i5-1335U", "Intel Iris Xe", "15.6 inch FHD 120Hz",
                    "54Wh", "1.65kg", "Windows 11", 2023,
                    15_990_000L, 2_500_000L, List.of("8GB", "16GB"), List.of("512GB SSD", "1TB SSD"),
                    List.of("Den", "Bac")),
            new LaptopSeed("Dell", "Vostro 3520", "Intel Core i5-1235U", "Intel Iris Xe", "15.6 inch FHD 120Hz",
                    "41Wh", "1.83kg", "Windows 11", 2023,
                    13_490_000L, 2_000_000L, List.of("8GB", "16GB"), List.of("256GB SSD", "512GB SSD"),
                    List.of("Den")),
            new LaptopSeed("Dell", "Latitude 5440", "Intel Core i5-1345U", "Intel Iris Xe", "14 inch FHD",
                    "54Wh", "1.36kg", "Windows 11 Pro", 2023,
                    24_990_000L, 3_500_000L, List.of("16GB", "32GB"), List.of("512GB SSD", "1TB SSD"),
                    List.of("Xam")),

            // --- HP ---
            new LaptopSeed("HP", "Spectre x360 14", "Intel Core Ultra 7 155H", "Intel Arc", "14 inch OLED 2.8K",
                    "68Wh", "1.44kg", "Windows 11", 2024,
                    36_990_000L, 5_000_000L, List.of("16GB", "32GB"), List.of("512GB SSD", "1TB SSD"),
                    List.of("Xanh dem", "Bac")),
            new LaptopSeed("HP", "Envy x360 14", "Intel Core Ultra 5 125U", "Intel Graphics", "14 inch WUXGA cam ung",
                    "59Wh", "1.39kg", "Windows 11", 2024,
                    25_990_000L, 3_500_000L, List.of("16GB", "32GB"), List.of("512GB SSD", "1TB SSD"),
                    List.of("Bac", "Den")),
            new LaptopSeed("HP", "Pavilion 15", "AMD Ryzen 5 7530U", "AMD Radeon", "15.6 inch FHD",
                    "41Wh", "1.75kg", "Windows 11", 2023,
                    13_490_000L, 2_000_000L, List.of("8GB", "16GB"), List.of("256GB SSD", "512GB SSD"),
                    List.of("Bac", "Vang")),
            new LaptopSeed("HP", "Victus 16", "Intel Core i7-13700HX", "NVIDIA RTX 4060", "16.1 inch FHD 144Hz",
                    "70Wh", "2.42kg", "Windows 11", 2023,
                    28_990_000L, 4_000_000L, List.of("16GB", "32GB"), List.of("512GB SSD", "1TB SSD"),
                    List.of("Den", "Bac")),
            new LaptopSeed("HP", "ProBook 450 G10", "Intel Core i5-1335U", "Intel Iris Xe", "15.6 inch FHD",
                    "51Wh", "1.79kg", "Windows 11 Pro", 2023,
                    19_990_000L, 3_000_000L, List.of("8GB", "16GB"), List.of("512GB SSD", "1TB SSD"),
                    List.of("Bac")),

            // --- Lenovo ---
            new LaptopSeed("Lenovo", "ThinkPad X1 Carbon Gen 12", "Intel Core Ultra 7 155H", "Intel Arc", "14 inch WUXGA",
                    "57Wh", "1.09kg", "Windows 11 Pro", 2024,
                    42_990_000L, 6_000_000L, List.of("16GB", "32GB"), List.of("512GB SSD", "1TB SSD"),
                    List.of("Den")),
            new LaptopSeed("Lenovo", "ThinkBook 14 G6", "AMD Ryzen 5 7530U", "AMD Radeon", "14 inch WUXGA",
                    "60Wh", "1.4kg", "Windows 11", 2023,
                    16_490_000L, 2_500_000L, List.of("16GB", "32GB"), List.of("512GB SSD", "1TB SSD"),
                    List.of("Xam")),
            new LaptopSeed("Lenovo", "IdeaPad Slim 5", "AMD Ryzen 7 7730U", "AMD Radeon", "14 inch WUXGA",
                    "56.5Wh", "1.46kg", "Windows 11", 2023,
                    16_990_000L, 2_500_000L, List.of("8GB", "16GB"), List.of("512GB SSD", "1TB SSD"),
                    List.of("Xam", "Xanh")),
            new LaptopSeed("Lenovo", "Legion 5 Pro 16", "AMD Ryzen 7 7745HX", "NVIDIA RTX 4060", "16 inch WQXGA 165Hz",
                    "80Wh", "2.55kg", "Windows 11", 2023,
                    36_990_000L, 5_000_000L, List.of("16GB", "32GB"), List.of("512GB SSD", "1TB SSD"),
                    List.of("Xam", "Den")),
            new LaptopSeed("Lenovo", "LOQ 15", "Intel Core i5-12450HX", "NVIDIA RTX 4050", "15.6 inch FHD 144Hz",
                    "60Wh", "2.4kg", "Windows 11", 2023,
                    21_490_000L, 3_000_000L, List.of("8GB", "16GB"), List.of("512GB SSD", "1TB SSD"),
                    List.of("Xam")),

            // --- ASUS ---
            new LaptopSeed("ASUS", "ROG Zephyrus G14", "AMD Ryzen 9 8945HS", "NVIDIA RTX 4070", "14 inch OLED 120Hz",
                    "73Wh", "1.5kg", "Windows 11", 2024,
                    44_990_000L, 6_000_000L, List.of("16GB", "32GB"), List.of("512GB SSD", "1TB SSD"),
                    List.of("Trang", "Xam")),
            new LaptopSeed("ASUS", "Zenbook 14 OLED", "Intel Core Ultra 5 125H", "Intel Arc", "14 inch OLED 3K",
                    "75Wh", "1.28kg", "Windows 11", 2024,
                    24_990_000L, 3_500_000L, List.of("16GB", "32GB"), List.of("512GB SSD", "1TB SSD"),
                    List.of("Xanh dem", "Bac")),
            new LaptopSeed("ASUS", "Vivobook 15", "Intel Core i5-1335U", "Intel Iris Xe", "15.6 inch FHD",
                    "42Wh", "1.7kg", "Windows 11", 2023,
                    14_490_000L, 2_200_000L, List.of("8GB", "16GB"), List.of("512GB SSD", "1TB SSD"),
                    List.of("Bac", "Xanh")),
            new LaptopSeed("ASUS", "TUF Gaming F15", "Intel Core i7-12700H", "NVIDIA RTX 4060", "15.6 inch FHD 144Hz",
                    "90Wh", "2.2kg", "Windows 11", 2023,
                    27_990_000L, 4_000_000L, List.of("8GB", "16GB"), List.of("512GB SSD", "1TB SSD"),
                    List.of("Xam", "Den")),
            new LaptopSeed("ASUS", "ProArt Studiobook 16", "Intel Core i9-13980HX", "NVIDIA RTX 4070", "16 inch OLED 3.2K",
                    "90Wh", "2.4kg", "Windows 11 Pro", 2023,
                    58_990_000L, 7_000_000L, List.of("16GB", "32GB"), List.of("1TB SSD", "2TB SSD"),
                    List.of("Den")),

            // --- Acer ---
            new LaptopSeed("Acer", "Nitro 5 AN515", "Intel Core i5-12500H", "NVIDIA RTX 4050", "15.6 inch FHD 144Hz",
                    "57.5Wh", "2.5kg", "Windows 11", 2023,
                    21_990_000L, 3_000_000L, List.of("8GB", "16GB"), List.of("512GB SSD", "1TB SSD"),
                    List.of("Den")),
            new LaptopSeed("Acer", "Swift Go 14", "Intel Core i5-1335U", "Intel Iris Xe", "14 inch OLED 2.8K",
                    "65Wh", "1.32kg", "Windows 11", 2023,
                    17_490_000L, 2_500_000L, List.of("16GB", "32GB"), List.of("512GB SSD", "1TB SSD"),
                    List.of("Bac", "Xam")),
            new LaptopSeed("Acer", "Aspire 5", "Intel Core i5-1235U", "Intel Iris Xe", "15.6 inch FHD",
                    "50Wh", "1.78kg", "Windows 11", 2023,
                    13_990_000L, 2_000_000L, List.of("8GB", "16GB"), List.of("256GB SSD", "512GB SSD"),
                    List.of("Bac", "Den")),
            new LaptopSeed("Acer", "Predator Helios Neo 16", "Intel Core i7-13650HX", "NVIDIA RTX 4060", "16 inch WQXGA 165Hz",
                    "90Wh", "2.6kg", "Windows 11", 2023,
                    35_990_000L, 5_000_000L, List.of("16GB", "32GB"), List.of("512GB SSD", "1TB SSD"),
                    List.of("Den")),

            // --- MSI ---
            new LaptopSeed("MSI", "Katana 15", "Intel Core i7-13620H", "NVIDIA RTX 4060", "15.6 inch FHD 144Hz",
                    "53.5Wh", "2.25kg", "Windows 11", 2023,
                    27_490_000L, 4_000_000L, List.of("16GB", "32GB"), List.of("512GB SSD", "1TB SSD"),
                    List.of("Den")),
            new LaptopSeed("MSI", "Modern 14", "Intel Core i5-1335U", "Intel Iris Xe", "14 inch FHD",
                    "39.3Wh", "1.4kg", "Windows 11", 2023,
                    12_990_000L, 2_000_000L, List.of("8GB", "16GB"), List.of("512GB SSD", "1TB SSD"),
                    List.of("Den", "Bac")),
            new LaptopSeed("MSI", "Cyborg 15", "Intel Core i7-12650H", "NVIDIA RTX 4050", "15.6 inch FHD 144Hz",
                    "53.5Wh", "1.98kg", "Windows 11", 2023,
                    22_990_000L, 3_000_000L, List.of("8GB", "16GB"), List.of("512GB SSD", "1TB SSD"),
                    List.of("Den")),
            new LaptopSeed("MSI", "Prestige 13 AI Evo", "Intel Core Ultra 7 155H", "Intel Arc", "13.3 inch OLED",
                    "75Wh", "0.99kg", "Windows 11", 2024,
                    32_990_000L, 4_500_000L, List.of("16GB", "32GB"), List.of("512GB SSD", "1TB SSD"),
                    List.of("Xam", "Trang")));

    static Map<String, Object> phoneSpec(PhoneSeed p, String storage) {
        return Map.of(
                "screen", p.screen(), "refreshRate", p.refreshRate(), "chipset", p.chipset(),
                "ram", p.ram(), "storage", storage, "battery", p.battery(),
                "camera", p.camera(), "os", p.os(), "releaseYear", p.year());
    }

    static Map<String, Object> laptopSpec(LaptopSeed l, String ram, String ssd) {
        return Map.of(
                "screen", l.screen(), "cpu", l.cpu(), "gpu", l.gpu(),
                "ram", ram, "storage", ssd, "battery", l.battery(),
                "weight", l.weight(), "os", l.os(), "releaseYear", l.year());
    }

    private SeedCatalog() {
    }
}
