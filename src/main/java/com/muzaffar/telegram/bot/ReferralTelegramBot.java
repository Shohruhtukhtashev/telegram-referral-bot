package com.muzaffar.telegram.bot;

import static com.muzaffar.telegram.constants.BotConstants.*;

import com.muzaffar.telegram.bot.handler.user.ChannelVerificationHandler;
import com.muzaffar.telegram.bot.handler.user.InlineQueryHandler;
import com.muzaffar.telegram.bot.handler.user.ReferralHandler;
import com.muzaffar.telegram.bot.handler.user.StartCommandHandler;
import com.muzaffar.telegram.bot.handler.user.StatsCommandHandler;
import com.muzaffar.telegram.config.BotConfig;
import com.muzaffar.telegram.service.UserSessionService;
import com.muzaffar.telegram.util.TelegramBotUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Refactored ReferralTelegramBot that delegates to handlers.
 * This version is significantly smaller and more maintainable.
 */
@Slf4j
@Component
public class ReferralTelegramBot extends TelegramLongPollingBot {

    private final BotConfig botConfig;
    private final TelegramBotUtils botUtils;
    private final UserSessionService sessionService;

    // Handlers
    private final StartCommandHandler startCommandHandler;
    private final StatsCommandHandler statsCommandHandler;
    private final ChannelVerificationHandler channelVerificationHandler;
    private final ReferralHandler referralHandler;
    private final InlineQueryHandler inlineQueryHandler;

    // High performance asynchronous update processing pool
    private final ExecutorService updateExecutor = Executors.newFixedThreadPool(16);

    public ReferralTelegramBot(BotConfig botConfig,
            TelegramBotUtils botUtils,
            UserSessionService sessionService,
            StartCommandHandler startCommandHandler,
            StatsCommandHandler statsCommandHandler,
            ChannelVerificationHandler channelVerificationHandler,
            ReferralHandler referralHandler,
            InlineQueryHandler inlineQueryHandler) {
        super(botConfig.getBot().getToken());
        this.botConfig = botConfig;
        this.botUtils = botUtils;
        this.sessionService = sessionService;
        this.startCommandHandler = startCommandHandler;
        this.statsCommandHandler = statsCommandHandler;
        this.channelVerificationHandler = channelVerificationHandler;
        this.referralHandler = referralHandler;
        this.inlineQueryHandler = inlineQueryHandler;
    }

    @PostConstruct
    public void initHandlers() {
        // Initialize handlers with bot reference
        startCommandHandler.init(() -> this, this::getBotUsername);
        statsCommandHandler.init(() -> this, this::getBotUsername);
        channelVerificationHandler.init(() -> this);
        referralHandler.init(() -> this, this::getBotUsername);
        inlineQueryHandler.init(() -> this, this::getBotUsername);
    }

    @Override
    public String getBotUsername() {
        return botConfig.getBot().getUsername();
    }

    @Override
    public void clearWebhook() throws org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException {
        try {
            super.clearWebhook();
        } catch (Exception e) {
            log.warn("⚠️ Could not clear webhook for User bot, proceeding anyway: {}", e.getMessage());
        }
    }

    /**
     * Set user challenge context (used by admin bot for broadcasting)
     */
    public void setUserChallengeContext(Long chatId, Long challengeId) {
        sessionService.setChallengeContext(chatId, challengeId);
    }

    @Override
    public void onUpdateReceived(Update update) {
        updateExecutor.submit(() -> {
            try {
                if (update.hasMessage() && update.getMessage().hasText()) {
                    handleTextMessage(update);
                } else if (update.hasCallbackQuery()) {
                    handleCallbackQuery(update.getCallbackQuery());
                } else if (update.hasInlineQuery()) {
                    inlineQueryHandler.handle(update);
                }
            } catch (Exception e) {
                log.error("Error processing update", e);
                if (update.hasMessage()) {
                    sendMessage(update.getMessage().getChatId(), "❌ Xatolik yuz berdi. Iltimos, qaytadan urinib ko'ring.");
                }
            }
        });
    }

    /**
     * Handle text messages - delegates to appropriate handler
     */
    private void handleTextMessage(Update update) {
        String text = update.getMessage().getText();
        Long chatId = update.getMessage().getChatId();

        log.info("Received message from chatId={}: {}", chatId, text);

        if (startCommandHandler.canHandle(update)) {
            startCommandHandler.handle(update);
        } else if (statsCommandHandler.canHandle(update)) {
            statsCommandHandler.handle(update);
        } else {
            // Silently delete unknown messages to keep chat clean
            deleteMessage(chatId, update.getMessage().getMessageId());
        }
    }

    /**
     * Handle callback queries
     */
    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String data = callbackQuery.getData();
        String username = callbackQuery.getFrom().getUserName();
        String firstName = callbackQuery.getFrom().getFirstName();
        String lastName = callbackQuery.getFrom().getLastName();
        String callbackId = callbackQuery.getId();

        log.info("Received callback from chatId={}: {}", chatId, data);

        switch (data) {
            case CALLBACK_START_BOT:
                botUtils.answerCallbackQuery(this, callbackId, null, false);
                startCommandHandler.handleStartButton(chatId, messageId, username, firstName, lastName);
                break;

            case CALLBACK_VERIFY_CHANNELS:
                channelVerificationHandler.handleChannelVerification(
                        chatId, messageId, callbackId, username, firstName, lastName,
                        () -> startCommandHandler.sendInformationMessageAfterVerification(chatId, username, firstName,
                                lastName));
                break;

            case CALLBACK_MY_REFERRALS:
                botUtils.answerCallbackQuery(this, callbackId, null, false);
                referralHandler.handleMyReferrals(chatId, username, firstName, lastName);
                break;

            default:
                botUtils.answerCallbackQuery(this, callbackId, null, false);
                log.warn("Unknown callback data: {}", data);
                break;
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Send a simple text message
     */
    private void sendMessage(Long chatId, String text) {
        botUtils.sendMessage(this, chatId, text);
    }

    /**
     * Delete a message
     */
    private void deleteMessage(Long chatId, Integer messageId) {
        botUtils.deleteMessage(this, chatId, messageId);
    }
}
