package com.shoppingagent.product.normalization;

import com.shoppingagent.search.model.Product;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Module 6.1 — Standardizes product data across providers.
 * Normalizes names, brands, categories to consistent formats.
 */
@Service
public class ProductNormalizationService {

    private static final Map<String, String> BRAND_ALIASES = Map.ofEntries(
            Map.entry("sandisk", "SanDisk"),
            Map.entry("san disk", "SanDisk"),
            Map.entry("kingston", "Kingston"),
            Map.entry("hp", "HP"),
            Map.entry("hewlett packard", "HP"),
            Map.entry("samsung", "Samsung"),
            Map.entry("boat", "boAt"),
            Map.entry("b0at", "boAt"),
            Map.entry("jbl", "JBL"),
            Map.entry("sony", "Sony"),
            Map.entry("noise", "Noise"),
            Map.entry("realme", "Realme"),
            Map.entry("apple", "Apple"),
            Map.entry("lenovo", "Lenovo"),
            Map.entry("asus", "ASUS"),
            Map.entry("dell", "Dell"),
            Map.entry("acer", "Acer"),
            Map.entry("xiaomi", "Xiaomi"),
            Map.entry("mi", "Xiaomi"),
            Map.entry("oneplus", "OnePlus"),
            Map.entry("one plus", "OnePlus")
    );

    private static final Map<String, String> CATEGORY_ALIASES = Map.ofEntries(
            Map.entry("pen drive", "pendrive"),
            Map.entry("usb drive", "pendrive"),
            Map.entry("flash drive", "pendrive"),
            Map.entry("usb stick", "pendrive"),
            Map.entry("thumb drive", "pendrive"),
            Map.entry("earphones", "headphones"),
            Map.entry("earbuds", "headphones"),
            Map.entry("earbud", "headphones"),
            Map.entry("headset", "headphones"),
            Map.entry("notebook", "laptop"),
            Map.entry("smart watch", "smartwatch"),
            Map.entry("mobile", "phone"),
            Map.entry("smartphone", "phone"),
            Map.entry("cell phone", "phone")
    );

    /**
     * Normalize a list of products — standardize brand names, categories, and trim whitespace.
     */
    public List<Product> normalize(List<Product> products) {
        return products.stream()
                .map(this::normalizeProduct)
                .toList();
    }

    private Product normalizeProduct(Product product) {
        return product.toBuilder()
                .name(normalizeName(product.getName()))
                .brand(normalizeBrand(product.getBrand()))
                .category(normalizeCategory(product.getCategory()))
                .build();
    }

    private String normalizeName(String name) {
        if (name == null) return null;
        return name.trim().replaceAll("\\s+", " ");
    }

    String normalizeBrand(String brand) {
        if (brand == null) return null;
        String key = brand.trim().toLowerCase(Locale.ROOT);
        return BRAND_ALIASES.getOrDefault(key, brand.trim());
    }

    String normalizeCategory(String category) {
        if (category == null) return null;
        String key = category.trim().toLowerCase(Locale.ROOT);
        return CATEGORY_ALIASES.getOrDefault(key, key);
    }
}
