package com.muzaffar.telegram.bot.handler.admin;

import static com.muzaffar.telegram.constants.BotConstants.*;

import com.muzaffar.telegram.entity.Challenge;
import com.muzaffar.telegram.enums.MessageKey;
import com.muzaffar.telegram.service.ChallengeService;
import com.muzaffar.telegram.service.MessageTemplateService;
import com.muzaffar.telegram.util.KeyboardBuilder;
import com.muzaffar.telegram.util.TelegramBotUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Supplier;

/**
 * Handler for settings operations.
 * Manages referral count, private link, information message, and stats header
 * settings.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingsHandler {

    private final ChallengeService challengeService;
    private final MessageTemplateService messageTemplateService;
    private final AdminStateManager stateManager;
    private final AdminNavigationHandler navigationHandler;
    private final TelegramBotUtils botUtils;

    private Supplier<TelegramLongPollingBot> botSupplier;

    public void init(Supplier<TelegramLongPollingBot> botSupplier) {
        this.botSupplier = botSupplier;
    }

    // ==================== Referral Count ====================

    public void startSetReferrals(Long chatId, Integer messageId) {
        Long challengeId = stateManager.getCurrentChallengeId(chatId);
        if (challengeId == null) {
            sendMessage(chatId, "❌ Challenge tanlanmagan.");
            return;
        }

        Challenge challenge = challengeService.getChallengeById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));

        stateManager.setState(chatId, STATE_AWAITING_REFERRAL_COUNT);
        stateManager.setMenuMessageId(chatId, messageId);

        int currentCount = challenge.getRequiredReferralCount();
        InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                .addButton("🔙 Orqaga", CALLBACK_MENU_SETTINGS)
                .build();
        editMessage(chatId, messageId,
                String.format(
                        "🔢 *Referallar sonini o'zgartirish*\n\nHozirgi qiymat: *%d*\n\nYangi qiymatni yuboring (1 dan katta son):",
                        currentCount),
                keyboard);
    }

    public void handleReferralCountInput(Long chatId, String text, Integer messageId) {
        Long challengeId = stateManager.getCurrentChallengeId(chatId);
        if (challengeId == null) {
            sendMessage(chatId, "❌ Challenge tanlanmagan.", navigationHandler.createBackButton());
            stateManager.clearState(chatId);
            stateManager.clearMenuMessageId(chatId);
            return;
        }

        try {
            int count = Integer.parseInt(text.trim());

            if (count < 1) {
                InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                        .addButton("🔙 Orqaga", CALLBACK_MENU_SETTINGS)
                        .build();
                sendMessage(chatId,
                        "❌ *Noto'g'ri format:*\n\nReferallar soni kamida 1 bo'lishi kerak.\n\nQaytadan kiriting:",
                        keyboard);
                return;
            }

            botUtils.deleteMessage(botSupplier.get(), chatId, messageId);

            challengeService.updateRequiredReferralCount(challengeId, count);

            sendMessage(chatId, String.format("✅ *Referallar soni o'zgartirildi!*\n\nYangi qiymat: *%d*", count),
                    navigationHandler.createBackButton());

            stateManager.clearState(chatId);
            stateManager.clearMenuMessageId(chatId);
        } catch (NumberFormatException e) {
            InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                    .addButton("🔙 Orqaga", CALLBACK_MENU_SETTINGS)
                    .build();
            sendMessage(chatId, "❌ *Noto'g'ri format:*\n\nFaqat raqam kiriting. Qaytadan urinib ko'ring.", keyboard);
        }
    }

    // ==================== Private Link ====================

    public void startSetPrivateLink(Long chatId, Integer messageId) {
        Long challengeId = stateManager.getCurrentChallengeId(chatId);
        if (challengeId == null) {
            sendMessage(chatId, "❌ Challenge tanlanmagan.");
            return;
        }

        Challenge challenge = challengeService.getChallengeById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));

        stateManager.setState(chatId, STATE_AWAITING_PRIVATE_LINK);
        stateManager.setMenuMessageId(chatId, messageId);

        String currentId = challenge.getPrivateChannelId();
        if (currentId == null || currentId.isEmpty()) {
            currentId = "O'rnatilmagan";
        }
        InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                .addButton("🔙 Orqaga", CALLBACK_MENU_SETTINGS)
                .build();
        editMessage(chatId, messageId,
                String.format("🔗 *Shaxsiy kanal ID o'zgartirish*\n\n" +
                        "Hozirgi ID: `%s`\n\n" +
                        "Bir martalik havolalar yaratish uchun shaxsiy kanalning *ID raqamini* yuboring.\n\n" +
                        "*Masalan:* `-1001234567890`\n\n" +
                        "⚠️ *Muhim:* Bot shaxsiy kanalda admin bo'lishi kerak!", currentId),
                keyboard);
    }

    public void handlePrivateLinkInput(Long chatId, String text, Integer messageId) {
        Long challengeId = stateManager.getCurrentChallengeId(chatId);
        if (challengeId == null) {
            sendMessage(chatId, "❌ Challenge tanlanmagan.", navigationHandler.createBackButton());
            stateManager.clearState(chatId);
            stateManager.clearMenuMessageId(chatId);
            return;
        }

        String channelId = text.trim();

        if (!channelId.matches("-?\\d+")) {
            InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                    .addButton("🔙 Orqaga", CALLBACK_MENU_SETTINGS)
                    .build();
            sendMessage(chatId,
                    "❌ *Noto'g'ri format:*\n\nKanal ID faqat raqamlardan iborat bo'lishi kerak (masalan: `-1001234567890`).\n\nQaytadan kiriting:",
                    keyboard);
            return;
        }

        botUtils.deleteMessage(botSupplier.get(), chatId, messageId);

        challengeService.updatePrivateChannelId(challengeId, channelId);

        sendMessage(chatId, String.format("✅ *Shaxsiy kanal ID o'zgartirildi!*\n\nYangi ID: `%s`\n\n" +
                "Endi bir martalik havolalar avtomatik yaratiladi.", channelId), navigationHandler.createBackButton());

        stateManager.clearState(chatId);
        stateManager.clearMenuMessageId(chatId);
    }

    // ==================== Information Message ====================

    public void startSetInformationMessage(Long chatId, Integer messageId) {
        botUtils.deleteMessage(botSupplier.get(), chatId, messageId);

        Long challengeId = getCurrentChallengeId(chatId);
        messageTemplateService.getImagePath(challengeId, MessageKey.INFORMATION_MESSAGE).ifPresent(imagePath -> {
            try {
                Message sentPhoto = botUtils.sendPhotoWithCaption(botSupplier.get(), chatId, imagePath, "🖼 *Hozirgi rasm:*");
                if (sentPhoto != null) {
                    stateManager.trackTempMessage(chatId, sentPhoto.getMessageId());
                }
            } catch (Exception e) {
                log.error("Error sending current information image", e);
            }
        });

        String currentText = messageTemplateService.getText(challengeId, MessageKey.INFORMATION_MESSAGE);
        boolean hasImage = messageTemplateService.getImagePath(challengeId, MessageKey.INFORMATION_MESSAGE).isPresent();

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(
                String.format(
                        "📝 *Information xabarini o'zgartirish*\n\n" +
                                "📝 *Hozirgi matn:*\n```\n%s\n```\n\n" +
                                "🖼 *Rasm:* %s\n\n" +
                                "Kerakli amalni tanlang:",
                        currentText,
                        hasImage ? "✅ O'rnatilgan" : "❌ O'rnatilmagan"));
        message.setParseMode("Markdown");

        KeyboardBuilder builder = new KeyboardBuilder()
                .addRow("🔹 Matnni o'zgartirish", CALLBACK_EDIT_INFORMATION_TEXT)
                .addRow(hasImage ? "🔹 Rasmni o'zgartirish" : "🔹 Rasm qo'shish", CALLBACK_EDIT_INFORMATION_IMAGE);

        if (hasImage) {
            builder.addButton("🔹 Rasmni o'chirish", CALLBACK_DELETE_INFORMATION_IMAGE);
        }

        InlineKeyboardMarkup keyboard = builder
                .addRow("🔙 Orqaga", CALLBACK_MENU_SETTINGS)
                .build();
        message.setReplyMarkup(keyboard);

        try {
            botSupplier.get().execute(message);
        } catch (TelegramApiException e) {
            log.error("Error showing information message menu", e);
        }
    }

    public void startEditInformationText(Long chatId, Integer messageId) {
        stateManager.setState(chatId, STATE_AWAITING_INFORMATION_MESSAGE);
        stateManager.setMenuMessageId(chatId, messageId);

        Long challengeId = getCurrentChallengeId(chatId);
        String currentText = messageTemplateService.getText(challengeId, MessageKey.INFORMATION_MESSAGE);

        editMessage(chatId, messageId,
                String.format(
                        "📝 *Information matnini o'zgartirish*\n\n" +
                                "📝 *Hozirgi matn:*\n```\n%s\n```\n\n" +
                                "Yangi matnni yuboring:\n\n" +
                                "*Eslatma:* Markdown formatidan foydalanishingiz mumkin (*bold*, _italic_, `code`)",
                        currentText),
                new KeyboardBuilder()
                        .addButton("🔙 Orqaga", CALLBACK_SETTINGS_INFORMATION)
                        .build());
    }

    public void startEditInformationImage(Long chatId, Integer messageId) {
        stateManager.setState(chatId, STATE_AWAITING_INFORMATION_IMAGE);

        Long challengeId = getCurrentChallengeId(chatId);
        messageTemplateService.getImagePath(challengeId, MessageKey.INFORMATION_MESSAGE).ifPresent(imagePath -> {
            try {
                Message sentPhoto = botUtils.sendPhotoWithCaption(botSupplier.get(), chatId, imagePath,
                        "🖼 *Hozirgi rasm:*");
                if (sentPhoto != null) {
                    stateManager.trackTempMessage(chatId, sentPhoto.getMessageId());
                }
            } catch (Exception e) {
                log.error("Error sending current information image", e);
            }
        });

        Message message = sendMessage(chatId,
                "🖼 *Information rasmini o'zgartirish*\n\n" +
                        "Yangi rasmni yuboring (foto yoki fayl ko'rinishida):\n\n" +
                        "*Eslatma:* Sifatli rasm uchun faylni siqilmagan holda yuboring (Document).",
                new KeyboardBuilder()
                        .addButton("🔙 Orqaga", CALLBACK_SETTINGS_INFORMATION)
                        .build());
        if (message != null) {
            stateManager.trackTempMessage(chatId, message.getMessageId());
        }
    }

    public void confirmDeleteInformationImage(Long chatId, Integer messageId) {
        Long challengeId = getCurrentChallengeId(chatId);
        messageTemplateService.getImagePath(challengeId, MessageKey.INFORMATION_MESSAGE).ifPresent(oldPath -> {
            try {
                String fullOldPath = botUtils.getFullImagePath(oldPath);
                Files.deleteIfExists(Paths.get(fullOldPath));
                log.info("Deleted information image: {}", fullOldPath);
            } catch (IOException e) {
                log.error("Failed to delete old image file", e);
            }
        });

        messageTemplateService.updateImagePath(challengeId, MessageKey.INFORMATION_MESSAGE, "");

        editMessage(chatId, messageId,
                "✅ *Information rasmi o'chirildi!*\n\nRasm endi ko'rsatilmaydi.",
                navigationHandler.createBackButton());
    }

    public void handleInformationMessageInput(Long chatId, String text, Integer messageId) {
        String trimmedText = text.trim();

        if (trimmedText.isEmpty()) {
            InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                    .addButton("🔙 Orqaga", CALLBACK_SETTINGS_INFORMATION)
                    .build();
            sendMessage(chatId, "❌ *Noto'g'ri format:*\n\nXabar bo'sh bo'lishi mumkin emas.\n\nQaytadan kiriting:",
                    keyboard);
            return;
        }

        botUtils.deleteMessage(botSupplier.get(), chatId, messageId);
        Long challengeId = getCurrentChallengeId(chatId);
        messageTemplateService.updateText(challengeId, MessageKey.INFORMATION_MESSAGE, trimmedText);
        sendMessage(chatId, "✅ *Information xabari o'zgartirildi!*\n\nYangi foydalanuvchilar ushbu xabarni ko'rishadi.",
                navigationHandler.createBackButton());

        stateManager.clearState(chatId);
        stateManager.clearMenuMessageId(chatId);
    }

    // ==================== FORWARD_MESSAGE Settings ====================

    /**
     * Start editing forward message - show submenu
     */
    public void startSetForwardMessage(Long chatId, Integer messageId) {
        Long challengeId = getCurrentChallengeId(chatId);

        String currentText = messageTemplateService.getText(challengeId, MessageKey.FORWARD_MESSAGE);
        boolean hasImage = messageTemplateService.getImagePath(challengeId, MessageKey.FORWARD_MESSAGE).isPresent();

        StringBuilder message = new StringBuilder();
        message.append("📤 *Forward xabar sozlamalari*\n\n");
        message.append(
                "Bu xabar statistikadan keyin yuboriladi va Do'stlarimga ulashish buttoni bilan birga keladi.\n\n");
        message.append("*Hozirgi matn:*\n");
        if (currentText != null && !currentText.isEmpty()) {
            message.append(currentText).append("\n\n");
        } else {
            message.append("_(Bo'sh)_\n\n");
        }
        message.append("*Rasm:* ").append(hasImage ? "✅ Mavjud" : "❌ Mavjud emas");

        KeyboardBuilder keyboardBuilder = new KeyboardBuilder()
                .addButton("✏️ Matnni o'zgartirish", CALLBACK_EDIT_FORWARD_TEXT)
                .newRow()
                .addButton("🖼 Rasmni o'zgartirish", CALLBACK_EDIT_FORWARD_IMAGE);

        if (hasImage) {
            keyboardBuilder.newRow()
                    .addButton("🗑 Rasmni o'chirish", CALLBACK_DELETE_FORWARD_IMAGE);
        }

        keyboardBuilder.newRow()
                .addButton("🔙 Orqaga", CALLBACK_BACK);

        // Delete old message and send image first if exists, then menu message
        if (hasImage) {
            botUtils.deleteMessage(botSupplier.get(), chatId, messageId);

            // Send image first
            messageTemplateService.getImagePath(challengeId, MessageKey.FORWARD_MESSAGE).ifPresent(imagePath -> {
                try {
                    botUtils.sendPhotoFromFile(botSupplier.get(), chatId, imagePath);
                } catch (Exception e) {
                    log.error("Error sending forward message image preview", e);
                }
            });

            // Then send menu message
            sendMessage(chatId, message.toString(), keyboardBuilder.build());
        } else {
            // No image, just edit the existing message
            editMessage(chatId, messageId, message.toString(), keyboardBuilder.build());
        }
    }

    /**
     * Start editing forward message text
     */
    public void startEditForwardText(Long chatId, Integer messageId) {
        Long challengeId = getCurrentChallengeId(chatId);

        stateManager.setState(chatId, STATE_AWAITING_FORWARD_MESSAGE);
        stateManager.setMenuMessageId(chatId, messageId);

        String currentText = messageTemplateService.getText(challengeId, MessageKey.FORWARD_MESSAGE);

        StringBuilder message = new StringBuilder();
        message.append("📤 *Forward xabar matnini o'zgartirish*\n\n");
        message.append("*Hozirgi matn:*\n");
        if (currentText != null && !currentText.isEmpty()) {
            message.append(currentText).append("\n\n");
        } else {
            message.append("_(Bo'sh)_\n\n");
        }
        message.append("Yangi matnni kiriting:");

        InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                .addButton("🔙 Orqaga", CALLBACK_SETTINGS_SET_FORWARD_MESSAGE)
                .build();

        editMessage(chatId, messageId, message.toString(), keyboard);
    }

    /**
     * Start editing forward message image
     */
    public void startEditForwardImage(Long chatId, Integer messageId) {
        Long challengeId = getCurrentChallengeId(chatId);

        // Send current image if exists
        messageTemplateService.getImagePath(challengeId, MessageKey.FORWARD_MESSAGE).ifPresent(imagePath -> {
            try {
                botUtils.sendPhotoFromFile(botSupplier.get(), chatId, imagePath);
            } catch (Exception e) {
                log.error("Error sending forward image preview", e);
            }
        });

        stateManager.setState(chatId, STATE_AWAITING_FORWARD_IMAGE);
        stateManager.setMenuMessageId(chatId, messageId);

        InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                .addButton("🔙 Orqaga", CALLBACK_SETTINGS_SET_FORWARD_MESSAGE)
                .build();

        editMessage(chatId, messageId,
                "🖼 *Forward xabar rasmini o'zgartirish*\n\nYangi rasmni yuboring:",
                keyboard);
    }

    /**
     * Confirm delete forward message image
     */
    public void confirmDeleteForwardImage(Long chatId, Integer messageId) {
        Long challengeId = getCurrentChallengeId(chatId);

        // Delete the image file
        messageTemplateService.getImagePath(challengeId, MessageKey.FORWARD_MESSAGE).ifPresent(oldPath -> {
            try {
                String fullPath = botUtils.getFullImagePath(oldPath);
                Files.deleteIfExists(Paths.get(fullPath));
                log.info("Deleted forward image: {}", fullPath);
            } catch (Exception e) {
                log.error("Error deleting forward image: {}", oldPath, e);
            }
        });

        // Clear image path in database
        messageTemplateService.updateImagePath(challengeId, MessageKey.FORWARD_MESSAGE, "");

        editMessage(chatId, messageId,
                "✅ *Forward xabar rasmi o'chirildi!*\n\nRasm endi ko'rsatilmaydi.",
                navigationHandler.createBackButton());
    }

    /**
     * Handle forward message text input
     */
    public void handleForwardMessageInput(Long chatId, String text, Integer messageId) {
        String trimmedText = text.trim();

        if (trimmedText.isEmpty()) {
            InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                    .addButton("🔙 Orqaga", CALLBACK_SETTINGS_SET_FORWARD_MESSAGE)
                    .build();
            sendMessage(chatId, "❌ *Noto'g'ri format:*\n\nMatn bo'sh bo'lishi mumkin emas.\n\nQaytadan kiriting:",
                    keyboard);
            return;
        }

        botUtils.deleteMessage(botSupplier.get(), chatId, messageId);
        Long challengeId = getCurrentChallengeId(chatId);
        messageTemplateService.updateText(challengeId, MessageKey.FORWARD_MESSAGE, trimmedText);
        sendMessage(chatId, "✅ *Forward xabar matni o'zgartirildi!*", navigationHandler.createBackButton());

        stateManager.clearState(chatId);
        stateManager.clearMenuMessageId(chatId);
    }

    // ==================== Video Guide URL Settings ====================

    public void startSetVideoGuide(Long chatId, Integer messageId) {
        Long challengeId = getCurrentChallengeId(chatId);
        Challenge challenge = challengeService.getChallengeById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));

        stateManager.setState(chatId, STATE_AWAITING_VIDEO_GUIDE_URL);
        stateManager.setMenuMessageId(chatId, messageId);

        String currentUrl = challenge.getVideoGuideUrl();
        boolean hasUrl = currentUrl != null && !currentUrl.trim().isEmpty();

        KeyboardBuilder builder = new KeyboardBuilder();
        if (hasUrl) {
            builder.addButton("🗑 Linkni o'chirish", CALLBACK_SETTINGS_DELETE_VIDEO_GUIDE)
                   .newRow();
        }
        builder.addButton("🔙 Orqaga", CALLBACK_MENU_SETTINGS);

        String text = String.format(
                "📹 *Video qo'llanma havolasini sozlash*\n\n" +
                "Bu havola yangi foydalanuvchilar /start bosganda xush kelibsiz xabarida ko'rsatiladi.\n\n" +
                "🔗 *Hozirgi havola:* %s\n\n" +
                "Yangi kanal/video havolasini yuboring:\n" +
                "*Masalan:* `https://t.me/Bot_uchun_Videoo`",
                hasUrl ? "`" + currentUrl + "`" : "_(O'rnatilmagan - default: `https://t.me/Bot_uchun_Videoo`)_");

        editMessage(chatId, messageId, text, builder.build());
    }

    public void handleVideoGuideUrlInput(Long chatId, String text, Integer messageId) {
        Long challengeId = getCurrentChallengeId(chatId);
        String raw = text.trim();

        if (raw.isEmpty()) {
            InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                    .addButton("🔙 Orqaga", CALLBACK_MENU_SETTINGS)
                    .build();
            sendMessage(chatId, "❌ Havola bo'sh bo'lishi mumkin emas. Qaytadan kiriting:", keyboard);
            return;
        }

        // Normalize URL
        String normalizedUrl = raw;
        if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
            if (normalizedUrl.startsWith("@")) {
                normalizedUrl = "https://t.me/" + normalizedUrl.substring(1);
            } else if (normalizedUrl.startsWith("t.me/")) {
                normalizedUrl = "https://" + normalizedUrl;
            } else {
                normalizedUrl = "https://" + normalizedUrl;
            }
        }

        botUtils.deleteMessage(botSupplier.get(), chatId, messageId);

        challengeService.updateVideoGuideUrl(challengeId, normalizedUrl);

        sendMessage(chatId,
                String.format("✅ *Video qo'llanma havolasi saqlandi!*\n\n🔗 Havola: `%s`", normalizedUrl),
                navigationHandler.createBackButton());

        stateManager.clearState(chatId);
        stateManager.clearMenuMessageId(chatId);
    }

    public void confirmDeleteVideoGuide(Long chatId, Integer messageId) {
        Long challengeId = getCurrentChallengeId(chatId);
        challengeService.updateVideoGuideUrl(challengeId, null);

        editMessage(chatId, messageId,
                "✅ *Video qo'llanma havolasi o'chirildi!*\n\nStandart havola ishlatiladi.",
                navigationHandler.createBackButton());
    }

    private Long getCurrentChallengeId(Long chatId) {

        Long challengeId = stateManager.getCurrentChallengeId(chatId);
        if (challengeId != null) {
            return challengeId;
        }
        return challengeService.getDefaultChallenge()
                .map(Challenge::getId)
                .orElse(1L);
    }

    private void editMessage(Long chatId, Integer messageId, String text, InlineKeyboardMarkup keyboard) {
        try {
            EditMessageText message = new EditMessageText();
            message.setChatId(chatId.toString());
            message.setMessageId(messageId);
            message.setText(text);
            message.setParseMode("Markdown");
            message.setReplyMarkup(keyboard);
            botSupplier.get().execute(message);
        } catch (TelegramApiException e) {
            log.error("Error editing message", e);
        }
    }

    private void sendMessage(Long chatId, String text) {
        sendMessage(chatId, text, null);
    }

    private Message sendMessage(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        if (keyboard != null) {
            return botUtils.sendMessageWithKeyboard(botSupplier.get(), chatId, text, keyboard);
        } else {
            botUtils.sendMessage(botSupplier.get(), chatId, text);
            return null;
        }
    }
}
