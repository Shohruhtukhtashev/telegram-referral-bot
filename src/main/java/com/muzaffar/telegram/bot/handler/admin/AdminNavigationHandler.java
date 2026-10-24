package com.muzaffar.telegram.bot.handler.admin;

import static com.muzaffar.telegram.constants.BotConstants.*;

import com.muzaffar.telegram.util.KeyboardBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.function.Supplier;

import com.muzaffar.telegram.entity.Challenge;
import com.muzaffar.telegram.service.ChallengeService;
import com.muzaffar.telegram.service.ChallengeUserService;
import java.util.List;

/**
 * Handles navigation and menu management for admin bot.
 * Manages back button navigation and menu display.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminNavigationHandler {

    private final AdminStateManager stateManager;
    private final ChallengeUserService challengeUserService;
    private final ChallengeService challengeService;

    private final Map<Long, Stack<String>> navigationHistory = new HashMap<>();
    private Supplier<TelegramLongPollingBot> botSupplier;

    /**
     * Initialize with bot reference
     */
    public void init(Supplier<TelegramLongPollingBot> botSupplier) {
        this.botSupplier = botSupplier;
    }

    // ==================== Navigation History ====================

    /**
     * Push current menu to navigation history
     */
    public void pushNavigation(Long chatId, String currentCallback) {
        navigationHistory.computeIfAbsent(chatId, k -> new Stack<>()).push(currentCallback);
        log.debug("Pushed to navigation history for chatId={}: {}", chatId, currentCallback);
    }

    /**
     * Get previous menu from navigation history
     */
    public String popNavigation(Long chatId) {
        Stack<String> history = navigationHistory.get(chatId);
        if (history != null && !history.isEmpty()) {
            String previous = history.pop();
            log.debug("Popped from navigation history for chatId={}: {}", chatId, previous);
            return previous;
        }
        return CALLBACK_CHALLENGE_MANAGEMENT;
    }

    /**
     * Clear navigation history for user
     */
    public void clearNavigation(Long chatId) {
        navigationHistory.remove(chatId);
        log.debug("Cleared navigation history for chatId={}", chatId);
    }

    // ==================== Menu Display ====================

    /**
     * Build the text for challenge management / admin dashboard
     */
    private String buildDashboardText() {
        long totalUsers = challengeUserService.getTotalUniqueUsers();
        long verifiedUsers = challengeUserService.getVerifiedUniqueUsers();
        long completedUsers = challengeUserService.getCompletedUniqueUsers();

        return String.format(
                "📊 *Admin boshqaruv paneli*\n\n" +
                "👥 *Botdagi jami obunachilar:* %d ta\n" +
                "✅ *Obunani tasdiqlaganlar:* %d ta\n" +
                "🎯 *Mukofot olganlar:* %d ta\n\n" +
                "Kerakli amalni tanlang:",
                totalUsers, verifiedUsers, completedUsers);
    }

    /**
     * Build the keyboard for challenge management / admin dashboard
     */
    private InlineKeyboardMarkup buildDashboardKeyboard() {
        return new KeyboardBuilder()
                .addButton("➕ Yangi challenge", CALLBACK_CHALLENGE_CREATE)
                .addButton("📋 Challengelar", CALLBACK_CHALLENGE_LIST)
                .newRow()
                .addButton("📢 Barchaga xabar yuborish", CALLBACK_BROADCAST_CUSTOM_ALL)
                .newRow()
                .addButton("📊 To'liq statistika", CALLBACK_ADMIN_STATS)
                .build();
    }

    /**
     * Show challenge management menu (new message)
     */
    public void showChallengeManagementMenu(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(buildDashboardText());
        message.setParseMode("Markdown");
        message.setReplyMarkup(buildDashboardKeyboard());

        try {
            botSupplier.get().execute(message);
        } catch (TelegramApiException e) {
            log.error("Error showing challenge management menu", e);
        }
    }

    /**
     * Show challenge management menu (edit existing message)
     */
    public void showChallengeManagementMenu(Long chatId, Integer messageId) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(buildDashboardText());
        message.setParseMode("Markdown");
        message.setReplyMarkup(buildDashboardKeyboard());

        try {
            botSupplier.get().execute(message);
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException e) {
            if (e.getMessage() != null && e.getMessage().contains("message is not modified")) {
                log.debug("Message already shows challenge management menu, skipping update for chatId={}", chatId);
            } else {
                log.error("Error showing challenge management menu", e);
            }
        } catch (TelegramApiException e) {
            log.error("Error showing challenge management menu", e);
        }
    }

    /**
     * Show channel menu
     */
    public void showChannelMenu(Long chatId, Integer messageId) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText("📺 *Kanallarni boshqarish*\n\nKerakli amalni tanlang:");
        message.setParseMode("Markdown");

        InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                .addButton("➕ Kanal qo'shish", CALLBACK_CHANNEL_ADD)
                .newRow()
                .addButton("📋 Kanallar ro'yxati", CALLBACK_CHANNEL_LIST)
                .newRow()
                .addButton("🗑 Kanalni o'chirish", CALLBACK_CHANNEL_REMOVE)
                .newRow()
                .addButton("🔙 Orqaga", CALLBACK_BACK)
                .build();
        message.setReplyMarkup(keyboard);

        try {
            botSupplier.get().execute(message);
        } catch (TelegramApiException e) {
            log.error("Error showing channel menu", e);
        }
    }

    /**
     * Show settings menu
     */
    public void showSettingsMenu(Long chatId, Integer messageId) {
        cleanupTempMessages(chatId);

        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText("⚙️ *Sozlamalar*\n\n🔗 Kerakli amallarni tanlang:");
        message.setParseMode("Markdown");

        InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                .addButton("🔢 Referallar sonini o'zgartirish", CALLBACK_SETTINGS_SET_REFERRALS)
                .newRow()
                .addButton("🔗 Shaxsiy kanal ID o'zgartirish", CALLBACK_SETTINGS_SET_PRIVATE_LINK)
                .newRow()
                .addButton("📹 Video qo'llanma linki", CALLBACK_SETTINGS_SET_VIDEO_GUIDE)
                .newRow()
                .addButton("📝 Information xabarini o'zgartirish", CALLBACK_SETTINGS_INFORMATION)
                .newRow()
                .addButton("📤 Forward xabarini o'zgartirish", CALLBACK_SETTINGS_SET_FORWARD_MESSAGE)
                .newRow()
                .addButton("🔙 Orqaga", CALLBACK_BACK)
                .build();
        message.setReplyMarkup(keyboard);

        try {
            botSupplier.get().execute(message);
        } catch (TelegramApiException e) {
            log.error("Error showing settings menu", e);
        }
    }

    /**
     * Show broadcast menu
     */
    public void showBroadcastMenu(Long chatId, Integer messageId) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId.toString());
        message.setMessageId(messageId);
        message.setText(
                "📢 *Xabar yuborish*\n\n" +
                        "Quyidagi variantlardan birini tanlang:");
        message.setParseMode("Markdown");

        InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                .addButton("📢 Barchaga yangi xabar yuborish", CALLBACK_BROADCAST_CUSTOM_ALL)
                .newRow()
                .addButton("📝 Information xabarini barchaga", CALLBACK_BROADCAST_INFORMATION_ALL)
                .newRow()
                .addButton("👥 Tanlangan foydalanuvchilarga", CALLBACK_BROADCAST_INFORMATION_SINGLE)
                .newRow()
                .addButton("🔙 Orqaga", CALLBACK_BACK)
                .build();
        message.setReplyMarkup(keyboard);

        try {
            botSupplier.get().execute(message);
        } catch (TelegramApiException e) {
            log.error("Error showing broadcast menu", e);
        }
    }

    /**
     * Show full admin statistics
     */
    public void showFullAdminStats(Long chatId, Integer messageId) {
        long totalUsers = challengeUserService.getTotalUniqueUsers();
        long verifiedUsers = challengeUserService.getVerifiedUniqueUsers();
        long completedUsers = challengeUserService.getCompletedUniqueUsers();

        StringBuilder sb = new StringBuilder();
        sb.append("📊 *Bot to'liq statistikasi*\n\n");
        sb.append(String.format("👥 *Jami obunachilar (start bosganlar):* %d ta\n", totalUsers));
        sb.append(String.format("✅ *Kanallarga obuna bo'lganlar:* %d ta\n", verifiedUsers));
        sb.append(String.format("🎯 *Vazifani bajarganlar (mukofot olganlar):* %d ta\n\n", completedUsers));

        List<Challenge> challenges = challengeService.getAllChallenges();
        if (!challenges.isEmpty()) {
            sb.append("🏆 *Challengelar bo'yicha ko'rsatkichlar:*\n\n");
            for (Challenge ch : challenges) {
                ChallengeService.ChallengeStats stats = challengeService.getChallengeStats(ch.getId());
                String mainLabel = ch.getIsMain() ? " ⭐" : "";
                String activeLabel = ch.getIsActive() ? "✅ Faol" : "❌ Faol emas";

                sb.append(String.format("• *%s*%s (%s):\n", ch.getName(), mainLabel, activeLabel));
                sb.append(String.format("   Ishtirokchilar: %d ta\n", stats.totalUsers()));
                sb.append(String.format("   Bajarganlar: %d ta\n", stats.completedUsers()));
                sb.append(String.format("   Kerakli referallar: %d ta\n\n", ch.getRequiredReferralCount()));
            }
        }

        InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                .addButton("🔙 Asosiy menyu", CALLBACK_MAIN_MENU)
                .build();

        if (messageId != null) {
            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId.toString());
            edit.setMessageId(messageId);
            edit.setText(sb.toString());
            edit.setParseMode("Markdown");
            edit.setReplyMarkup(keyboard);
            try {
                botSupplier.get().execute(edit);
            } catch (TelegramApiException e) {
                log.error("Error editing full stats message", e);
            }
        } else {
            SendMessage msg = new SendMessage();
            msg.setChatId(chatId.toString());
            msg.setText(sb.toString());
            msg.setParseMode("Markdown");
            msg.setReplyMarkup(keyboard);
            try {
                botSupplier.get().execute(msg);
            } catch (TelegramApiException e) {
                log.error("Error sending full stats message", e);
            }
        }
    }

    /**
     * Cleanup temporary messages for user
     */
    private void cleanupTempMessages(Long chatId) {
        // Implementation delegated to the bot class
    }

    // ==================== Keyboard Helpers ====================

    /**
     * Create inline keyboard with back button
     */
    public InlineKeyboardMarkup createBackButton() {
        return KeyboardBuilder.singleButton("🔙 Orqaga", CALLBACK_BACK);
    }

    /**
     * Create inline keyboard with cancel button
     */
    public InlineKeyboardMarkup createCancelButton() {
        return KeyboardBuilder.singleButton(BUTTON_CANCEL, CALLBACK_CANCEL_OPERATION);
    }
}
