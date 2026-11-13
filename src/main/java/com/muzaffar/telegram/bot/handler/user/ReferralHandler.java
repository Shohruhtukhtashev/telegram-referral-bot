package com.muzaffar.telegram.bot.handler.user;

import static com.muzaffar.telegram.constants.BotConstants.*;

import com.muzaffar.telegram.entity.Challenge;
import com.muzaffar.telegram.entity.ChallengeChannel;
import com.muzaffar.telegram.entity.ChallengeUser;
import com.muzaffar.telegram.enums.MessageKey;
import com.muzaffar.telegram.service.ChallengeService;
import com.muzaffar.telegram.service.ChallengeUserService;
import com.muzaffar.telegram.service.InviteLinkService;
import com.muzaffar.telegram.service.MessageService;
import com.muzaffar.telegram.service.MessageTemplateService;
import com.muzaffar.telegram.service.UserSessionService;
import com.muzaffar.telegram.util.TelegramBotUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Handler for referral and reward related logic.
 * Manages "My Referrals" button, referral verification, and reward
 * distribution.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReferralHandler {

    private final ChallengeService challengeService;
    private final ChallengeUserService challengeUserService;
    private final MessageService messageService;
    private final MessageTemplateService messageTemplateService;
    private final InviteLinkService inviteLinkService;
    private final UserSessionService sessionService;
    private final TelegramBotUtils botUtils;
    private final ChannelVerificationHandler channelVerificationHandler;

    private Supplier<TelegramLongPollingBot> botSupplier;
    private Supplier<String> botUsernameSupplier;

    /**
     * Initialize the handler with bot reference.
     */
    public void init(Supplier<TelegramLongPollingBot> botSupplier, Supplier<String> botUsernameSupplier) {
        this.botSupplier = botSupplier;
        this.botUsernameSupplier = botUsernameSupplier;
    }

    /**
     * Handle "My Referrals" button press - show stats with share button
     */
    public void handleMyReferrals(Long chatId, String username, String firstName, String lastName) {
        // Get challenge from user context
        Optional<Long> challengeIdOpt = sessionService.getChallengeContext(chatId);

        Challenge challenge;
        if (challengeIdOpt.isPresent()) {
            challenge = challengeService.getChallengeById(challengeIdOpt.get()).orElse(null);
            if (challenge == null) {
                challenge = challengeService.getDefaultChallenge().orElse(null);
            }
        } else {
            challenge = challengeService.getDefaultChallenge().orElse(null);
        }

        if (challenge == null) {
            botUtils.sendMessage(botSupplier.get(), chatId, "❌ Hozircha faol challenge yo'q.");
            return;
        }

        if (challengeIdOpt.isEmpty() || !challengeIdOpt.get().equals(challenge.getId())) {
            sessionService.setChallengeContext(chatId, challenge.getId());
            log.debug("Set challenge context for user {} to challenge {}", chatId, challenge.getName());
        }

        // Get or create challenge user
        ChallengeUser challengeUser = challengeUserService.getUserInChallenge(challenge.getId(), chatId).orElse(null);

        if (challengeUser == null) {
            log.info("User {} not in challenge {}, starting registration process", chatId, challenge.getName());

            challengeUser = challengeUserService.addUserToChallenge(
                    challenge.getId(), chatId, username, firstName, lastName);

            if (!challengeUser.getHasVerifiedChannels()) {
                List<ChallengeChannel> activeChannels = challengeService
                        .getActiveChannelsForChallenge(challenge.getId());

                if (activeChannels.isEmpty()) {
                    challengeUserService.markUserAsVerified(challenge.getId(), chatId);
                } else {
                    SendMessage channelMessage = channelVerificationHandler
                            .createChallengeChannelSubscriptionMessage(chatId, activeChannels);
                    try {
                        botSupplier.get().execute(channelMessage);
                        return;
                    } catch (TelegramApiException e) {
                        log.error("Error sending channel subscription message", e);
                        botUtils.sendMessage(botSupplier.get(), chatId,
                                "❌ Xatolik yuz berdi. Iltimos, qaytadan urinib ko'ring.");
                        return;
                    }
                }
            }
        }

        try {
            long validReferrals = challengeUserService.getValidReferralCount(challenge.getId(), chatId);

            // Refresh challengeUser from database
            challengeUser = challengeUserService.getUserInChallenge(challenge.getId(), chatId).orElse(challengeUser);

            log.info("User {} stats: validReferrals={}, isCompleted={}, hasReceivedReward={}, required={}",
                    chatId, validReferrals, challengeUser.getIsCompleted(),
                    challengeUser.getHasReceivedReward(), challenge.getRequiredReferralCount());

            // Calculate indirect referrals (still needed for reward check)
            long indirectReferrals = challengeUserService.getIndirectReferralCount(challenge.getId(), chatId);

            // Send FORWARD_MESSAGE with referral link and share button (with optional
            // image)
            String forwardText = messageTemplateService.getText(challenge.getId(), MessageKey.FORWARD_MESSAGE);
            var forwardImagePath = messageTemplateService.getImagePath(challenge.getId(), MessageKey.FORWARD_MESSAGE);

            if (forwardText != null && !forwardText.trim().isEmpty()) {
                String referralLink = String.format("https://t.me/%s?start=%s",
                        botUsernameSupplier.get(), challengeUser.getReferralCode());

                // Create message with hyperlink
                String fullForwardText = forwardText + "\n\n[Sizga ham qatnashishni tavsiya qilaman 👇](" + referralLink
                        + ")";

                // Send with image if exists, otherwise just text
                if (forwardImagePath.isPresent()) {
                    botUtils.sendPhotoWithCaption(botSupplier.get(), chatId,
                            forwardImagePath.get(), fullForwardText);
                } else {
                    SendMessage forwardMessage = new SendMessage();
                    forwardMessage.setChatId(chatId.toString());
                    forwardMessage.setText(fullForwardText);
                    forwardMessage.setParseMode("Markdown");
                    botSupplier.get().execute(forwardMessage);
                }

                // Send follow-up explanation message
                botUtils.sendMessage(botSupplier.get(), chatId,
                        "👆 Yuqoridagi sizning referal link/havolangiz. Uni ko'proq tanishlaringizga ulashing va ko'proq ball yig'ing! Omad!");
            }

            Thread.sleep(500);

            // Check if user qualifies for private channel link
            if (challengeUser.getIsCompleted() || validReferrals >= challenge.getRequiredReferralCount()) {
                handleRewardCheck(challenge, chatId, challengeUser, validReferrals, indirectReferrals);
            }
        } catch (TelegramApiException e) {
            log.error("Error sending stats message", e);
        } catch (InterruptedException e) {
            log.error("Thread interrupted while sending stats", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Check and send reward if user qualifies (Centralized logic)
     */
    public void checkAndSendReward(Challenge challenge, Long chatId) {
        ChallengeUser challengeUser = challengeUserService.getUserInChallenge(challenge.getId(), chatId).orElse(null);
        if (challengeUser == null) {
            log.warn("User {} not found in challenge {} during reward check", chatId, challenge.getName());
            return;
        }

        if (!challengeUser.getHasReceivedReward()) {
            // Check if user is eligible for reward (completed or enough referrals)
            long validReferrals = challengeUserService.getValidReferralCount(challenge.getId(), chatId);

            if (challengeUser.getIsCompleted() || validReferrals >= challenge.getRequiredReferralCount()) {
                ReferralVerificationResult verification = verifyReferredUsersWithCounts(challenge, chatId);

                if (verification.activeReferrals >= challenge.getRequiredReferralCount()) {
                    String inviteLink = inviteLinkService.createOneTimeInviteLink(
                            botSupplier.get(), challenge.getId(), chatId);

                    if (inviteLink != null) {
                        SendMessage completionMessage = messageService.createChallengeCompletionMessage(
                                chatId,
                                challenge.getRequiredReferralCount(),
                                inviteLink);

                        try {
                            botSupplier.get().execute(completionMessage);
                            challengeUserService.markRewardSent(challenge.getId(), chatId);
                            log.info(
                                    "User {} received one-time private channel link for challenge {} with {} active referrals",
                                    chatId, challenge.getId(), verification.activeReferrals);
                        } catch (TelegramApiException e) {
                            log.error("Error sending reward message to user {}", chatId, e);
                        }
                    } else {
                        log.error("Failed to generate invite link for user {}", chatId);
                        botUtils.sendMessage(botSupplier.get(), chatId,
                                "❌ Xatolik yuz berdi. Iltimos, keyinroq qayta urinib ko'ring.");
                    }
                } else {
                    log.warn("User {} has {} total referrals but only {} active (required: {}, left: {})",
                            chatId, verification.totalReferrals, verification.activeReferrals,
                            challenge.getRequiredReferralCount(), verification.leftChannels);

                    try {
                        long indirectReferrals = challengeUserService.getIndirectReferralCount(challenge.getId(),
                                chatId);
                        SendMessage insufficientMessage = messageService.createInsufficientActiveReferralsMessage(
                                chatId,
                                verification.totalReferrals,
                                (int) indirectReferrals,
                                verification.activeReferrals,
                                verification.leftChannels,
                                challenge.getRequiredReferralCount());
                        botSupplier.get().execute(insufficientMessage);
                    } catch (TelegramApiException e) {
                        log.error("Error sending insufficient referrals message", e);
                    }
                }
            }
        } else {
            // User already received reward - show the full challenge completion message (if
            // called from button context)
            // Note: This part handles the "Takliflarim" button case mostly.
            // For automatic checks, logging "already received" is sufficient, but here we
            // reuse logic.
            // However, this calls getUnusedLink which is for display.

            Optional<String> unusedLink = inviteLinkService.getUnusedLink(challenge.getId(), chatId);

            if (unusedLink.isPresent()) {
                SendMessage completionMessage = messageService.createChallengeCompletionMessage(
                        chatId,
                        challenge.getRequiredReferralCount(),
                        unusedLink.get());
                try {
                    botSupplier.get().execute(completionMessage);
                } catch (TelegramApiException e) {
                    log.error("Error sending completion message", e);
                }
            } else {
                botUtils.sendMessage(botSupplier.get(), chatId, "✅ Siz allaqachon shaxsiy kanal havolasini olgansiz!");
            }
        }
    }

    /**
     * Check and send reward if user qualifies (Legacy wrapper or internal helper)
     */
    private void handleRewardCheck(Challenge challenge, Long chatId, ChallengeUser challengeUser,
            long validReferrals, long indirectReferrals) throws TelegramApiException, InterruptedException {
        checkAndSendReward(challenge, chatId);
    }

    /**
     * Verify referred users and return detailed counts
     */
    public ReferralVerificationResult verifyReferredUsersWithCounts(Challenge challenge, Long referrerChatId) {
        try {
            List<Long> referredUserIds = challengeUserService.getValidReferredUserIds(
                    challenge.getId(), referrerChatId);

            int totalReferrals = referredUserIds.size();

            if (referredUserIds.isEmpty()) {
                log.warn("No valid referred users found for referrer {}", referrerChatId);
                return new ReferralVerificationResult(false, 0, 0, 0);
            }

            List<ChallengeChannel> channels = challengeService.getActiveChannelsForChallenge(challenge.getId());

            if (channels.isEmpty()) {
                return new ReferralVerificationResult(true, totalReferrals, totalReferrals, 0);
            }

            int activeCount = 0;
            int leftCount = 0;

            for (Long referredUserId : referredUserIds) {
                boolean userStillInAllChannels = true;

                for (ChallengeChannel channel : channels) {
                    if (!channelVerificationHandler.isUserMemberOfChallengeChannel(referredUserId, channel)) {
                        log.warn("Referred user {} is no longer in channel {} (referrer: {})",
                                referredUserId, channel.getChannelName(), referrerChatId);
                        userStillInAllChannels = false;
                        break;
                    }
                }

                if (userStillInAllChannels) {
                    activeCount++;
                } else {
                    leftCount++;
                }
            }

            boolean allStillInChannels = (leftCount == 0);

            log.info("Referrer {} verification: total={}, active={}, left={}",
                    referrerChatId, totalReferrals, activeCount, leftCount);

            return new ReferralVerificationResult(allStillInChannels, totalReferrals, activeCount, leftCount);

        } catch (Exception e) {
            log.error("Error verifying referred users still in channels for referrer {}", referrerChatId, e);
            return new ReferralVerificationResult(true, 0, 0, 0);
        }
    }

    /**
     * Result of verifying referred users' channel subscriptions
     */
    public static class ReferralVerificationResult {
        public final boolean allStillInChannels;
        public final int totalReferrals;
        public final int activeReferrals;
        public final int leftChannels;

        public ReferralVerificationResult(boolean allStillInChannels, int totalReferrals,
                int activeReferrals, int leftChannels) {
            this.allStillInChannels = allStillInChannels;
            this.totalReferrals = totalReferrals;
            this.activeReferrals = activeReferrals;
            this.leftChannels = leftChannels;
        }
    }

    /**
     * Process pending referral after channel verification
     */
    public void processPendingReferral(Challenge challenge, Long chatId, String firstName) {
        Optional<String> referralCodeOpt = sessionService.getPendingReferral(chatId);
        String referralCode = referralCodeOpt.orElse(null);

        if (referralCode != null && !referralCode.isEmpty()) {
            log.info("Processing pending referral for user {} with code {}", chatId, referralCode);
            ChallengeUser referrer = challengeUserService.getUserByReferralCode(referralCode).orElse(null);

            if (referrer != null && referrer.getChallenge().getId().equals(challenge.getId())) {
                challengeUserService.addReferral(challenge.getId(), referrer.getUserChatId(), chatId);
                challengeUserService.validateReferral(challenge.getId(), referrer.getUserChatId(), chatId);
                sessionService.clearPendingReferral(chatId);

                notifyReferrerOfSuccess(challenge, referrer, chatId, firstName);
            } else {
                sessionService.clearPendingReferral(chatId);
            }
        }
    }

    /**
     * Notify referrer of successful referral
     */
    private void notifyReferrerOfSuccess(Challenge challenge, ChallengeUser referrer,
            Long referredChatId, String referredFirstName) {
        try {
            long validReferrals = challengeUserService.getValidReferralCount(challenge.getId(),
                    referrer.getUserChatId());
            SendMessage successMessage = messageService.createReferralSuccessMessage(
                    referrer.getUserChatId(),
                    (int) validReferrals,
                    challenge.getRequiredReferralCount(),
                    referredFirstName,
                    referredChatId);
            botSupplier.get().execute(successMessage);

            ChallengeUser updatedReferrer = challengeUserService.getUserInChallenge(
                    challenge.getId(), referrer.getUserChatId()).orElse(referrer);

            if ((updatedReferrer.getIsCompleted() || validReferrals >= challenge.getRequiredReferralCount())
                    && !updatedReferrer.getHasReceivedReward()) {

                // Use centralized logic to check and send reward
                checkAndSendReward(challenge, referrer.getUserChatId());
            }
        } catch (TelegramApiException e) {
            log.error("Error sending referral success message", e);
        }
    }
}
