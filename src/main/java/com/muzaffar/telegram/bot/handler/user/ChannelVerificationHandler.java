package com.muzaffar.telegram.bot.handler.user;

import static com.muzaffar.telegram.constants.BotConstants.*;

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
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberRestricted;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Handler for channel verification logic.
 * Manages parallel channel subscription checks and strict verification process.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelVerificationHandler {

    private final ChallengeService challengeService;
    private final ChallengeUserService challengeUserService;
    private final UserSessionService sessionService;
    private final MessageService messageService;
    private final TelegramBotUtils botUtils;

    private Supplier<TelegramLongPollingBot> botSupplier;

    // High performance pool for parallel Telegram API channel checks
    private final ExecutorService verificationExecutor = Executors.newFixedThreadPool(10);

    /**
     * Initialize the handler with bot reference.
     */
    public void init(Supplier<TelegramLongPollingBot> botSupplier) {
        this.botSupplier = botSupplier;
    }

    /**
     * Handle channel verification callback with callbackQueryId for instant feedback.
     */
    public void handleChannelVerification(Long chatId, Integer messageId, String callbackQueryId,
                                         String username, String firstName, String lastName,
                                         Runnable afterVerificationCallback) {
        // Determine which challenge to use
        Challenge challenge = determineChallenge(chatId);

        if (challenge == null) {
            if (callbackQueryId != null) {
                botUtils.answerCallbackQuery(botSupplier.get(), callbackQueryId, "❌ Hozircha faol challenge yo'q.", true);
            }
            botUtils.editMessage(botSupplier.get(), chatId, messageId, "❌ Hozircha faol challenge yo'q.");
            return;
        }

        // Get challenge channels
        List<ChallengeChannel> activeChannels = challengeService.getActiveChannelsForChallenge(challenge.getId());

        if (activeChannels.isEmpty()) {
            if (callbackQueryId != null) {
                botUtils.answerCallbackQuery(botSupplier.get(), callbackQueryId, "✅ Tasdiqlandi!", false);
            }
            botUtils.editMessage(botSupplier.get(), chatId, messageId, "✅ Barcha kanallarga obuna bo'lganingiz tasdiqlandi!");
            challengeUserService.markUserAsVerified(challenge.getId(), chatId);
            afterVerificationCallback.run();
            return;
        }

        // Parallel check across ALL channels
        List<ChallengeChannel> missingChannels = getMissingChallengeChannels(chatId, activeChannels);

        if (missingChannels.isEmpty()) {
            // User is strictly subscribed to EVERY single channel
            if (callbackQueryId != null) {
                botUtils.answerCallbackQuery(botSupplier.get(), callbackQueryId, "✅ Barcha kanallarga obuna bo'lganingiz tasdiqlandi!", false);
            }
            botUtils.editMessage(botSupplier.get(), chatId, messageId,
                    "✅ Barcha kanallarga obuna bo'lganingiz tasdiqlandi!");

            // Mark user as verified
            challengeUserService.markUserAsVerified(challenge.getId(), chatId);

            // Trigger after verification callback
            afterVerificationCallback.run();
        } else {
            // User has NOT subscribed to all channels - DO NOT ALLOW TO PROCEED!
            log.info("User {} failed verification. Missing {} channels out of {}",
                    chatId, missingChannels.size(), activeChannels.size());

            String channelWord = (activeChannels.size() > 1) ? "kanallarga" : "kanalga";
            if (callbackQueryId != null) {
                botUtils.answerCallbackQuery(botSupplier.get(), callbackQueryId,
                        "❌ Iltimos, avval barcha " + channelWord + " obuna bo'ling!", true);
            }
        }
    }

    /**
     * Backward-compatible overload
     */
    public void handleChannelVerification(Long chatId, Integer messageId, String username,
                                         String firstName, String lastName,
                                         Runnable afterVerificationCallback) {
        handleChannelVerification(chatId, messageId, null, username, firstName, lastName, afterVerificationCallback);
    }

    /**
     * Create inline keyboard with channel buttons and "A'zo bo'ldim" button
     */
    public InlineKeyboardMarkup createChallengeChannelSubscriptionKeyboard(List<ChallengeChannel> channels) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (ChallengeChannel channel : channels) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton channelButton = new InlineKeyboardButton();
            channelButton.setText(channel.getChannelName());
            String rawUsername = channel.getChannelUsername() != null ? channel.getChannelUsername().trim() : "";
            if (rawUsername.startsWith("http://") || rawUsername.startsWith("https://")) {
                channelButton.setUrl(rawUsername);
            } else {
                channelButton.setUrl("https://t.me/" + rawUsername.replace("@", ""));
            }
            row.add(channelButton);
            rows.add(row);
        }

        List<InlineKeyboardButton> verifyRow = new ArrayList<>();
        InlineKeyboardButton verifyButton = new InlineKeyboardButton();
        verifyButton.setText("✅ A'zo bo'ldim");
        verifyButton.setCallbackData(CALLBACK_VERIFY_CHANNELS);
        verifyRow.add(verifyButton);
        rows.add(verifyRow);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /**
     * Create channel subscription message for challenge channels
     */
    public SendMessage createChallengeChannelSubscriptionMessage(Long chatId, List<ChallengeChannel> channels) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setParseMode("Markdown");
        message.setDisableWebPagePreview(true);

        String channelWord = (channels != null && channels.size() > 1) ? "kanallarga" : "kanalga";
        StringBuilder text = new StringBuilder();
        text.append("Assalomu alaykum\n\n");
        text.append("Botdan to’liq foydalanish uchun quyidagi ").append(channelWord).append(" obuna bo’ling😊\n\n");
        text.append("Keyin \"✅ A'zo bo'ldim\" tugmasini bosing!");
        message.setText(text.toString());
        message.setReplyMarkup(createChallengeChannelSubscriptionKeyboard(channels));

        return message;
    }

    /**
     * Check if user is member of a challenge channel.
     * Only returns true if status is positively verified as creator, administrator, member, or valid restricted.
     */
    public boolean isUserMemberOfChallengeChannel(Long userId, ChallengeChannel channel) {
        if (channel == null) {
            return false;
        }

        // 1. Try with channelId (numeric ID) first
        if (channel.getChannelId() != null && !channel.getChannelId().trim().isEmpty()) {
            ChatMember chatMember = tryGetChatMember(userId, channel.getChannelId().trim());
            if (isChatMemberActive(chatMember)) {
                log.info("User {} confirmed member in channel {} via ID {}", userId, channel.getChannelName(), channel.getChannelId());
                return true;
            }
        }

        // 2. Fallback to @username format if ID check did not confirm membership
        if (channel.getChannelUsername() != null && !channel.getChannelUsername().trim().isEmpty()) {
            String raw = channel.getChannelUsername().trim();
            if (raw.startsWith("http://") || raw.startsWith("https://")) {
                raw = raw.substring(raw.lastIndexOf('/') + 1);
            }
            String cleanUser = raw.replace("@", "").trim();
            if (!cleanUser.isEmpty()) {
                ChatMember chatMember = tryGetChatMember(userId, "@" + cleanUser);
                if (isChatMemberActive(chatMember)) {
                    log.info("User {} confirmed member in channel {} via @{}", userId, channel.getChannelName(), cleanUser);
                    return true;
                }
            }
        }

        log.warn("User {} is NOT a member in channel {} (ID: {}, Username: @{})",
                userId, channel.getChannelName(), channel.getChannelId(), channel.getChannelUsername());
        return false;
    }

    /**
     * Positive whitelist check: ONLY true if user is currently active in the chat.
     */
    private boolean isChatMemberActive(ChatMember chatMember) {
        if (chatMember == null || chatMember.getStatus() == null) {
            return false;
        }
        String status = chatMember.getStatus().trim().toLowerCase();
        if ("creator".equals(status) || "administrator".equals(status) || "member".equals(status)) {
            return true;
        }
        if ("restricted".equals(status)) {
            if (chatMember instanceof ChatMemberRestricted restricted) {
                return Boolean.TRUE.equals(restricted.getIsMember());
            }
            return true;
        }
        return false;
    }

    /**
     * Parallel check: returns list of channels the user is NOT a member of.
     */
    public List<ChallengeChannel> getMissingChallengeChannels(Long userId, List<ChallengeChannel> channels) {
        if (channels == null || channels.isEmpty()) {
            return Collections.emptyList();
        }

        // Check all channels concurrently for ultra-fast response
        List<CompletableFuture<ChallengeChannel>> futures = channels.stream()
                .map(channel -> CompletableFuture.supplyAsync(() -> {
                    boolean isMember = isUserMemberOfChallengeChannel(userId, channel);
                    return isMember ? null : channel;
                }, verificationExecutor))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Notify user about missing channel subscriptions
     */
    public void notifyUserMissingChannels(Long chatId, List<ChallengeChannel> missingChannels) {
        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ *Siz quyidagi kanallardan chiqib ketgansiz:*\n\n");
        for (ChallengeChannel channel : missingChannels) {
            sb.append("• ").append(channel.getChannelName()).append("\n");
        }
        sb.append("\n_Iltimos, yana obuna bo'ling va \"✅ A'zo bo'ldim\" tugmasini bosing._");
        botUtils.sendMessage(botSupplier.get(), chatId, sb.toString());
    }

    /**
     * Try to get chat member with specific chat identifier
     */
    private ChatMember tryGetChatMember(Long userId, String chatId) {
        try {
            GetChatMember getChatMember = new GetChatMember();
            getChatMember.setChatId(chatId);
            getChatMember.setUserId(userId);

            ChatMember chatMember = botSupplier.get().execute(getChatMember);
            log.debug("Successfully checked membership for userId={} in chatId={}", userId, chatId);
            return chatMember;
        } catch (TelegramApiException e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "";
            if (errorMsg.contains("USER_NOT_PARTICIPANT") || errorMsg.contains("user not found")) {
                log.debug("User {} is confirmed NOT a participant of {}", userId, chatId);
                return null;
            } else if (errorMsg.contains("chat not found") || errorMsg.contains("bot is not a member")) {
                log.warn("Chat '{}' not accessible by bot: {}", chatId, errorMsg);
                return null;
            } else {
                log.debug("Telegram API error checking membership for userId={}, chatId={}: {}", userId, chatId, errorMsg);
                return null;
            }
        }
    }

    /**
     * Determine which challenge to use based on context
     */
    private Challenge determineChallenge(Long chatId) {
        // First check if user has a challenge context (from broadcast or Takliflarim)
        Optional<Long> challengeIdOpt = sessionService.getChallengeContext(chatId);
        if (challengeIdOpt.isPresent()) {
            Challenge challenge = challengeService.getChallengeById(challengeIdOpt.get()).orElse(null);
            if (challenge != null) {
                log.info("User {} verifying channels for challenge {} from context", chatId, challenge.getName());
                return challenge;
            }
        }

        // Check for referral code
        Optional<String> referralCodeOpt = sessionService.getPendingReferral(chatId);
        if (referralCodeOpt.isPresent()) {
            String referralCode = referralCodeOpt.get();
            ChallengeUser referrer = challengeUserService.getUserByReferralCode(referralCode).orElse(null);
            if (referrer != null) {
                Long refChallengeId = referrer.getChallenge().getId();
                Challenge challenge = challengeService.getChallengeById(refChallengeId).orElse(null);
                if (challenge != null) {
                    log.info("User {} verifying channels for challenge {} via referral code {}", chatId,
                            challenge.getName(), referralCode);
                    return challenge;
                }
            }
        }

        // Final fallback to default challenge
        return challengeService.getDefaultChallenge().orElse(null);
    }
}
