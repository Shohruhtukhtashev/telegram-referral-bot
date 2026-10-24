package com.muzaffar.telegram.bot;

import static com.muzaffar.telegram.constants.BotConstants.*;

import com.muzaffar.telegram.bot.handler.admin.*;
import com.muzaffar.telegram.config.BotConfig;
import com.muzaffar.telegram.util.TelegramBotUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Refactored AdminTelegramBot that delegates to handlers.
 * This version is significantly smaller and more maintainable.
 */
@Slf4j
@Component
public class AdminTelegramBot extends TelegramLongPollingBot {

    private final BotConfig botConfig;
    private final TelegramBotUtils botUtils;

    // Handlers
    private final AdminStateManager stateManager;
    private final AdminNavigationHandler navigationHandler;
    private final ChallengeManagementHandler challengeManagementHandler;
    private final ChannelManagementHandler channelManagementHandler;
    private final SettingsHandler settingsHandler;
    private final BroadcastHandler broadcastHandler;
    private final ImageUploadHandler imageUploadHandler;

    // Reference to referral bot for broadcasting - using ObjectProvider to avoid
    // circular dependency
    private final ObjectProvider<ReferralTelegramBot> referralBotProvider;

    public AdminTelegramBot(BotConfig botConfig,
            TelegramBotUtils botUtils,
            AdminStateManager stateManager,
            AdminNavigationHandler navigationHandler,
            ChallengeManagementHandler challengeManagementHandler,
            ChannelManagementHandler channelManagementHandler,
            SettingsHandler settingsHandler,
            BroadcastHandler broadcastHandler,
            ImageUploadHandler imageUploadHandler,
            ObjectProvider<ReferralTelegramBot> referralBotProvider) {
        super(botConfig.getAdminBot().getToken());
        this.botConfig = botConfig;
        this.botUtils = botUtils;
        this.stateManager = stateManager;
        this.navigationHandler = navigationHandler;
        this.challengeManagementHandler = challengeManagementHandler;
        this.channelManagementHandler = channelManagementHandler;
        this.settingsHandler = settingsHandler;
        this.broadcastHandler = broadcastHandler;
        this.imageUploadHandler = imageUploadHandler;
        this.referralBotProvider = referralBotProvider;
    }

    @PostConstruct
    public void initHandlers() {
        // Initialize handlers with bot reference
        navigationHandler.init(() -> this);
        challengeManagementHandler.init(() -> this);
        channelManagementHandler.init(() -> this);
        settingsHandler.init(() -> this);
        broadcastHandler.init(() -> this, () -> referralBotProvider.getObject());
        imageUploadHandler.init(() -> this);
    }

    @Override
    public String getBotUsername() {
        return botConfig.getAdminBot().getUsername();
    }

