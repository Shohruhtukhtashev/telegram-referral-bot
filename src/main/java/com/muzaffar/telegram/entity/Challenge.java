package com.muzaffar.telegram.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "challenges")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Challenge {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 200)
    private String name;
    
    @Column(nullable = false)
    @Builder.Default
    private Integer requiredReferralCount = 5;
    
    @Column(length = 500)
    private String privateChannelLink;  // Deprecated: kept for backward compatibility
    
    @Column(length = 100)
    private String privateChannelId;  // Channel ID for creating invite links (e.g., -1001234567890)
    
    @Column(length = 500)
    private String videoGuideUrl;  // Channel link containing video guide
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean isMain = false;  // Only one challenge should be marked as main
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
