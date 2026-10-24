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
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberLeft;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Handler for channel verification logic.
 * Manages channel subscription checks and verification process.
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

    /**
     * Initialize the handler with bot reference.
     */
    public void init(Supplier<TelegramLongPollingBot> botSupplier) {
        this.botSupplier = botSupplier;
    }

    /**
     * Handle channel verification callback
     */
    public void handleChannelVerification(Long chatId, Integer messageId, String username,
            String firstName, String lastName,
            Runnable afterVerificationCallback) {
        // Determine which challenge to use
        Challenge challenge = determineChallenge(chatId);

        if (challenge == null) {
            botUtils.editMessage(botSupplier.get(), chatId, messageId, "❌ Hozircha faol challenge yo'q.");
            return;
        }

        // Get challenge channels
        List<ChallengeChannel> activeChannels = challengeService.getActiveChannelsForChallenge(challenge.getId());

        if (activeChannels.isEmpty()) {
            botUtils.editMessage(botSupplier.get(), chatId, messageId, "✅ Verification complete!");
            challengeUserService.markUserAsVerified(challenge.getId(), chatId);
            afterVerificationCallback.run();
            return;
        }

        // Check if user is member of all channels
        boolean allSubscribed = true;
        StringBuilder notSubscribedChannels = new StringBuilder();

        for (ChallengeChannel channel : activeChannels) {
            if (!isUserMemberOfChallengeChannel(chatId, channel)) {
                allSubscribed = false;
                notSubscribedChannels.append("• ").append(channel.getChannelName()).append("\n");
            }
        }

        if (allSubscribed) {
            // User is subscribed to all channels
            botUtils.editMessage(botSupplier.get(), chatId, messageId,
                    "✅ Barcha kanallarga obuna bo'lganingiz tasdiqlandi!");

            // Mark user as verified
            challengeUserService.markUserAsVerified(challenge.getId(), chatId);

            // Trigger after verification callback
            afterVerificationCallback.run();
        } else {
            // User is not subscribed to all channels
            String errorMessage = "❌ Iltimos, avval quyidagi kanallarga obuna bo'ling:\n\n"
                    + notSubscribedChannels.toString();
            botUtils.editMessage(botSupplier.get(), chatId, messageId, errorMessage);

            // Resend subscription buttons
            try {
                Thread.sleep(RETRY_DELAY_MS);
                SendMessage channelMessage = createChallengeChannelSubscriptionMessage(chatId, activeChannels);
                botSupplier.get().execute(channelMessage);
            } catch (Exception e) {
                log.error("Error resending channel subscription message", e);
            }
        }
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
        text.append("Keyin \"✅ A'zo bo'ldim \" tugmasini bosing!");
        message.setText(text.toString());


        // Create keyboard with channel buttons + verify button
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Add a button for each channel
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



        // Add verify button at the bottom
        List<InlineKeyboardButton> verifyRow = new ArrayList<>();
        InlineKeyboardButton verifyButton = new InlineKeyboardButton();
        verifyButton.setText("✅ A'zo bo'ldim");
        verifyButton.setCallbackData(CALLBACK_VERIFY_CHANNELS);
        verifyRow.add(verifyButton);
        rows.add(verifyRow);

        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        return message;
    }

    /**
     * Check if user is member of a challenge channel
     * Tries both numeric ID and @username format
     */
    public boolean isUserMemberOfChallengeChannel(Long userId, ChallengeChannel channel) {
        // Try with numeric channel ID first
        ChatMember chatMember = tryGetChatMember(userId, channel.getChannelId());

        // If failed and we have a username, try with @username format
        if (chatMember == null && channel.getChannelUsername() != null && !channel.getChannelUsername().isEmpty()) {
            log.info("Retrying with @username format for channel: {}", channel.getChannelName());
            String raw = channel.getChannelUsername().trim();
            String cleanUser;
            if (raw.startsWith("http://") || raw.startsWith("https://")) {
                cleanUser = raw.substring(raw.lastIndexOf('/') + 1).replace("@", "");
            } else {
                cleanUser = raw.replace("@", "");
            }
            String usernameFormat = "@" + cleanUser;
            chatMember = tryGetChatMember(userId, usernameFormat);
        }

        if (chatMember != null) {
            String status = chatMember.getStatus();
            // Check if user is a member (not "left" or "kicked")
            boolean isMember = !"left".equals(status) && !"kicked".equals(status);
            log.info("User {} membership in channel {} ({}): {}", userId, channel.getChannelName(), status, isMember);
            return isMember;
        }

        log.error("⚠️ CONFIGURATION ERROR: Bot cannot access channel {} (ID: {}, Username: @{}). " +
                "The bot must be added as an ADMINISTRATOR to the channel! " +
                "Please add the bot as admin in the channel settings.",
                channel.getChannelName(), channel.getChannelId(), channel.getChannelUsername());

        return false;
    }

    /**
     * Get list of channels the user is NOT a member of
     */
    public List<ChallengeChannel> getMissingChallengeChannels(Long userId, List<ChallengeChannel> channels) {
        List<ChallengeChannel> missing = new ArrayList<>();
        for (ChallengeChannel channel : channels) {
            if (!isUserMemberOfChallengeChannel(userId, channel)) {
                missing.add(channel);
            }
        }
        return missing;
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
            String errorMsg = e.getMessage();

            if (errorMsg != null && errorMsg.contains("chat not found")) {
                log.warn("Chat not found with identifier '{}' - bot may not be admin or ID is incorrect", chatId);
                return null;
            } else if (errorMsg != null
                    && (errorMsg.contains("USER_NOT_PARTICIPANT") || errorMsg.contains("user not found"))) {
                log.debug("User {} is not a member of {}", userId, chatId);
                ChatMemberLeft leftMember = new ChatMemberLeft();
                org.telegram.telegrambots.meta.api.objects.User user = new org.telegram.telegrambots.meta.api.objects.User();
                user.setId(userId);
                user.setFirstName("");
                user.setIsBot(false);
                leftMember.setUser(user);
                return leftMember;
            } else if (errorMsg != null && errorMsg.contains("Bad Request")) {
                log.warn("Bad request error for chatId '{}': {}", chatId, errorMsg);
                return null;
            } else {
                log.debug("Unexpected error checking membership for userId={}, chatId={}: {}", userId, chatId,
                        errorMsg);
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
