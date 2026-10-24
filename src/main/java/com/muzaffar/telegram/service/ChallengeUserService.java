package com.muzaffar.telegram.service;

import com.muzaffar.telegram.entity.Challenge;
import com.muzaffar.telegram.entity.ChallengeReferral;
import com.muzaffar.telegram.entity.ChallengeUser;
import com.muzaffar.telegram.repository.ChallengeReferralRepository;
import com.muzaffar.telegram.repository.ChallengeRepository;
import com.muzaffar.telegram.repository.ChallengeUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengeUserService {
    
    private final ChallengeUserRepository challengeUserRepository;
    private final ChallengeReferralRepository challengeReferralRepository;
    private final ChallengeRepository challengeRepository;
    
    /**
     * Add or get user in a challenge
     */
    @Transactional
    public ChallengeUser addUserToChallenge(Long challengeId, Long userChatId, String username,
                                           String firstName, String lastName) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        
        // Check if user already exists in this challenge
        Optional<ChallengeUser> existingUser = challengeUserRepository
                .findByChallengeAndUserChatId(challenge, userChatId);
        
        if (existingUser.isPresent()) {
            return existingUser.get();
        }
        
        // Generate unique referral code
        String referralCode = generateUniqueReferralCode(challengeId, userChatId);
        
        ChallengeUser challengeUser = ChallengeUser.builder()
                .challenge(challenge)
                .userChatId(userChatId)
                .username(username)
                .firstName(firstName)
                .lastName(lastName)
                .referralCode(referralCode)
                .referralCount(0)
                .hasVerifiedChannels(false)
                .isCompleted(false)
                .hasReceivedReward(false)
                .build();
        
        ChallengeUser saved = challengeUserRepository.save(challengeUser);
        log.info("Added user {} to challenge {}", userChatId, challengeId);
        return saved;
    }
    
    /**
     * Generate unique referral code
     */
    private String generateUniqueReferralCode(Long challengeId, Long userChatId) {
        // Format: C{challengeId}_U{userChatId}_{randomPart}
        String baseCode = String.format("C%d_U%d_%s", 
                challengeId, 
                userChatId, 
                UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        
        // Ensure uniqueness
        while (challengeUserRepository.findByReferralCode(baseCode).isPresent()) {
            baseCode = String.format("C%d_U%d_%s", 
                    challengeId, 
                    userChatId, 
                    UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        }
        
        return baseCode;
    }
    
    /**
     * Get user by referral code
     */
    public Optional<ChallengeUser> getUserByReferralCode(String referralCode) {
        return challengeUserRepository.findByReferralCode(referralCode);
    }
    
    /**
     * Get user in a specific challenge
     */
    public Optional<ChallengeUser> getUserInChallenge(Long challengeId, Long userChatId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        return challengeUserRepository.findByChallengeAndUserChatId(challenge, userChatId);
    }
    
    /**
     * Mark user as verified channels
     */
    @Transactional
    public void markUserAsVerified(Long challengeId, Long userChatId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        
        ChallengeUser user = challengeUserRepository.findByChallengeAndUserChatId(challenge, userChatId)
                .orElseThrow(() -> new IllegalArgumentException("User not found in challenge"));
        
        user.setHasVerifiedChannels(true);
        challengeUserRepository.save(user);
        log.info("User {} verified channels in challenge {}", userChatId, challengeId);
    }
    
    /**
     * Mark user as NOT verified when they leave required channels
     */
    @Transactional
    public void markUserAsUnverified(Long challengeId, Long userChatId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        
        ChallengeUser user = challengeUserRepository.findByChallengeAndUserChatId(challenge, userChatId)
                .orElseThrow(() -> new IllegalArgumentException("User not found in challenge"));
        
        if (Boolean.TRUE.equals(user.getHasVerifiedChannels())) {
            user.setHasVerifiedChannels(false);
            challengeUserRepository.save(user);
            log.info("User {} left required channels in challenge {}. Marked as unverified.", userChatId, challengeId);
        }
    }
    
    /**
     * Increment referral count
     */
    @Transactional
    public void incrementReferralCount(Long challengeId, Long userChatId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        
        ChallengeUser user = challengeUserRepository.findByChallengeAndUserChatId(challenge, userChatId)
                .orElseThrow(() -> new IllegalArgumentException("User not found in challenge"));
        
        user.setReferralCount(user.getReferralCount() + 1);
        
        // Check if user completed challenge
        if (user.getReferralCount() >= challenge.getRequiredReferralCount() && !user.getIsCompleted()) {
            user.setIsCompleted(true);
            user.setCompletedAt(LocalDateTime.now());
            log.info("User {} completed challenge {}!", userChatId, challengeId);
        }
        
        challengeUserRepository.save(user);
    }
    
    /**
     * Mark reward as sent
     */
    @Transactional
    public void markRewardSent(Long challengeId, Long userChatId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        
        ChallengeUser user = challengeUserRepository.findByChallengeAndUserChatId(challenge, userChatId)
                .orElseThrow(() -> new IllegalArgumentException("User not found in challenge"));
        
        user.setHasReceivedReward(true);
        challengeUserRepository.save(user);
        log.info("Marked reward as sent for user {} in challenge {}", userChatId, challengeId);
    }
    
    /**
     * Get all users in a challenge
     */
    public List<ChallengeUser> getUsersInChallenge(Long challengeId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        return challengeUserRepository.findByChallenge(challenge);
    }
    
    /**
     * Get completed users in a challenge
     */
    public List<ChallengeUser> getCompletedUsersInChallenge(Long challengeId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        return challengeUserRepository.findCompletedUsersByChallenge(challenge);
    }
    
    /**
     * Add referral
     * @return true if referral was successfully added, false if rejected
     */
    @Transactional
    public boolean addReferral(Long challengeId, Long referrerChatId, Long referredChatId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        
        // Check for circular referral - prevent A→B if B→A already exists
        if (challengeReferralRepository.existsByChallengeAndReferrerChatIdAndReferredChatId(
                challenge, referredChatId, referrerChatId)) {
            log.warn("Circular referral detected: {} already referred {} in challenge {}. Ignoring reverse referral.",
                    referredChatId, referrerChatId, challengeId);
            return false;
        }

        // Check if user already has a referrer (database constraint will also prevent this)
        if (challengeReferralRepository.existsByChallengeIdAndReferredChatId(
                challengeId, referredChatId)) {
            log.warn("User {} already has a referrer in challenge {}. Cannot be referred again by {}.",
                    referredChatId, challengeId, referrerChatId);
            return false;
        }
        
        // Check if this exact referral already exists (same referrer + referred)
        if (challengeReferralRepository.existsByChallengeAndReferrerChatIdAndReferredChatId(
                challenge, referrerChatId, referredChatId)) {
            log.warn("Referral already exists: {} referred {} in challenge {}", 
                    referrerChatId, referredChatId, challengeId);
            return false;
        }
        
        // Check if the referred user has already been referred by ANYONE in this challenge.
        // Each invited friend should only be counted once for the original inviter.
        if (challengeReferralRepository.existsByChallengeAndReferredChatId(challenge, referredChatId)) {
            log.warn("User {} was already referred by someone else in challenge {}. Ignoring referral from {}.", 
                    referredChatId, challengeId, referrerChatId);
            return false;
        }
        
        ChallengeReferral referral = ChallengeReferral.builder()
                .challenge(challenge)
                .referrerChatId(referrerChatId)
                .referredChatId(referredChatId)
                .isValid(false)
                .build();
        
        challengeReferralRepository.save(referral);
        log.info("Added referral: {} referred {} in challenge {}", referrerChatId, referredChatId, challengeId);
        return true;
    }
    
    /**
     * Mark referral as valid (when referred user verifies channels)
     */
    @Transactional
    public void validateReferral(Long challengeId, Long referrerChatId, Long referredChatId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        
        Optional<ChallengeReferral> referralOpt = challengeReferralRepository
                .findByChallengeAndReferrerChatIdAndReferredChatId(challenge, referrerChatId, referredChatId);
        
        if (referralOpt.isPresent() && !referralOpt.get().getIsValid()) {
            ChallengeReferral referral = referralOpt.get();
            referral.setIsValid(true);
            challengeReferralRepository.save(referral);
            
            // Increment referrer's count
            incrementReferralCount(challengeId, referrerChatId);
            log.info("Validated referral: {} referred {} in challenge {}", referrerChatId, referredChatId, challengeId);
        }
    }
    
    /**
     * Get valid referral count for user
     */
    public long getValidReferralCount(Long challengeId, Long userChatId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        return challengeReferralRepository.countValidReferralsByReferrerAndChallenge(challenge, userChatId);
    }
    
    /**
     * Get list of valid referred user chat IDs for a referrer
     */
    public List<Long> getValidReferredUserIds(Long challengeId, Long referrerChatId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        
        List<ChallengeReferral> validReferrals = challengeReferralRepository
                .findValidReferralsByReferrerAndChallenge(challenge, referrerChatId);
        
        return validReferrals.stream()
                .map(ChallengeReferral::getReferredChatId)
                .toList();
    }
    
    /**
     * Get indirect referral count for user (referrals made by user's direct referrals)
     */
    public long getIndirectReferralCount(Long challengeId, Long userChatId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found: " + challengeId));
        
        // Get all users directly referred by this user
        List<Long> directReferrals = getValidReferredUserIds(challengeId, userChatId);
        
        // Count total referrals made by all direct referrals
        long indirectCount = 0;
        for (Long directReferralChatId : directReferrals) {
            indirectCount += challengeReferralRepository
                    .countValidReferralsByReferrerAndChallenge(challenge, directReferralChatId);
        }
        
        log.debug("User {} has {} indirect referrals in challenge {} (from {} direct referrals)", 
                userChatId, indirectCount, challengeId, directReferrals.size());
        
        return indirectCount;
    }
    
    /**
     * Get all challenges user is participating in
     */
    public List<ChallengeUser> getUserChallenges(Long userChatId) {
        return challengeUserRepository.findByUserChatId(userChatId);
    }

    /**
     * Get total unique users who started the bot
     */
    public long getTotalUniqueUsers() {
        return challengeUserRepository.countTotalUniqueUsers();
    }

    /**
     * Get total unique users who verified channels
     */
    public long getVerifiedUniqueUsers() {
        return challengeUserRepository.countVerifiedUniqueUsers();
    }

    /**
     * Get total unique users who completed challenge reward
     */
    public long getCompletedUniqueUsers() {
        return challengeUserRepository.countCompletedUniqueUsers();
    }

    /**
     * Get all distinct user chat IDs (all users who ever pressed /start)
     */
    public List<Long> getAllDistinctUserChatIds() {
        return challengeUserRepository.findAllDistinctUserChatIds();
    }
}
