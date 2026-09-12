package com.shoppingagent.search.provider;

import com.shoppingagent.ai.model.ShoppingQuery;
import com.shoppingagent.search.model.Availability;
import com.shoppingagent.search.model.Product;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Module 5.3 — mandatory mock provider. Lets the entire system work end-to-end
 * with zero external/paid dependencies. Demo data only — these are NOT real-time
 * prices and must never be presented to the user as such.
 *
 * Enabled by default (SHOPPING_SEARCH_MODE=mock, which is also the default when
 * the property is unset) via the class-level condition below.
 */
@Component
@ConditionalOnProperty(prefix = "shopping.search", name = "mode", havingValue = "mock")
public class MockProductSearchProvider implements ProductSearchProvider {

    private static final String SOURCE = "MOCK";

    private final List<Product> catalog = buildCatalog();

    @Override
    public List<Product> search(ShoppingQuery query) {
        String category = query.getCategory() == null ? "" : query.getCategory().toLowerCase(Locale.ROOT);

        return catalog.stream()
                .filter(p -> category.isBlank() || matchesCategory(p, category))
                .map(p -> p.toBuilder().lastCheckedAt(Instant.now()).build())
                .toList();
    }

    private boolean matchesCategory(Product p, String category) {
        if (category == null || category.isBlank()) return true;
        String cat = category.toLowerCase(Locale.ROOT).trim();
        String prodCat = p.getCategory().toLowerCase(Locale.ROOT).trim();
        String prodName = p.getName().toLowerCase(Locale.ROOT);

        if (prodCat.contains(cat) || cat.contains(prodCat)) {
            return true;
        }

        String catClean = cat.replaceAll("[\\s_-]", "");
        String prodCatClean = prodCat.replaceAll("[\\s_-]", "");
        if (prodCatClean.contains(catClean) || catClean.contains(prodCatClean)) {
            return true;
        }

        // Synonyms
        if (matchesSynonyms(catClean, prodCatClean)) {
            return true;
        }

        // Check if any word in query matches product category or name
        for (String word : cat.split("[\\s/,-]+")) {
            if (word.length() >= 3 && (prodCat.contains(word) || prodName.contains(word))) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesSynonyms(String want, String have) {
        if ((want.contains("headphone") || want.contains("earphone") || want.contains("earbud") || want.contains("airpod") || want.contains("tws") || want.contains("audio") || want.contains("headset"))
                && (have.contains("headphone") || have.contains("earphone") || have.contains("earbud") || have.contains("airpod") || have.contains("audio") || have.contains("headset"))) {
            return true;
        }
        if ((want.contains("laptop") || want.contains("notebook") || want.contains("macbook") || want.contains("ultrabook") || want.contains("chromebook") || want.contains("computer") || want.contains("pc"))
                && (have.contains("laptop") || have.contains("notebook") || have.contains("macbook") || have.contains("ultrabook") || have.contains("computer") || have.contains("pc"))) {
            return true;
        }
        if ((want.contains("smartwatch") || want.contains("watch") || want.contains("band") || want.contains("tracker") || want.contains("wearable"))
                && (have.contains("smartwatch") || have.contains("watch") || have.contains("band") || have.contains("wearable"))) {
            return true;
        }
        if ((want.contains("phone") || want.contains("mobile") || want.contains("smartphone") || want.contains("iphone") || want.contains("android") || want.contains("cellular"))
                && (have.contains("phone") || have.contains("mobile") || have.contains("smartphone") || have.contains("iphone") || have.contains("cellular"))) {
            return true;
        }
        if ((want.contains("pendrive") || want.contains("flashdrive") || want.contains("usbdrive") || want.contains("usb") || want.contains("storage") || want.contains("drive"))
                && (have.contains("pendrive") || have.contains("flashdrive") || have.contains("usb") || have.contains("storage"))) {
            return true;
        }
        if ((want.contains("mouse") || want.contains("mice") || want.contains("trackpad"))
                && (have.contains("mouse") || have.contains("mice") || have.contains("trackpad"))) {
            return true;
        }
        if ((want.contains("keyboard") || want.contains("keypad"))
                && (have.contains("keyboard") || have.contains("keypad"))) {
            return true;
        }
        if ((want.contains("shoe") || want.contains("sneaker") || want.contains("footwear") || want.contains("running") || want.contains("boot"))
                && (have.contains("shoe") || have.contains("sneaker") || have.contains("footwear") || have.contains("running"))) {
            return true;
        }
        if ((want.contains("tablet") || want.contains("ipad") || want.contains("tab"))
                && (have.contains("tablet") || have.contains("ipad") || have.contains("tab"))) {
            return true;
        }
        return false;
    }

    @Override
    public String getProviderName() {
        return SOURCE;
    }

    private List<Product> buildCatalog() {
        List<Product> products = new ArrayList<>();

        // 1. Pendrives / USB Drives
        products.add(pendrive("p1", "SanDisk Ultra Flair 128GB", "SanDisk", "899", "1099",
                4.5, 12000, "Amazon", "128GB", "USB 3.0"));
        products.add(pendrive("p2", "Kingston DataTraveler 128GB", "Kingston", "849", "999",
                4.4, 8500, "Flipkart", "128GB", "USB 3.2"));
        products.add(pendrive("p3", "HP x796w 128GB", "HP", "799", "949",
                4.3, 4200, "Croma", "128GB", "USB 3.1"));
        products.add(pendrive("p4", "SanDisk Cruzer Blade 64GB", "SanDisk", "499", "599",
                4.2, 15000, "Amazon", "64GB", "USB 2.0"));
        products.add(pendrive("p5", "Samsung BAR Plus 128GB", "Samsung", "1099", "1299",
                4.6, 3100, "Amazon", "128GB", "USB 3.1"));

        // 2. Headphones / Earphones / Audio
        products.add(headphones("h1", "boAt Rockerz 450", "boAt", "1299", "1999",
                4.1, 45000, "Flipkart", true, "30 hours"));
        products.add(headphones("h2", "JBL Tune 510BT", "JBL", "1799", "2499",
                4.3, 22000, "Amazon", true, "40 hours"));
        products.add(headphones("h3", "Sony WH-CH520", "Sony", "2999", "3990",
                4.4, 9800, "Amazon", true, "50 hours"));
        products.add(headphones("h4", "Noise Two Pro", "Noise", "1499", "2299",
                4.0, 6700, "Flipkart", true, "35 hours"));
        products.add(headphones("h5", "boAt Bassheads 100", "boAt", "349", "599",
                4.0, 68000, "Amazon", false, "N/A"));
        products.add(headphones("h6", "Apple AirPods Pro (2nd Gen)", "Apple", "19990", "24900",
                4.8, 38000, "Amazon", true, "30 hours"));
        products.add(headphones("h7", "Sony WH-1000XM5 Wireless ANC", "Sony", "26990", "34990",
                4.7, 16000, "Amazon", true, "30 hours"));

        // 3. Laptops
        products.add(laptop("l1", "Lenovo IdeaPad Slim 3", "Lenovo", "35990", "49990",
                4.2, 3400, "Amazon", "8GB", "512GB SSD", "Intel Core i3"));
        products.add(laptop("l2", "HP 15s Ryzen 3", "HP", "38490", "51000",
                4.3, 4100, "Flipkart", "8GB", "512GB SSD", "AMD Ryzen 3"));
        products.add(laptop("l3", "ASUS Vivobook 15", "ASUS", "39990", "54990",
                4.4, 5200, "Amazon", "16GB", "512GB SSD", "Intel Core i3 12th Gen"));
        products.add(laptop("l4", "Acer Aspire 3", "Acer", "32990", "45999",
                4.1, 2100, "Croma", "8GB", "512GB SSD", "AMD Ryzen 3"));
        products.add(laptop("l5", "Apple MacBook Air M1", "Apple", "69990", "92900",
                4.8, 14200, "Amazon", "8GB", "256GB SSD", "Apple M1"));
        products.add(laptop("l6", "Apple MacBook Air M3", "Apple", "104990", "114900",
                4.8, 8500, "Amazon", "8GB", "256GB SSD", "Apple M3"));
        products.add(laptop("l7", "Apple MacBook Air M3 (16GB RAM)", "Apple", "124990", "134900",
                4.9, 3200, "Flipkart", "16GB", "512GB SSD", "Apple M3"));
        products.add(laptop("l8", "Apple MacBook Air M2", "Apple", "89990", "99900",
                4.7, 11000, "Croma", "8GB", "256GB SSD", "Apple M2"));

        // 4. Smartwatches
        products.add(smartwatch("w1", "Noise ColorFit Pulse 2 Max", "Noise", "1499", "3999",
                4.1, 18000, "Amazon", "1.85 inch", true, "10 days"));
        products.add(smartwatch("w2", "boAt Wave Call", "boAt", "1299", "3499",
                4.0, 24000, "Flipkart", "1.69 inch", true, "7 days"));
        products.add(smartwatch("w3", "Fire-Boltt Ninja Call Pro Plus", "Fire-Boltt", "1199", "3999",
                4.2, 19500, "Amazon", "1.83 inch", true, "8 days"));
        products.add(smartwatch("w4", "Amazfit Pop 3R AMOLED", "Amazfit", "3499", "4999",
                4.4, 6200, "Amazon", "1.43 inch AMOLED", true, "12 days"));
        products.add(smartwatch("w5", "Samsung Galaxy Watch 4", "Samsung", "8999", "14999",
                4.5, 9300, "Amazon", "1.4 inch Super AMOLED", true, "2 days"));

        // 5. Smartphones
        products.add(smartphone("s1", "Redmi 12 5G", "Xiaomi", "11999", "15999",
                4.2, 31000, "Amazon", "6GB", "128GB", "5000mAh"));
        products.add(smartphone("s2", "Samsung Galaxy M14 5G", "Samsung", "12490", "17990",
                4.2, 28000, "Amazon", "6GB", "128GB", "6000mAh"));
        products.add(smartphone("s3", "realme Narzo 60x 5G", "realme", "12999", "16999",
                4.3, 15000, "Flipkart", "6GB", "128GB", "5000mAh"));
        products.add(smartphone("s4", "OnePlus Nord CE 3 Lite 5G", "OnePlus", "17499", "19999",
                4.3, 42000, "Amazon", "8GB", "128GB", "5000mAh"));
        products.add(smartphone("s5", "Apple iPhone 15 (128 GB)", "Apple", "69999", "79900",
                4.7, 28000, "Amazon", "6GB", "128GB", "3349mAh"));
        products.add(smartphone("s6", "Samsung Galaxy S24 5G", "Samsung", "74999", "79999",
                4.6, 12000, "Amazon", "8GB", "256GB", "4000mAh"));

        // 6. Accessories (Mouse & Keyboard)
        products.add(accessory("m1", "Logitech B170 Wireless Mouse", "Logitech", "599", "895",
                4.4, 62000, "Amazon", "mouse", "Wireless 2.4GHz", "1000 DPI"));
        products.add(accessory("m2", "HP 150 Wireless Optical Mouse", "HP", "649", "999",
                4.2, 8500, "Flipkart", "mouse", "Wireless 2.4GHz", "1600 DPI"));
        products.add(accessory("m3", "Portronics Toad 23 Wireless Mouse", "Portronics", "349", "599",
                4.0, 11000, "Amazon", "mouse", "Wireless 2.4GHz", "1200 DPI"));
        products.add(accessory("k1", "Logitech K380 Bluetooth Keyboard", "Logitech", "2795", "3495",
                4.5, 14000, "Amazon", "keyboard", "Bluetooth Multi-device", "Compact"));
        products.add(accessory("k2", "Portronics Bubble Wireless Keyboard", "Portronics", "899", "1499",
                4.1, 5600, "Flipkart", "keyboard", "Wireless 2.4GHz & BT", "Full size"));

        // 7. Shoes & Footwear
        products.add(shoes("sh1", "Nike Revolution 6 Running Shoes", "Nike", "2995", "3995",
                4.3, 14200, "Amazon", "Running", "Mesh"));
        products.add(shoes("sh2", "Puma Smash V2 Sneakers", "Puma", "2499", "3499",
                4.2, 9800, "Flipkart", "Casual", "Leather"));
        products.add(shoes("sh3", "Adidas Dropline Running Shoes", "Adidas", "2799", "3999",
                4.4, 11300, "Amazon", "Running", "Breathable Mesh"));
        products.add(shoes("sh4", "Campus First Running Shoes", "Campus", "1299", "1899",
                4.1, 24000, "Amazon", "Sports", "Phylon Sole"));
        products.add(shoes("sh5", "Nike Air Zoom Pegasus 40", "Nike", "8995", "11495",
                4.6, 8900, "Amazon", "Running", "Engineered Mesh"));

        return products;
    }

    private String storeUrl(String store, String name) {
        String encoded = java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8);
        String s = store == null ? "" : store.toLowerCase(Locale.ROOT);
        if (s.contains("flipkart")) return "https://www.flipkart.com/search?q=" + encoded;
        if (s.contains("croma")) return "https://www.croma.com/searchB?q=" + encoded;
        return "https://www.amazon.in/s?k=" + encoded;
    }

    private Product pendrive(String id, String name, String brand, String price, String originalPrice,
                              double rating, int reviews, String store, String capacity, String usbStandard) {
        Map<String, String> specs = new LinkedHashMap<>();
        specs.put("capacity", capacity);
        specs.put("usbStandard", usbStandard);

        return Product.builder()
                .id(id)
                .name(name)
                .brand(brand)
                .category("pendrive")
                .description(name + " USB flash drive")
                .imageUrl(LiveProductSearchProvider.resolveImageUrl("pendrive", name))
                .price(new BigDecimal(price))
                .currency("INR")
                .originalPrice(new BigDecimal(originalPrice))
                .discountPercentage(discount(price, originalPrice))
                .rating(rating)
                .reviewCount(reviews)
                .store(store)
                .productUrl(storeUrl(store, name))
                .availability(Availability.IN_STOCK)
                .deliveryInfo("2-4 business days")
                .specifications(specs)
                .source(SOURCE)
                .build();
    }

    private Product headphones(String id, String name, String brand, String price, String originalPrice,
                                double rating, int reviews, String store, boolean wireless, String battery) {
        Map<String, String> specs = new LinkedHashMap<>();
        specs.put("wireless", String.valueOf(wireless));
        specs.put("batteryLife", battery);

        return Product.builder()
                .id(id)
                .name(name)
                .brand(brand)
                .category("headphones")
                .description(name + " headphones")
                .imageUrl(LiveProductSearchProvider.resolveImageUrl("headphones", name))
                .price(new BigDecimal(price))
                .currency("INR")
                .originalPrice(new BigDecimal(originalPrice))
                .discountPercentage(discount(price, originalPrice))
                .rating(rating)
                .reviewCount(reviews)
                .store(store)
                .productUrl(storeUrl(store, name))
                .availability(Availability.IN_STOCK)
                .deliveryInfo("2-4 business days")
                .specifications(specs)
                .source(SOURCE)
                .build();
    }

    private Product laptop(String id, String name, String brand, String price, String originalPrice,
                           double rating, int reviews, String store, String ram, String storage, String processor) {
        Map<String, String> specs = new LinkedHashMap<>();
        specs.put("ram", ram);
        specs.put("storage", storage);
        specs.put("processor", processor);

        return Product.builder()
                .id(id)
                .name(name)
                .brand(brand)
                .category("laptop")
                .description(name + " " + processor + " " + ram + " " + storage)
                .imageUrl(LiveProductSearchProvider.resolveImageUrl("laptop", name))
                .price(new BigDecimal(price))
                .currency("INR")
                .originalPrice(new BigDecimal(originalPrice))
                .discountPercentage(discount(price, originalPrice))
                .rating(rating)
                .reviewCount(reviews)
                .store(store)
                .productUrl(storeUrl(store, name))
                .availability(Availability.IN_STOCK)
                .deliveryInfo("1-3 business days")
                .specifications(specs)
                .source(SOURCE)
                .build();
    }

    private Product smartwatch(String id, String name, String brand, String price, String originalPrice,
                              double rating, int reviews, String store, String display, boolean btCalling, String battery) {
        Map<String, String> specs = new LinkedHashMap<>();
        specs.put("display", display);
        specs.put("bluetoothCalling", String.valueOf(btCalling));
        specs.put("batteryLife", battery);

        return Product.builder()
                .id(id)
                .name(name)
                .brand(brand)
                .category("smartwatch")
                .description(name + " with " + display + " display")
                .imageUrl(LiveProductSearchProvider.resolveImageUrl("smartwatch", name))
                .price(new BigDecimal(price))
                .currency("INR")
                .originalPrice(new BigDecimal(originalPrice))
                .discountPercentage(discount(price, originalPrice))
                .rating(rating)
                .reviewCount(reviews)
                .store(store)
                .productUrl(storeUrl(store, name))
                .availability(Availability.IN_STOCK)
                .deliveryInfo("2-4 business days")
                .specifications(specs)
                .source(SOURCE)
                .build();
    }

    private Product smartphone(String id, String name, String brand, String price, String originalPrice,
                               double rating, int reviews, String store, String ram, String storage, String battery) {
        Map<String, String> specs = new LinkedHashMap<>();
        specs.put("ram", ram);
        specs.put("storage", storage);
        specs.put("battery", battery);

        return Product.builder()
                .id(id)
                .name(name)
                .brand(brand)
                .category("smartphone")
                .description(name + " (" + ram + " RAM, " + storage + ")")
                .imageUrl(LiveProductSearchProvider.resolveImageUrl("smartphone", name))
                .price(new BigDecimal(price))
                .currency("INR")
                .originalPrice(new BigDecimal(originalPrice))
                .discountPercentage(discount(price, originalPrice))
                .rating(rating)
                .reviewCount(reviews)
                .store(store)
                .productUrl(storeUrl(store, name))
                .availability(Availability.IN_STOCK)
                .deliveryInfo("1-2 business days")
                .specifications(specs)
                .source(SOURCE)
                .build();
    }

    private Product accessory(String id, String name, String brand, String price, String originalPrice,
                              double rating, int reviews, String store, String category, String specKey, String specVal) {
        Map<String, String> specs = new LinkedHashMap<>();
        specs.put("connectivity", specKey);
        specs.put("details", specVal);

        return Product.builder()
                .id(id)
                .name(name)
                .brand(brand)
                .category(category)
                .description(name + " - " + specVal)
                .imageUrl(LiveProductSearchProvider.resolveImageUrl(category, name))
                .price(new BigDecimal(price))
                .currency("INR")
                .originalPrice(new BigDecimal(originalPrice))
                .discountPercentage(discount(price, originalPrice))
                .rating(rating)
                .reviewCount(reviews)
                .store(store)
                .productUrl(storeUrl(store, name))
                .availability(Availability.IN_STOCK)
                .deliveryInfo("2-3 business days")
                .specifications(specs)
                .source(SOURCE)
                .build();
    }

    private Product shoes(String id, String name, String brand, String price, String originalPrice,
                          double rating, int reviews, String store, String type, String material) {
        Map<String, String> specs = new LinkedHashMap<>();
        specs.put("type", type);
        specs.put("material", material);

        return Product.builder()
                .id(id)
                .name(name)
                .brand(brand)
                .category("shoes")
                .description(name + " (" + type + ")")
                .imageUrl(LiveProductSearchProvider.resolveImageUrl("shoes", name))
                .price(new BigDecimal(price))
                .currency("INR")
                .originalPrice(new BigDecimal(originalPrice))
                .discountPercentage(discount(price, originalPrice))
                .rating(rating)
                .reviewCount(reviews)
                .store(store)
                .productUrl(storeUrl(store, name))
                .availability(Availability.IN_STOCK)
                .deliveryInfo("2-4 business days")
                .specifications(specs)
                .source(SOURCE)
                .build();
    }

    private Double discount(String price, String originalPrice) {
        double p = Double.parseDouble(price);
        double o = Double.parseDouble(originalPrice);
        if (o <= 0) return null;
        return Math.round(((o - p) / o) * 1000.0) / 10.0;
    }
}
