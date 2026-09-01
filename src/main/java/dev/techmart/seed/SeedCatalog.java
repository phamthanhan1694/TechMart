package dev.techmart.seed;

import java.util.List;
import java.util.Map;

/**
 * Du lieu "hat giong": san pham co that voi thong so chinh xac, tra tu trang chu hang.
 * Bo sinh se to hop tu day ra vai tram san pham - xem {@link DataSeeder}.
 *
 * <p>Spec cua cung mot danh muc dung CHUNG bo key. Neu key khong nhat quan thi bo loc
 * {@code spec.ram=16GB} se tra thieu ket qua (docs/08-seed-data.md muc 3).
 */
final class SeedCatalog {

    record PhoneSeed(String brand, String name, String chipset, String screen, String refreshRate,
                     String camera, String battery, String os, int year, long basePrice) {}

    record LaptopSeed(String brand, String name, String cpu, String gpu, String screen,
                      String battery, String weight, String os, int year, long basePrice) {}

    static final List<String> PHONE_STORAGE = List.of("128GB", "256GB", "512GB");
    static final List<String> PHONE_RAM = List.of("8GB", "12GB");
    static final List<String> PHONE_COLORS = List.of("Den", "Trang", "Xanh", "Titan tu nhien");

    static final List<String> LAPTOP_RAM = List.of("8GB", "16GB", "32GB");
    static final List<String> LAPTOP_SSD = List.of("256GB SSD", "512GB SSD", "1TB SSD");
    static final List<String> LAPTOP_COLORS = List.of("Bac", "Xam", "Den");

    static final List<String> BRANDS = List.of(
            "Apple", "Samsung", "Xiaomi", "OPPO", "Google", "Sony",
            "Dell", "HP", "Lenovo", "ASUS", "Acer", "MSI");

    static final List<PhoneSeed> PHONES = List.of(
            new PhoneSeed("Apple", "iPhone 15 Pro Max", "A17 Pro", "6.7 inch OLED", "120Hz",
                    "48MP + 12MP + 12MP", "4441mAh", "iOS 17", 2023, 29_990_000L),
            new PhoneSeed("Apple", "iPhone 15", "A16 Bionic", "6.1 inch OLED", "60Hz",
                    "48MP + 12MP", "3349mAh", "iOS 17", 2023, 19_990_000L),
            new PhoneSeed("Apple", "iPhone 14 Pro", "A16 Bionic", "6.1 inch OLED", "120Hz",
                    "48MP + 12MP + 12MP", "3200mAh", "iOS 16", 2022, 24_990_000L),
            new PhoneSeed("Apple", "iPhone 13", "A15 Bionic", "6.1 inch OLED", "60Hz",
                    "12MP + 12MP", "3240mAh", "iOS 15", 2021, 14_990_000L),
            new PhoneSeed("Samsung", "Galaxy S24 Ultra", "Snapdragon 8 Gen 3", "6.8 inch AMOLED", "120Hz",
                    "200MP + 50MP + 12MP + 10MP", "5000mAh", "Android 14", 2024, 31_990_000L),
            new PhoneSeed("Samsung", "Galaxy S24", "Exynos 2400", "6.2 inch AMOLED", "120Hz",
                    "50MP + 12MP + 10MP", "4000mAh", "Android 14", 2024, 20_990_000L),
            new PhoneSeed("Samsung", "Galaxy S23 FE", "Exynos 2200", "6.4 inch AMOLED", "120Hz",
                    "50MP + 12MP + 8MP", "4500mAh", "Android 13", 2023, 13_990_000L),
            new PhoneSeed("Samsung", "Galaxy A55", "Exynos 1480", "6.6 inch Super AMOLED", "120Hz",
                    "50MP + 12MP + 5MP", "5000mAh", "Android 14", 2024, 9_490_000L),
            new PhoneSeed("Xiaomi", "Xiaomi 14 Ultra", "Snapdragon 8 Gen 3", "6.73 inch AMOLED", "120Hz",
                    "50MP + 50MP + 50MP + 50MP", "5300mAh", "Android 14", 2024, 27_990_000L),
            new PhoneSeed("Xiaomi", "Redmi Note 13 Pro", "Snapdragon 7s Gen 2", "6.67 inch AMOLED", "120Hz",
                    "200MP + 8MP + 2MP", "5100mAh", "Android 13", 2024, 7_490_000L),
            new PhoneSeed("OPPO", "Find X7 Ultra", "Snapdragon 8 Gen 3", "6.82 inch AMOLED", "120Hz",
                    "50MP + 50MP + 50MP + 50MP", "5000mAh", "Android 14", 2024, 24_990_000L),
            new PhoneSeed("OPPO", "Reno 11", "Dimensity 8200", "6.7 inch AMOLED", "120Hz",
                    "50MP + 32MP + 8MP", "4800mAh", "Android 14", 2024, 10_990_000L),
            new PhoneSeed("Google", "Pixel 8 Pro", "Google Tensor G3", "6.7 inch OLED", "120Hz",
                    "50MP + 48MP + 48MP", "5050mAh", "Android 14", 2023, 25_990_000L),
            new PhoneSeed("Google", "Pixel 8a", "Google Tensor G3", "6.1 inch OLED", "120Hz",
                    "64MP + 13MP", "4492mAh", "Android 14", 2024, 12_990_000L),
            new PhoneSeed("Sony", "Xperia 1 VI", "Snapdragon 8 Gen 3", "6.5 inch OLED", "120Hz",
                    "48MP + 12MP + 12MP", "5000mAh", "Android 14", 2024, 28_990_000L));

