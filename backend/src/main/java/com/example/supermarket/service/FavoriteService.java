package com.example.supermarket.service;

import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.FavoriteResponse;
import com.example.supermarket.dto.PriceAlertResponse;
import com.example.supermarket.entity.PriceAlert;
import com.example.supermarket.entity.Product;
import com.example.supermarket.entity.UserFavorite;
import com.example.supermarket.exception.ResourceNotFoundException;
import com.example.supermarket.repository.PriceAlertRepository;
import com.example.supermarket.repository.ProductRepository;
import com.example.supermarket.repository.UserFavoriteRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 收藏 + 降价提醒。
 *
 * 降价检测采用「读时同步」(sync-on-read) 而不是在每个改价入口埋钩子：
 * 收藏时的售价 priceAtFavorite 就是基线，只要当前售价低于它就生成/更新提醒。
 * 好处是任何改价路径（后台编辑、种子自愈、直接改库）都能被捕捉到，且价格回升后提醒会自动失效。
 */
@Service
public class FavoriteService {

    private static final byte NOT_DELETED = 0;
    private static final int MAX_PAGE_SIZE = 100;

    private final UserFavoriteRepository favoriteRepository;
    private final PriceAlertRepository alertRepository;
    private final ProductRepository productRepository;

    public FavoriteService(UserFavoriteRepository favoriteRepository,
                           PriceAlertRepository alertRepository,
                           ProductRepository productRepository) {
        this.favoriteRepository = favoriteRepository;
        this.alertRepository = alertRepository;
        this.productRepository = productRepository;
    }

