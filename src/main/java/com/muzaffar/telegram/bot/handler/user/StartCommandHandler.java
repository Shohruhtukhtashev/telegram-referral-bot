package com.muzaffar.telegram.bot.handler.user;

import static com.muzaffar.telegram.constants.BotConstants.*;

import com.muzaffar.telegram.bot.handler.BotHandler;
import com.muzaffar.telegram.entity.Challenge;
import com.muzaffar.telegram.entity.ChallengeChannel;
import com.muzaffar.telegram.entity.ChallengeUser;
import com.muzaffar.telegram.service.ChallengeService;
import com.muzaffar.telegram.service.ChallengeUserService;
import com.muzaffar.telegram.service.MessageService;
import com.muzaffar.telegram.service.UserSessionService;
import com.muzaffar.telegram.util.TelegramBotUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Handler for /start command and START button.
 * Manages user registration and challenge joining flow.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartCommandHandler implements BotHandler {

    private final ChallengeService challengeService;
    private final ChallengeUserService challengeUserService;
    private final MessageService messageService;
    private final UserSessionService sessionService;
    private final TelegramBotUtils botUtils;
    private final ChannelVerificationHandler channelVerificationHandler;
    private final ReferralHandler referralHandler;

    private Supplier<TelegramLongPollingBot> botSupplier;
    private Supplier<String> botUsernameSupplier;

    /**
     * Initialize the handler with bot reference.
     */
    public void init(Supplier<TelegramLongPollingBot> botSupplier, Supplier<String> botUsernameSupplier) {
        this.botSupplier = botSupplier;
        this.botUsernameSupplier = botUsernameSupplier;
    }

    @Override
    public boolean canHandle(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return false;
        }
        return update.getMessage().getText().startsWith(START_COMMAND);
    }

    @Override
    public void handle(Update update) {
        handleStartCommand(update.getMessage());
    }

    /**
     * Handle /start command (challenge-aware)
     */
    public void handleStartCommand(Message message) {
        Long chatId = message.getChatId();
        String username = message.getFrom().getUserName();
        String firstName = message.getFrom().getFirstName();
        String lastName = message.getFrom().getLastName();
        String text = message.getText();

        // Extract referral code if present (/start <referralCode>)
        String referralCode = null;
        if (text.length() > START_COMMAND_PREFIX_LENGTH) {
            referralCode = text.substring(START_COMMAND_PREFIX_LENGTH).trim();
        }

        // Determine which challenge to use
        Challenge challenge = determineChallenge(chatId, referralCode);

        if (challenge == null) {
            botUtils.sendMessage(botSupplier.get(), chatId,
                    "❌ Hozircha faol challenge yo'q. Iltimos, keyinroq urinib ko'ring.");
            log.warn("No active challenge found for user {}", chatId);
            return;
        }

        final Challenge finalChallenge = challenge;
        final String finalReferralCode = referralCode;

        // Get or create challenge user
        ChallengeUser challengeUser = challengeUserService.getUserInChallenge(finalChallenge.getId(), chatId)
                .orElseGet(() -> challengeUserService.addUserToChallenge(
                        finalChallenge.getId(), chatId, username, firstName, lastName));

        // Fetch active channels once for membership checks
        List<ChallengeChannel> activeChannels = challengeService.getActiveChannelsForChallenge(finalChallenge.getId());

        // If user previously verified, ensure they are still subscribed to all channels
        if (challengeUser.getHasVerifiedChannels() && !activeChannels.isEmpty()) {
            List<ChallengeChannel> missingChannels = channelVerificationHandler.getMissingChallengeChannels(chatId,
                    activeChannels);
            if (!missingChannels.isEmpty()) {
                log.info("User {} left some required channels for challenge {}", chatId, finalChallenge.getName());
                challengeUserService.markUserAsUnverified(finalChallenge.getId(), chatId);

                if (finalReferralCode != null && !finalReferralCode.isEmpty()) {
                    sessionService.setPendingReferral(chatId, finalReferralCode);
                }

                channelVerificationHandler.notifyUserMissingChannels(chatId, missingChannels);

                try {
                    SendMessage channelMessage = channelVerificationHandler
                            .createChallengeChannelSubscriptionMessage(chatId, activeChannels);
                    botSupplier.get().execute(channelMessage);
                } catch (TelegramApiException e) {
                    log.error("Error resending channel subscription message after detecting missing channels", e);
                }
                return;
            }
        }

        // Check if user has verified channels
        if (!challengeUser.getHasVerifiedChannels()) {
            // Store referral code for later processing (after channel verification)
            if (finalReferralCode != null && !finalReferralCode.isEmpty()) {
                sessionService.setPendingReferral(chatId, finalReferralCode);
                log.info("Stored referral code for user {} to process after channel verification", chatId);
            }

            // Show channel subscription request (from challenge's channels)
            if (!activeChannels.isEmpty()) {
                try {
                    sendWelcomeGuideMessage(chatId, firstName, finalChallenge);
                    SendMessage channelMessage = channelVerificationHandler
                            .createChallengeChannelSubscriptionMessage(chatId, activeChannels);
                    botSupplier.get().execute(channelMessage);
                    return;
                } catch (TelegramApiException e) {
                    log.error("Error sending channel subscription message", e);
                }
            } else {
                // No channels configured, mark as verified
                challengeUserService.markUserAsVerified(finalChallenge.getId(), chatId);
                challengeUser = challengeUserService.getUserInChallenge(finalChallenge.getId(), chatId).get();
            }
        }

        // Check if this is a new user with a referral code and process it
        if (finalReferralCode != null && !finalReferralCode.isEmpty()) {
            processNewReferral(finalChallenge, chatId, firstName, finalReferralCode, challengeUser);
        }

        // Send information messages in sequence (same for referral and direct users)
        sendInformationSequence(finalChallenge, chatId, firstName);
    }

    /**
     * Handle START button press - show channel subscription
     */
    public void handleStartButton(Long chatId, Integer messageId, String username, String firstName, String lastName) {
        Challenge challenge = determineChallenge(chatId, null);

        if (challenge == null) {
            botUtils.sendMessage(botSupplier.get(), chatId, "❌ Hozircha faol challenge yo'q.");
            return;
        }

        final Challenge finalChallenge = challenge;

        // Get or create challenge user
        ChallengeUser challengeUser = challengeUserService.getUserInChallenge(finalChallenge.getId(), chatId)
                .orElseGet(() -> challengeUserService.addUserToChallenge(
                        finalChallenge.getId(), chatId, username, firstName, lastName));

        // Check if user has already verified channels
        if (challengeUser.getHasVerifiedChannels()) {
            sendInformationSequence(finalChallenge, chatId, firstName);
            return;
        }

        // Delete the intro message
        botUtils.deleteMessage(botSupplier.get(), chatId, messageId);

        // Show channel subscription request from challenge
        List<ChallengeChannel> activeChannels = challengeService.getActiveChannelsForChallenge(finalChallenge.getId());
        if (!activeChannels.isEmpty()) {
            try {
                sendWelcomeGuideMessage(chatId, firstName, finalChallenge);
                SendMessage channelMessage = channelVerificationHandler
                        .createChallengeChannelSubscriptionMessage(chatId, activeChannels);
                botSupplier.get().execute(channelMessage);
            } catch (TelegramApiException e) {
                log.error("Error sending channel subscription message", e);
            }
        } else {
            // No channels configured, mark as verified and show information
            challengeUserService.markUserAsVerified(finalChallenge.getId(), chatId);
            sendInformationSequence(finalChallenge, chatId, firstName);
        }
    }

    /**
     * Send information message sequence: greeting -> main message (with Takliflarim
     * button)
     */
    public void sendInformationSequence(Challenge challenge, Long chatId, String firstName) {
        try {
            // Store the challenge context for this user
            sessionService.setChallengeContext(chatId, challenge.getId());

            // 1. Send greeting message first
            SendMessage greetingMessage = messageService.createGreetingMessage(chatId, firstName);
            botSupplier.get().execute(greetingMessage);

            // Small delay for better UX
            Thread.sleep(INFORMATION_SEQUENCE_DELAY_MS);

            // 2. Send main information message with Takliflarim button (with image if
            // available)
            messageService.sendInformationMessageWithImage(botSupplier.get(), challenge.getId(), chatId);

        } catch (TelegramApiException | InterruptedException e) {
            log.error("Error sending information sequence", e);
        }
    }

    /**
     * Send information message after channel verification with pending referral
     * processing
     */
    public void sendInformationMessageAfterVerification(Long chatId, String username, String firstName,
            String lastName) {
        String referralCode = sessionService.getPendingReferral(chatId).orElse(null);

        Challenge challenge = determineChallenge(chatId, referralCode);

        if (challenge == null) {
            botUtils.sendMessage(botSupplier.get(), chatId, "❌ Hozircha faol challenge yo'q.");
            return;
        }

        // Process pending referral if exists
        if (referralCode != null && !referralCode.isEmpty()) {
            referralHandler.processPendingReferral(challenge, chatId, firstName);
        }

        // Send the same information sequence for everyone (referral or direct)
        sendInformationSequence(challenge, chatId, firstName);
    }

    /**
     * Process new referral for a user
     */
    private void processNewReferral(Challenge challenge, Long chatId, String firstName,
            String referralCode, ChallengeUser challengeUser) {
        ChallengeUser referrer = challengeUserService.getUserByReferralCode(referralCode).orElse(null);

        // Check if referrer exists, is in the same challenge, and is not the same user
        // (prevent self-referral)
        if (referrer != null && referrer.getChallenge().getId().equals(challenge.getId())
                && !referrer.getUserChatId().equals(chatId)) {
            boolean referralAdded = challengeUserService.addReferral(challenge.getId(), referrer.getUserChatId(),
                    chatId);

            if (referralAdded && challengeUser.getHasVerifiedChannels()) {
                challengeUserService.validateReferral(challenge.getId(), referrer.getUserChatId(), chatId);

                try {
                    long validReferrals = challengeUserService.getValidReferralCount(challenge.getId(),
                            referrer.getUserChatId());
                    SendMessage successMessage = messageService.createReferralSuccessMessage(
                            referrer.getUserChatId(),
                            (int) validReferrals,
                            challenge.getRequiredReferralCount(),
                            firstName,
                            chatId);
                    botSupplier.get().execute(successMessage);

                    // Check if referrer now qualifies for private channel link
                    checkAndSendReferrerReward(challenge, referrer, validReferrals);

                } catch (TelegramApiException e) {
                    log.error("Error sending referral success message", e);
                } catch (InterruptedException e) {
                    log.error("Thread interrupted", e);
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Check if referrer qualifies for reward and send it
     */
    private void checkAndSendReferrerReward(Challenge challenge, ChallengeUser referrer, long validReferrals)
            throws TelegramApiException, InterruptedException {
        ChallengeUser updatedReferrer = challengeUserService.getUserInChallenge(
                challenge.getId(), referrer.getUserChatId()).orElse(referrer);

        if ((updatedReferrer.getIsCompleted() || validReferrals >= challenge.getRequiredReferralCount())
                && !updatedReferrer.getHasReceivedReward()) {

            log.info("Referrer {} now has {} valid referrals (required: {}), checking channel subscriptions",
                    referrer.getUserChatId(), validReferrals, challenge.getRequiredReferralCount());

            ReferralHandler.ReferralVerificationResult verification = referralHandler
                    .verifyReferredUsersWithCounts(challenge, referrer.getUserChatId());

            if (verification.activeReferrals >= challenge.getRequiredReferralCount()) {
                // Delegate to ReferralHandler for centralized reward sending
                referralHandler.checkAndSendReward(challenge, referrer.getUserChatId());
            } else {
                log.warn("Referrer {} has only {} active referrals (required: {}, left channel: {})",
                        referrer.getUserChatId(), verification.activeReferrals,
                        challenge.getRequiredReferralCount(), verification.leftChannels);
            }
        }
    }

    /**
     * Determine which challenge to use based on context
     * 
     * Priority:
     * 1. Referral code challenge (if provided)
     * 2. Main challenge (if current challenge is no longer main and new main
     * exists)
     * 3. Current challenge context (if still valid)
     * 4. Default main challenge
     */
    private Challenge determineChallenge(Long chatId, String referralCode) {
        Challenge mainChallenge = challengeService.getDefaultChallenge().orElse(null);

        // 1. PRIORITY: Agar referral code bo'lsa - referrer's challenge ishlatish
        if (referralCode != null && !referralCode.isEmpty()) {
            ChallengeUser referrer = challengeUserService.getUserByReferralCode(referralCode).orElse(null);
            if (referrer != null) {
                Challenge referrerChallenge = referrer.getChallenge();
                log.info("User {} joining challenge {} via referral code {}",
                        chatId, referrerChallenge.getName(), referralCode);
                return referrerChallenge;
            }
        }

        // 2. User'ning current challenge context'ini tekshirish
        Optional<Long> currentChallengeIdOpt = sessionService.getChallengeContext(chatId);

        if (currentChallengeIdOpt.isPresent()) {
            Challenge currentChallenge = challengeService.getChallengeById(currentChallengeIdOpt.get()).orElse(null);

            // 3. Agar current challenge mavjud va main challenge ham mavjud bo'lsa
            if (currentChallenge != null && mainChallenge != null) {
                // Agar main challenge o'zgargan bo'lsa (current challenge endi main emas)
                if (!currentChallenge.getIsMain() && mainChallenge.getIsMain()
                        && !currentChallenge.getId().equals(mainChallenge.getId())) {
                    log.info("User {} switching from challenge '{}' to new main challenge '{}'",
                            chatId, currentChallenge.getName(), mainChallenge.getName());
                    return mainChallenge; // Yangi main challenge ga o'tkazish
                }
                // Current challenge hali ham valid (yoki hali ham main)
                log.info("User {} continuing with challenge '{}'", chatId, currentChallenge.getName());
                return currentChallenge;
            } else if (currentChallenge != null) {
                // Main challenge yo'q lekin current challenge mavjud
                return currentChallenge;
            }
        }

        // 4. DEFAULT: main challenge
        if (mainChallenge != null) {
            log.info("User {} joining default main challenge '{}'", chatId, mainChallenge.getName());
        }
        return mainChallenge;
    }

    /**
     * Send welcome message with video guide link:
     * 🌟 Botga xush kelibsiz, {name}!
     * [Botdan foydalanish uchun video qo'llanma]({link})
     */
    private void sendWelcomeGuideMessage(Long chatId, String firstName, Challenge challenge) {
        String name = (firstName != null && !firstName.trim().isEmpty()) ? firstName.trim() : "qadrli foydalanuvchi";
        name = name.replace("*", "").replace("_", "").replace("[", "").replace("`", "");

        String guideUrl = challengeService.getVideoGuideUrl(challenge != null ? challenge.getId() : null)
                .orElse("https://t.me/Bot_uchun_Videoo");

        String text = String.format("🌟 Botga xush kelibsiz, %s!\n\n[Botdan foydalanish uchun video qo'llanma](%s)",
                name, guideUrl);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("Markdown");
        message.setDisableWebPagePreview(true);

        try {
            botSupplier.get().execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending welcome guide message to chatId={}", chatId, e);
        }
    }
}