    static final List<LaptopSeed> LAPTOPS = List.of(
            new LaptopSeed("Apple", "MacBook Air M3", "Apple M3", "10-core GPU", "13.6 inch Retina",
                    "52.6Wh", "1.24kg", "macOS Sonoma", 2024, 27_990_000L),
            new LaptopSeed("Apple", "MacBook Pro 14 M3 Pro", "Apple M3 Pro", "18-core GPU", "14.2 inch Liquid Retina XDR",
                    "70Wh", "1.55kg", "macOS Sonoma", 2023, 49_990_000L),
            new LaptopSeed("Apple", "MacBook Air M2", "Apple M2", "8-core GPU", "13.6 inch Retina",
                    "52.6Wh", "1.24kg", "macOS Ventura", 2022, 22_990_000L),
            new LaptopSeed("Dell", "XPS 13 Plus", "Intel Core i7-1360P", "Intel Iris Xe", "13.4 inch OLED",
                    "55Wh", "1.26kg", "Windows 11", 2023, 34_990_000L),
            new LaptopSeed("Dell", "Inspiron 15", "Intel Core i5-1335U", "Intel Iris Xe", "15.6 inch FHD",
                    "54Wh", "1.65kg", "Windows 11", 2023, 15_990_000L),
            new LaptopSeed("HP", "Spectre x360 14", "Intel Core Ultra 7", "Intel Arc", "14 inch OLED",
                    "68Wh", "1.44kg", "Windows 11", 2024, 36_990_000L),
            new LaptopSeed("HP", "Pavilion 15", "AMD Ryzen 5 7530U", "AMD Radeon", "15.6 inch FHD",
                    "41Wh", "1.75kg", "Windows 11", 2023, 13_490_000L),
            new LaptopSeed("Lenovo", "ThinkPad X1 Carbon Gen 12", "Intel Core Ultra 7 155H", "Intel Arc", "14 inch WUXGA",
                    "57Wh", "1.09kg", "Windows 11", 2024, 42_990_000L),
            new LaptopSeed("Lenovo", "IdeaPad Slim 5", "AMD Ryzen 7 7730U", "AMD Radeon", "14 inch WUXGA",
                    "56.5Wh", "1.46kg", "Windows 11", 2023, 16_990_000L),
            new LaptopSeed("ASUS", "ROG Zephyrus G14", "AMD Ryzen 9 8945HS", "NVIDIA RTX 4070", "14 inch OLED",
                    "73Wh", "1.5kg", "Windows 11", 2024, 44_990_000L),
            new LaptopSeed("ASUS", "Zenbook 14 OLED", "Intel Core Ultra 5", "Intel Arc", "14 inch OLED",
                    "75Wh", "1.28kg", "Windows 11", 2024, 24_990_000L),
            new LaptopSeed("Acer", "Nitro 5", "Intel Core i5-12500H", "NVIDIA RTX 4050", "15.6 inch FHD 144Hz",
                    "57.5Wh", "2.5kg", "Windows 11", 2023, 21_990_000L),
            new LaptopSeed("Acer", "Swift Go 14", "Intel Core i5-1335U", "Intel Iris Xe", "14 inch OLED",
                    "65Wh", "1.32kg", "Windows 11", 2023, 17_490_000L),
            new LaptopSeed("MSI", "Katana 15", "Intel Core i7-13620H", "NVIDIA RTX 4060", "15.6 inch FHD 144Hz",
                    "53.5Wh", "2.25kg", "Windows 11", 2023, 27_490_000L),
            new LaptopSeed("MSI", "Modern 14", "Intel Core i5-1335U", "Intel Iris Xe", "14 inch FHD",
                    "39.3Wh", "1.4kg", "Windows 11", 2023, 12_990_000L));

    /** Bien the theo nam ra mat de sinh them san pham ma van hop ly. */
    static final List<String> VARIANT_SUFFIXES = List.of(
            "", " (2023)", " 5G", " Plus", " Lite", " Pro", " Max", " SE", " Neo", " Prime",
            " Edge", " Ultra Edition", " Business", " Creator", " Gaming Edition");

    static Map<String, Object> phoneSpec(PhoneSeed p, String ram, String storage) {
        return Map.of(
                "screen", p.screen(), "refreshRate", p.refreshRate(), "chipset", p.chipset(),
                "ram", ram, "storage", storage, "battery", p.battery(),
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
