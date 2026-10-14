package dev.techmart.catalog.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "product_images")
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    protected ProductImage() {
    }

    public ProductImage(String url, int sortOrder) {
        this.url = url;
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public String getUrl() { return url; }
    public Integer getSortOrder() { return sortOrder; }

    void attachTo(Product product) {
        this.product = product;
    }
}
