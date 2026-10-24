package com.muzaffar.telegram.service;

import com.muzaffar.telegram.entity.Challenge;
import com.muzaffar.telegram.entity.ChallengeChannel;
import com.muzaffar.telegram.entity.ChallengeUser;
import com.muzaffar.telegram.repository.ChallengeChannelRepository;
import com.muzaffar.telegram.repository.ChallengeRepository;
import com.muzaffar.telegram.repository.ChallengeUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengeService {
    
    private final ChallengeRepository challengeRepository;
    private final ChallengeChannelRepository challengeChannelRepository;
    private final ChallengeUserRepository challengeUserRepository;

    // High-performance thread-safe in-memory cache
    private volatile Challenge cachedMainChallenge = null;
    private volatile long cachedMainChallengeTime = 0;
    private final Map<Long, List<ChallengeChannel>> activeChannelsCache = new ConcurrentHashMap<>();
    private final Map<Long, Long> activeChannelsCacheTime = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 60_000; // 60 seconds TTL

    public void invalidateCache() {
        cachedMainChallenge = null;
        activeChannelsCache.clear();
        activeChannelsCacheTime.clear();
        log.debug("ChallengeService in-memory cache invalidated");
    }
    
    /**
     * Create a new challenge
     */
    @Transactional
    public Challenge createChallenge(String name, Integer requiredReferralCount, String privateChannelId) {
        Challenge challenge = Challenge.builder()
                .name(name)
                .requiredReferralCount(requiredReferralCount)
                .privateChannelId(privateChannelId)
                .isActive(true)
                .build();
        
        Challenge saved = challengeRepository.save(challenge);
        invalidateCache();
        log.info("Created new challenge: {} (ID: {})", name, saved.getId());
        return saved;
    }
    
    /**
     * Get challenge by ID
     */
    public Optional<Challenge> getChallengeById(Long challengeId) {
        return challengeRepository.findById(challengeId);
    }
    
    /**
     * Get all challenges
     */
    public List<Challenge> getAllChallenges() {
        return challengeRepository.findAllOrderByCreatedAtDesc();
    }
    
    /**
     * Get all active challenges
     */
    public List<Challenge> getActiveChallenges() {
        return challengeRepository.findAllActiveChallenges();
    }
    
    /**
     * Get the main challenge (for new users) - served from cache
     */
    public Optional<Challenge> getDefaultChallenge() {
        long now = System.currentTimeMillis();
        if (cachedMainChallenge != null && (now - cachedMainChallengeTime) < CACHE_TTL_MS) {
            return Optional.of(cachedMainChallenge);
        }
        Optional<Challenge> main = challengeRepository.findMainChallenge();
        main.ifPresent(c -> {
            cachedMainChallenge = c;
            cachedMainChallengeTime = now;
        });
        return main;
    }
    
    /**
     * Set a challenge as the main challenge (unsets all others)
     */
    @Transactional
    public void setMainChallenge(Long challengeId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        
        if (!challenge.getIsActive()) {
            throw new IllegalArgumentException("Cannot set inactive challenge as main");
        }
        
        // Unset all other challenges as main
        List<Challenge> allChallenges = challengeRepository.findAll();
        for (Challenge c : allChallenges) {
            if (c.getIsMain() && !c.getId().equals(challengeId)) {
                c.setIsMain(false);
                challengeRepository.save(c);
            }
        }
        
        // Set this challenge as main
        challenge.setIsMain(true);
        challengeRepository.save(challenge);
        invalidateCache();
        log.info("Set challenge {} as main challenge", challengeId);
    }
    
    /**
     * Update private channel ID for generating invite links
     */
    @Transactional
    public Challenge updatePrivateChannelId(Long challengeId, String privateChannelId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        
        challenge.setPrivateChannelId(privateChannelId);
        
        Challenge updated = challengeRepository.save(challenge);
        invalidateCache();
        log.info("Updated privateChannelId for challenge {}: {}", challengeId, privateChannelId);
        return updated;
    }
    
    /**
     * Update required referral count for a challenge
     */
    @Transactional
    public Challenge updateRequiredReferralCount(Long challengeId, Integer requiredReferralCount) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        
        challenge.setRequiredReferralCount(requiredReferralCount);
        
        Challenge updated = challengeRepository.save(challenge);
        invalidateCache();
        log.info("Updated requiredReferralCount for challenge {}: {}", challengeId, requiredReferralCount);
        return updated;
    }
    
    /**
     * Activate/deactivate challenge
     */
    @Transactional
    public void toggleChallengeStatus(Long challengeId, boolean isActive) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        
        challenge.setIsActive(isActive);
        challengeRepository.save(challenge);
        invalidateCache();
        log.info("Challenge {} is now {}", challengeId, isActive ? "active" : "inactive");
    }
    
    /**
     * Delete challenge (careful - this should be rare)
     */
    @Transactional
    public void deleteChallenge(Long challengeId) {
        if (!challengeRepository.existsById(challengeId)) {
            throw new IllegalArgumentException("Challenge not found: " + challengeId);
        }
        
        challengeRepository.deleteById(challengeId);
        invalidateCache();
        log.warn("Deleted challenge: {}", challengeId);
    }
    
    /**
     * Add channel to challenge
     */
    @Transactional
    public ChallengeChannel addChannelToChallenge(Long challengeId, String channelId, 
                                                   String channelName, String channelUsername) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        
        // Check if channel already exists for this challenge
        if (challengeChannelRepository.existsByChallengeAndChannelId(challenge, channelId)) {
            throw new IllegalArgumentException("Channel already exists in this challenge");
        }
        
        ChallengeChannel challengeChannel = ChallengeChannel.builder()
                .challenge(challenge)
                .channelId(channelId)
                .channelName(channelName)
                .channelUsername(channelUsername)
                .isActive(true)
                .build();
        
        ChallengeChannel saved = challengeChannelRepository.save(challengeChannel);
        invalidateCache();
        log.info("Added channel {} to challenge {}", channelName, challengeId);
        return saved;
    }
    
    /**
     * Get channels for a challenge
     */
    public List<ChallengeChannel> getChannelsForChallenge(Long challengeId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        return challengeChannelRepository.findByChallenge(challenge);
    }
    
    /**
     * Get active channels for a challenge - served from cache
     */
    public List<ChallengeChannel> getActiveChannelsForChallenge(Long challengeId) {
        long now = System.currentTimeMillis();
        Long cacheTime = activeChannelsCacheTime.get(challengeId);
        if (cacheTime != null && (now - cacheTime) < CACHE_TTL_MS) {
            List<ChallengeChannel> cached = activeChannelsCache.get(challengeId);
            if (cached != null) {
                return cached;
            }
        }
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        List<ChallengeChannel> channels = challengeChannelRepository.findActiveChannelsByChallenge(challenge);
        activeChannelsCache.put(challengeId, channels);
        activeChannelsCacheTime.put(challengeId, now);
        return channels;
    }
    
    /**
     * Remove channel from challenge
     */
    @Transactional
    public boolean removeChannelFromChallenge(Long challengeId, String channelId) {
        Optional<ChallengeChannel> channelOpt = challengeChannelRepository
                .findByChallengeIdAndChannelId(challengeId, channelId);
        
        if (channelOpt.isPresent()) {
            challengeChannelRepository.delete(channelOpt.get());
            invalidateCache();
            log.info("Removed channel {} from challenge {}", channelId, challengeId);
            return true;
        }
        return false;
    }
    
    /**
     * Get statistics for a challenge
     */
    public ChallengeStats getChallengeStats(Long challengeId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        
        long totalUsers = challengeUserRepository.countUsersByChallenge(challenge);
        long completedUsers = challengeUserRepository.countCompletedUsersByChallenge(challenge);
        long totalChannels = challengeChannelRepository.countActiveChannelsByChallenge(challenge);
        
        return new ChallengeStats(totalUsers, completedUsers, totalChannels);
    }

    /**
     * Update video guide URL for a challenge
     */
    @Transactional
    public void updateVideoGuideUrl(Long challengeId, String videoGuideUrl) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        challenge.setVideoGuideUrl(videoGuideUrl != null && !videoGuideUrl.trim().isEmpty() ? videoGuideUrl.trim() : null);
        challengeRepository.save(challenge);
        log.info("Updated video guide URL for challenge {}: {}", challengeId, videoGuideUrl);
    }

    /**
     * Get video guide URL for a challenge (or fall back to default challenge or https://t.me/Bot_uchun_Videoo)
     */
    public Optional<String> getVideoGuideUrl(Long challengeId) {
        if (challengeId != null) {
            Optional<Challenge> challengeOpt = challengeRepository.findById(challengeId);
            if (challengeOpt.isPresent() && challengeOpt.get().getVideoGuideUrl() != null 
                    && !challengeOpt.get().getVideoGuideUrl().trim().isEmpty()) {
                return Optional.of(challengeOpt.get().getVideoGuideUrl().trim());
            }
        }
        Optional<String> defaultUrl = getDefaultChallenge()
                .map(Challenge::getVideoGuideUrl)
                .filter(url -> url != null && !url.trim().isEmpty());
        if (defaultUrl.isPresent()) {
            return defaultUrl;
        }
        return Optional.of("https://t.me/Bot_uchun_Videoo");
    }
    
    /**
     * DTO for challenge statistics
     */
    public record ChallengeStats(long totalUsers, long completedUsers, long totalChannels) {}
}