    @Override
    public void clearWebhook() throws org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException {
        try {
            super.clearWebhook();
        } catch (Exception e) {
            log.warn("⚠️ Could not clear webhook for Admin bot, proceeding anyway: {}", e.getMessage());
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage()) {
                Message message = update.getMessage();
                Long chatId = message.getChatId();

                if (!isAdmin(chatId)) {
                    sendMessage(chatId, "❌ Sizda ushbu botdan foydalanish huquqi yo'q.");
                    return;
                }

                if (message.hasText()) {
                    handleTextMessage(chatId, message);
                } else if (message.hasPhoto()) {
                    if (STATE_AWAITING_CUSTOM_BROADCAST_MESSAGE.equals(stateManager.getState(chatId))) {
                        broadcastHandler.handleCustomBroadcastPhotoInput(chatId, message);
                    } else {
                        imageUploadHandler.handlePhotoUpload(chatId, message);
                    }
                } else if (message.hasDocument()) {
                    imageUploadHandler.handleDocumentUpload(chatId, message);
                }
            } else if (update.hasCallbackQuery()) {
                CallbackQuery callbackQuery = update.getCallbackQuery();
                Long chatId = callbackQuery.getMessage().getChatId();

                if (!isAdmin(chatId)) {
                    return;
                }

                handleCallbackQuery(callbackQuery);
            }
        } catch (Exception e) {
            log.error("Error processing update", e);
            if (update.hasMessage()) {
                sendMessage(update.getMessage().getChatId(), "❌ Xatolik yuz berdi. Iltimos qaytadan urinib ko'ring.");
            }
        }
    }

    /**
     * Handle text messages
     */
    private void handleTextMessage(Long chatId, Message message) {
        String text = message.getText();
        log.info("Admin message from chatId={}: {}", chatId, text);

        if (text.equals(START_COMMAND) || text.equals(MENU_COMMAND)) {
            navigationHandler.showChallengeManagementMenu(chatId);
        } else if (text.equals(STATS_COMMAND) || text.equals(ADMIN_STATS_COMMAND)) {
            navigationHandler.showFullAdminStats(chatId, null);
        } else if (text.startsWith(ADMIN_BROADCAST_COMMAND)) {
            String broadcastContent = text.length() > ADMIN_BROADCAST_PREFIX_LENGTH
                    ? text.substring(ADMIN_BROADCAST_PREFIX_LENGTH).trim() : "";
            if (!broadcastContent.isEmpty()) {
                broadcastHandler.handleCustomBroadcastTextInput(chatId, broadcastContent, message.getMessageId());
            } else {
                broadcastHandler.startBroadcastCustomAll(chatId, null);
            }
        } else {
            handleTextInput(chatId, text, message.getMessageId());
        }
    }

    /**
     * Handle text input based on current state
     */
    private void handleTextInput(Long chatId, String text, Integer messageId) {
        String state = stateManager.getState(chatId);

        if (state == null) {
            log.debug("Ignoring text input from chatId={} (no active state): {}", chatId, text);
            deleteMessage(chatId, messageId);
            return;
        }

        switch (state) {
            case STATE_AWAITING_CHANNEL_ID:
                channelManagementHandler.handleChannelIdInput(chatId, text, messageId);
                break;
            case STATE_AWAITING_CHANNEL_NAME:
                channelManagementHandler.handleChannelNameInput(chatId, text, messageId);
                break;
            case STATE_AWAITING_CHANNEL_USERNAME:
                channelManagementHandler.handleChannelUsernameInput(chatId, text, messageId);
                break;
            case STATE_AWAITING_CHALLENGE_NAME:
                challengeManagementHandler.handleChallengeNameInput(chatId, text, messageId);
                break;
            case STATE_AWAITING_CHALLENGE_REFERRAL_COUNT:
                challengeManagementHandler.handleChallengeReferralCountInput(chatId, text, messageId);
                break;
            case STATE_AWAITING_CHALLENGE_PRIVATE_LINK:
                challengeManagementHandler.handleChallengePrivateLinkInput(chatId, text, messageId);
                break;
            case STATE_AWAITING_REFERRAL_COUNT:
                settingsHandler.handleReferralCountInput(chatId, text, messageId);
                break;
            case STATE_AWAITING_PRIVATE_LINK:
                settingsHandler.handlePrivateLinkInput(chatId, text, messageId);
                break;
            case STATE_AWAITING_INFORMATION_MESSAGE:
                settingsHandler.handleInformationMessageInput(chatId, text, messageId);
                break;
            case STATE_AWAITING_FORWARD_MESSAGE:
                settingsHandler.handleForwardMessageInput(chatId, text, messageId);
                break;
            case STATE_AWAITING_BROADCAST_MESSAGE:
                broadcastHandler.handleBroadcastUserIdInput(chatId, text, messageId);
                break;
            case STATE_AWAITING_VIDEO_GUIDE_URL:
                settingsHandler.handleVideoGuideUrlInput(chatId, text, messageId);
                break;
            case STATE_AWAITING_CUSTOM_BROADCAST_MESSAGE:
                broadcastHandler.handleCustomBroadcastTextInput(chatId, text, messageId);
                break;
            default:
                log.warn("Unknown state for chatId={}: {}", chatId, state);
                stateManager.clearAll(chatId);
                break;
        }
    }

    /**
     * Handle callback queries
     */
    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String data = callbackQuery.getData();

        log.info("Admin callback from chatId={}: {}", chatId, data);

        switch (data) {
            // Main navigation
            case CALLBACK_MENU_MAIN:
            case CALLBACK_MAIN_MENU:
            case CALLBACK_CHALLENGE_MANAGEMENT:
                navigationHandler.showChallengeManagementMenu(chatId, messageId);
                break;
            case CALLBACK_ADMIN_STATS:
                navigationHandler.pushNavigation(chatId, CALLBACK_MAIN_MENU);
                navigationHandler.showFullAdminStats(chatId, messageId);
                break;
            case CALLBACK_BACK:
                handleBackButton(chatId, messageId);
                break;
            case CALLBACK_CANCEL_OPERATION:
                handleCancelOperation(chatId, messageId);
                break;

            // Challenge management
            case CALLBACK_CHALLENGE_LIST:
                navigationHandler.pushNavigation(chatId, CALLBACK_CHALLENGE_MANAGEMENT);
                challengeManagementHandler.showChallengeList(chatId, messageId);
                break;
            case CALLBACK_CHALLENGE_CREATE:
                navigationHandler.pushNavigation(chatId, CALLBACK_CHALLENGE_MANAGEMENT);
                challengeManagementHandler.startCreateChallenge(chatId, messageId);
                break;
            case CALLBACK_CHALLENGE_SET_MAIN:
                challengeManagementHandler.handleSetMainChallenge(chatId, messageId);
                break;

            // Channel management
            case CALLBACK_MENU_CHANNELS:
                navigationHandler.pushNavigation(chatId, CALLBACK_CHALLENGE_LIST);
                navigationHandler.showChannelMenu(chatId, messageId);
                break;
            case CALLBACK_CHANNEL_ADD:
                navigationHandler.pushNavigation(chatId, CALLBACK_MENU_CHANNELS);
                channelManagementHandler.startAddChannel(chatId, messageId);
                break;
            case CALLBACK_CHANNEL_LIST:
                navigationHandler.pushNavigation(chatId, CALLBACK_MENU_CHANNELS);
                channelManagementHandler.showChannelList(chatId, messageId);
                break;
            case CALLBACK_CHANNEL_REMOVE:
                navigationHandler.pushNavigation(chatId, CALLBACK_MENU_CHANNELS);
                channelManagementHandler.startRemoveChannel(chatId, messageId);
                break;

            // Settings
            case CALLBACK_MENU_SETTINGS:
                navigationHandler.pushNavigation(chatId, CALLBACK_CHALLENGE_LIST);
                navigationHandler.showSettingsMenu(chatId, messageId);
                break;
            case CALLBACK_SETTINGS_SET_REFERRALS:
                navigationHandler.pushNavigation(chatId, CALLBACK_MENU_SETTINGS);
                settingsHandler.startSetReferrals(chatId, messageId);
                break;
            case CALLBACK_SETTINGS_SET_PRIVATE_LINK:
                navigationHandler.pushNavigation(chatId, CALLBACK_MENU_SETTINGS);
                settingsHandler.startSetPrivateLink(chatId, messageId);
                break;
            case CALLBACK_SETTINGS_SET_VIDEO_GUIDE:
                navigationHandler.pushNavigation(chatId, CALLBACK_MENU_SETTINGS);
                settingsHandler.startSetVideoGuide(chatId, messageId);
                break;
            case CALLBACK_SETTINGS_DELETE_VIDEO_GUIDE:
                settingsHandler.confirmDeleteVideoGuide(chatId, messageId);
                break;
            case CALLBACK_SETTINGS_INFORMATION:
                navigationHandler.pushNavigation(chatId, CALLBACK_MENU_SETTINGS);
                settingsHandler.startSetInformationMessage(chatId, messageId);
                break;
            case CALLBACK_EDIT_INFORMATION_TEXT:
                settingsHandler.startEditInformationText(chatId, messageId);
                break;
            case CALLBACK_EDIT_INFORMATION_IMAGE:
                settingsHandler.startEditInformationImage(chatId, messageId);
                break;
            case CALLBACK_DELETE_INFORMATION_IMAGE:
                settingsHandler.confirmDeleteInformationImage(chatId, messageId);
                break;
            case CALLBACK_SETTINGS_SET_FORWARD_MESSAGE:
                navigationHandler.pushNavigation(chatId, CALLBACK_MENU_SETTINGS);
                settingsHandler.startSetForwardMessage(chatId, messageId);
                break;
            case CALLBACK_EDIT_FORWARD_TEXT:
                settingsHandler.startEditForwardText(chatId, messageId);
                break;
            case CALLBACK_EDIT_FORWARD_IMAGE:
                settingsHandler.startEditForwardImage(chatId, messageId);
                break;
            case CALLBACK_DELETE_FORWARD_IMAGE:
                settingsHandler.confirmDeleteForwardImage(chatId, messageId);
                break;

            // Broadcast
            case CALLBACK_MENU_BROADCAST:
                navigationHandler.pushNavigation(chatId, CALLBACK_CHALLENGE_LIST);
                navigationHandler.showBroadcastMenu(chatId, messageId);
                break;
            case CALLBACK_BROADCAST_CUSTOM_ALL:
                navigationHandler.pushNavigation(chatId, CALLBACK_MAIN_MENU);
                broadcastHandler.startBroadcastCustomAll(chatId, messageId);
                break;
            case CALLBACK_BROADCAST_CUSTOM_ALL_CONFIRM:
                broadcastHandler.executeBroadcastCustomAll(chatId, messageId);
                break;
            case CALLBACK_BROADCAST_INFORMATION_ALL:
                navigationHandler.pushNavigation(chatId, CALLBACK_MENU_BROADCAST);
                broadcastHandler.confirmBroadcastInformationAll(chatId, messageId);
                break;
            case CALLBACK_BROADCAST_INFORMATION_ALL_CONFIRM:
                broadcastHandler.executeBroadcastInformationAll(chatId, messageId);
                break;
            case CALLBACK_BROADCAST_INFORMATION_SINGLE:
                navigationHandler.pushNavigation(chatId, CALLBACK_MENU_BROADCAST);
                broadcastHandler.startBroadcastInformationSingle(chatId, messageId);
                break;

            default:
                handleDynamicCallback(chatId, messageId, data);
                break;
        }
    }

    /**
     * Handle dynamic callbacks (challenge selection, channel removal, etc.)
     */
    private void handleDynamicCallback(Long chatId, Integer messageId, String data) {
        if (data.startsWith(CALLBACK_CHALLENGE_SELECT_PREFIX)) {
            navigationHandler.pushNavigation(chatId, CALLBACK_CHALLENGE_LIST);
            Long challengeId = Long.parseLong(data.substring(CALLBACK_CHALLENGE_SELECT_PREFIX.length()));
            challengeManagementHandler.selectChallenge(chatId, messageId, challengeId);
        } else if (data.startsWith("remove_channel_")) {
            String channelId = data.substring("remove_channel_".length());
            channelManagementHandler.handleRemoveChannelConfirm(chatId, messageId, channelId);
        } else {
            log.warn("Unknown callback data: {}", data);
        }
    }

    /**
     * Handle back button navigation
     */
    private void handleBackButton(Long chatId, Integer messageId) {
        String previousCallback = navigationHandler.popNavigation(chatId);
        log.info("Back button pressed by chatId={}, navigating to: {}", chatId, previousCallback);

        switch (previousCallback) {
            case CALLBACK_CHALLENGE_MANAGEMENT:
            case CALLBACK_MAIN_MENU:
                navigationHandler.showChallengeManagementMenu(chatId, messageId);
                break;
            case CALLBACK_CHALLENGE_LIST:
                challengeManagementHandler.showChallengeList(chatId, messageId);
                break;
            case CALLBACK_MENU_CHANNELS:
                navigationHandler.showChannelMenu(chatId, messageId);
                break;
            case CALLBACK_MENU_SETTINGS:
                navigationHandler.showSettingsMenu(chatId, messageId);
                break;
            case CALLBACK_MENU_BROADCAST:
                navigationHandler.showBroadcastMenu(chatId, messageId);
                break;
            default:
                if (previousCallback.startsWith(CALLBACK_CHALLENGE_SELECT_PREFIX)) {
                    Long challengeId = Long
                            .parseLong(previousCallback.substring(CALLBACK_CHALLENGE_SELECT_PREFIX.length()));
                    challengeManagementHandler.selectChallenge(chatId, messageId, challengeId);
                } else {
                    navigationHandler.showChallengeManagementMenu(chatId, messageId);
                }
                break;
        }
    }

    /**
     * Handle cancel operation
     */
    private void handleCancelOperation(Long chatId, Integer messageId) {
        stateManager.clearAll(chatId);
        navigationHandler.clearNavigation(chatId);
        cleanupTempMessages();
        navigationHandler.showChallengeManagementMenu(chatId, messageId);
    }

    // ==================== Utility Methods ====================

    private Long currentChatIdForCleanup;

    private void trackTempMessage(Long chatId, Integer messageId) {
        currentChatIdForCleanup = chatId;
        stateManager.trackTempMessage(chatId, messageId);
    }

    private void cleanupTempMessages() {
        if (currentChatIdForCleanup != null) {
            for (Integer msgId : stateManager.getTempMessageIds(currentChatIdForCleanup)) {
                deleteMessage(currentChatIdForCleanup, msgId);
            }
            stateManager.clearTempMessages(currentChatIdForCleanup);
        }
    }

    private boolean isAdmin(Long chatId) {
        return botUtils.isAdmin(chatId);
    }

    private void sendMessage(Long chatId, String text) {
        botUtils.sendMessage(this, chatId, text);
    }

    private void deleteMessage(Long chatId, Integer messageId) {
        botUtils.deleteMessage(this, chatId, messageId);
    }
}