    /** 收藏（幂等：重复收藏不改变基线价，避免覆盖用户第一次看到的价格） */
    @Transactional
    public FavoriteResponse add(Long userId, Long productId) {
        Product product = requireProduct(productId);
        if (!favoriteRepository.existsByUserIdAndProductId(userId, productId)) {
            UserFavorite created = new UserFavorite();
            created.setUserId(userId);
            created.setProductId(productId);
            created.setPriceAtFavorite(product.getPrice());
            favoriteRepository.saveAndFlush(created);
        }
        syncAlerts(userId);
        UserFavorite favorite = favoriteRepository.findByUserIdAndProductId(userId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Favorite not found"));
        return FavoriteResponse.from(favorite, product);
    }

    /** 取消收藏（连带清掉该商品的降价提醒） */
    @Transactional
    public boolean remove(Long userId, Long productId) {
        if (!favoriteRepository.existsByUserIdAndProductId(userId, productId)) {
            return false;
        }
        favoriteRepository.deleteByUserIdAndProductId(userId, productId);
        alertRepository.findByUserIdAndProductId(userId, productId).ifPresent(alertRepository::delete);
        return true;
    }

    @Transactional
    public PageResponse<FavoriteResponse> listFavorites(Long userId, int page, int size) {
        // 先同步一次：清掉「商品已删除」的收藏与「价格已回升」的陈旧提醒，保证分页 total 准确
        syncAlerts(userId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<UserFavorite> pageData = favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        Map<Long, Product> products = loadProducts(pageData.getContent().stream()
                .map(UserFavorite::getProductId).collect(Collectors.toList()));
        List<FavoriteResponse> items = pageData.getContent().stream()
                .filter(favorite -> products.containsKey(favorite.getProductId()))
                .map(favorite -> FavoriteResponse.from(favorite, products.get(favorite.getProductId())))
                .collect(Collectors.toList());
        return PageResponse.of(items, safePage, safeSize, pageData.getTotalElements());
    }

    /** 供前端渲染「已收藏」心形状态：一次性拿到全部收藏的商品 id */
    @Transactional(readOnly = true)
    public List<Long> favoriteIds(Long userId) {
        return favoriteRepository.findByUserId(userId).stream()
                .map(UserFavorite::getProductId)
                .collect(Collectors.toList());
    }

    @Transactional
    public PageResponse<PriceAlertResponse> listAlerts(Long userId, int page, int size) {
        syncAlerts(userId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PriceAlert> pageData = alertRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        Map<Long, Product> products = loadProducts(pageData.getContent().stream()
                .map(PriceAlert::getProductId).collect(Collectors.toList()));
        List<PriceAlertResponse> items = pageData.getContent().stream()
                .map(alert -> PriceAlertResponse.from(alert, products.get(alert.getProductId())))
                .collect(Collectors.toList());
        return PageResponse.of(items, safePage, safeSize, pageData.getTotalElements());
    }

    @Transactional
    public long unreadCount(Long userId) {
        syncAlerts(userId);
        return alertRepository.countByUserIdAndIsRead(userId, PriceAlert.UNREAD);
    }

    @Transactional
    public int markAllRead(Long userId) {
        List<PriceAlert> unread = alertRepository.findByUserIdAndIsRead(userId, PriceAlert.UNREAD);
        unread.forEach(alert -> alert.setIsRead(PriceAlert.READ));
        alertRepository.saveAll(unread);
        return unread.size();
    }

    /**
     * 按当前售价重建该用户的降价提醒：
     * - 现价 < 收藏价 → 生成提醒；价格再次下探则刷新并重新标为未读
     * - 现价 >= 收藏价（回升）或商品已删除 → 删除提醒（收藏本身在商品删除时也一并清理）
     */
    @Transactional
    public void syncAlerts(Long userId) {
        List<UserFavorite> favorites = favoriteRepository.findByUserId(userId);
        if (favorites.isEmpty()) {
            return;
        }
        Map<Long, Product> products = loadProducts(favorites.stream()
                .map(UserFavorite::getProductId).collect(Collectors.toList()));
        for (UserFavorite favorite : favorites) {
            Product product = products.get(favorite.getProductId());
            PriceAlert existing = alertRepository
                    .findByUserIdAndProductId(userId, favorite.getProductId()).orElse(null);
            if (product == null) {
                // 商品已被删除：收藏与提醒都不该继续存在
                if (existing != null) {
                    alertRepository.delete(existing);
                }
                favoriteRepository.delete(favorite);
                continue;
            }
            BigDecimal base = favorite.getPriceAtFavorite();
            BigDecimal current = product.getPrice();
            boolean dropped = base != null && current != null && current.compareTo(base) < 0;
            if (!dropped) {
                if (existing != null) {
                    alertRepository.delete(existing);
                }
                continue;
            }
            BigDecimal drop = base.subtract(current).setScale(2, RoundingMode.HALF_UP);
            if (existing == null) {
                PriceAlert alert = new PriceAlert();
                alert.setUserId(userId);
                alert.setProductId(favorite.getProductId());
                alert.setOldPrice(base);
                alert.setNewPrice(current);
                alert.setDropAmount(drop);
                alert.setIsRead(PriceAlert.UNREAD);
                alertRepository.save(alert);
                continue;
            }
            boolean droppedAgain = existing.getNewPrice() == null
                    || existing.getNewPrice().compareTo(current) != 0;
            existing.setOldPrice(base);
            existing.setNewPrice(current);
            existing.setDropAmount(drop);
            if (droppedAgain) {
                existing.setIsRead(PriceAlert.UNREAD);
            }
            alertRepository.save(existing);
        }
    }

    private Map<Long, Product> loadProducts(Collection<Long> productIds) {
        Map<Long, Product> map = new HashMap<>();
        if (productIds == null || productIds.isEmpty()) {
            return map;
        }
        productRepository.findByIdInAndDeleted(productIds, NOT_DELETED)
                .forEach(product -> map.put(product.getId(), product));
        return map;
    }

    private Product requireProduct(Long productId) {
        if (productId == null) {
            throw new ResourceNotFoundException("Product not found");
        }
        return productRepository.findByIdAndDeleted(productId, NOT_DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }
}
