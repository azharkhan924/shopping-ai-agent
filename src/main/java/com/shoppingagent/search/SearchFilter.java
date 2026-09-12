package com.shoppingagent.search;

import com.shoppingagent.ai.model.ShoppingQuery;
import com.shoppingagent.search.model.Product;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Module 5.7 — basic, deterministic filtering only. No ranking, no scoring, no
 * "best match" logic — that's Module 6/7. This just removes products that
 * clearly violate what the user explicitly asked for.
 */
@Component
public class SearchFilter {

    public List<Product> apply(List<Product> products, ShoppingQuery query) {
        return products.stream()
                .filter(p -> matchesCategory(p, query))
                .filter(p -> matchesRequiredSpecs(p, query))
                .filter(p -> withinPriceRange(p, query))
                .filter(p -> respectsBrandRestrictions(p, query))
                .filter(p -> respectsStoreRestrictions(p, query))
                .filter(p -> meetsMinimumRating(p, query))
                .toList();
    }

    private boolean matchesCategory(Product p, ShoppingQuery query) {
        if (isBlank(query.getCategory())) return true;
        if (p.getCategory() == null) return true; // unknown category -> don't exclude
        String want = query.getCategory().toLowerCase(Locale.ROOT).trim();
        String have = p.getCategory().toLowerCase(Locale.ROOT).trim();
        if (have.contains(want) || want.contains(have)) return true;

        String wantClean = want.replaceAll("[\\s_-]", "");
        String haveClean = have.replaceAll("[\\s_-]", "");
        if (haveClean.contains(wantClean) || wantClean.contains(haveClean)) return true;

        if (matchesSynonyms(wantClean, haveClean)) return true;

        // Check if product name contains any significant word from the category
        String prodName = p.getName() != null ? p.getName().toLowerCase(Locale.ROOT) : "";
        for (String word : want.split("[\\s/,-]+")) {
            if (word.length() >= 3 && prodName.contains(word)) {
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

    private boolean matchesRequiredSpecs(Product p, ShoppingQuery query) {
        Map<String, String> required = query.getRequiredSpecifications();
        if (required == null || required.isEmpty()) return true;

        for (Map.Entry<String, String> entry : required.entrySet()) {
            String key = entry.getKey();
            String wantedValue = entry.getValue();
            if (key == null || wantedValue == null || wantedValue.isBlank()) continue;

            String actual = findSpec(p, key);
            if (actual != null) {
                if (!specMatches(actual, wantedValue)) {
                    // If spec doesn't match directly, check if product name explicitly mentions it (e.g. M3)
                    String pName = p.getName() != null ? p.getName().toLowerCase(Locale.ROOT) : "";
                    if (!pName.contains(wantedValue.toLowerCase(Locale.ROOT).trim())) {
                        return false;
                    }
                }
            } else {
                // If spec key wasn't in specifications map, we don't penalize missing unknown specs
            }
        }
        return true;
    }

    private boolean specMatches(String actual, String wanted) {
        if (actual == null || wanted == null) return false;
        String a = actual.toLowerCase(Locale.ROOT).trim();
        String w = wanted.toLowerCase(Locale.ROOT).trim();
        if (a.equalsIgnoreCase(w)) return true;
        if (a.contains(w) || w.contains(a)) return true;

        // Strip non-alphanumerics (e.g. "16gb" vs "16 gb", "m-3" vs "m3")
        String aClean = a.replaceAll("[^a-z0-9]", "");
        String wClean = w.replaceAll("[^a-z0-9]", "");
        if (!aClean.isEmpty() && !wClean.isEmpty()) {
            if (aClean.contains(wClean) || wClean.contains(aClean)) return true;
        }
        return false;
    }

    private String findSpec(Product p, String key) {
        if (p.getSpecifications() == null || key == null) return null;
        String kLower = key.toLowerCase(Locale.ROOT).trim();
        for (Map.Entry<String, String> spec : p.getSpecifications().entrySet()) {
            if (spec.getKey() != null) {
                String specKey = spec.getKey().toLowerCase(Locale.ROOT).trim();
                if (specKey.equalsIgnoreCase(kLower)) {
                    return spec.getValue();
                }
                // Key synonyms (e.g. chip == processor == cpu, ram == memory, ssd == storage)
                if (isKeySynonym(kLower, specKey)) {
                    return spec.getValue();
                }
            }
        }
        return null;
    }

    private boolean isKeySynonym(String k1, String k2) {
        if ((k1.contains("chip") || k1.contains("cpu") || k1.contains("processor") || k1.contains("soc"))
                && (k2.contains("chip") || k2.contains("cpu") || k2.contains("processor") || k2.contains("soc"))) return true;
        if ((k1.contains("ram") || k1.contains("memory"))
                && (k2.contains("ram") || k2.contains("memory"))) return true;
        if ((k1.contains("storage") || k1.contains("ssd") || k1.contains("rom") || k1.contains("hdd") || k1.contains("disk"))
                && (k2.contains("storage") || k2.contains("ssd") || k2.contains("rom") || k2.contains("hdd") || k2.contains("disk"))) return true;
        return false;
    }

    private boolean withinPriceRange(Product p, ShoppingQuery query) {
        if (p.getPrice() == null) return true; // unknown price -> don't exclude
        double price = p.getPrice().doubleValue();
        if (query.getMaxPrice() != null && price > query.getMaxPrice()) return false;
        if (query.getMinPrice() != null && price < query.getMinPrice()) return false;
        return true;
    }

    private boolean respectsBrandRestrictions(Product p, ShoppingQuery query) {
        if (p.getBrand() == null) return true;
        String brand = p.getBrand().toLowerCase(Locale.ROOT).trim();

        if (query.getExcludedBrands() != null) {
            for (String excluded : query.getExcludedBrands()) {
                if (excluded != null) {
                    String ex = excluded.toLowerCase(Locale.ROOT).trim();
                    if (brand.equals(ex) || brand.contains(ex) || ex.contains(brand)) {
                        return false;
                    }
                }
            }
        }

        List<String> requiredBrands = query.getBrands();
        if (requiredBrands == null || requiredBrands.isEmpty()) return true;
        return requiredBrands.stream()
                .filter(java.util.Objects::nonNull)
                .anyMatch(b -> {
                    String bLower = b.toLowerCase(Locale.ROOT).trim();
                    return brand.equals(bLower) || brand.contains(bLower) || bLower.contains(brand);
                });
    }

    private boolean respectsStoreRestrictions(Product p, ShoppingQuery query) {
        if (p.getStore() == null) return true;
        String store = p.getStore().toLowerCase(Locale.ROOT).trim();

        if (query.getExcludedStores() != null) {
            for (String excluded : query.getExcludedStores()) {
                if (excluded != null && store.equals(excluded.toLowerCase(Locale.ROOT).trim())) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean meetsMinimumRating(Product p, ShoppingQuery query) {
        if (query.getMinimumRating() == null) return true;
        if (p.getRating() == null) return true; // unknown rating -> don't exclude
        return p.getRating() >= query.getMinimumRating();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
