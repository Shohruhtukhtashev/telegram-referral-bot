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
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Handler for image upload operations.
 * Handles photo and document uploads for message templates.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageUploadHandler {

    private final ChallengeService challengeService;
    private final MessageTemplateService messageTemplateService;
    private final AdminStateManager stateManager;
    private final AdminNavigationHandler navigationHandler;
    private final TelegramBotUtils botUtils;

    private Supplier<TelegramLongPollingBot> botSupplier;

    public void init(Supplier<TelegramLongPollingBot> botSupplier) {
        this.botSupplier = botSupplier;
    }

    /**
     * Handle photo upload for message template images
     */
    public void handlePhotoUpload(Long chatId, Message message) {
        String state = stateManager.getState(chatId);

        MessageKey templateKey;
        if (STATE_AWAITING_INFORMATION_MESSAGE.equals(state) || STATE_AWAITING_INFORMATION_IMAGE.equals(state)) {
            templateKey = MessageKey.INFORMATION_MESSAGE;
        } else if (STATE_AWAITING_FORWARD_MESSAGE.equals(state) || STATE_AWAITING_FORWARD_IMAGE.equals(state)) {
            templateKey = MessageKey.FORWARD_MESSAGE;
        } else {
            InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                    .addButton("🔙 Orqaga", CALLBACK_CANCEL_OPERATION)
                    .build();
            sendMessage(chatId, "❌ Rasm kutilmayapti. Iltimos sozlamalar menyusidan boshqa harakatni tanlang.",
                    keyboard);
            return;
        }

        try {
            List<PhotoSize> photos = message.getPhoto();
            PhotoSize photo = photos.stream()
                    .max(Comparator.comparing(PhotoSize::getFileSize))
                    .orElse(null);

            if (photo == null) {
                String backCallback = templateKey == MessageKey.INFORMATION_MESSAGE
                        ? CALLBACK_SETTINGS_INFORMATION
                        : CALLBACK_SETTINGS_SET_FORWARD_MESSAGE;
                InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                        .addButton("🔙 Orqaga", backCallback)
                        .build();
                sendMessage(chatId, "❌ Rasm topilmadi. Qaytadan urinib ko'ring.", keyboard);
                return;
            }

            GetFile getFileMethod = new GetFile();
            getFileMethod.setFileId(photo.getFileId());
            org.telegram.telegrambots.meta.api.objects.File file = botSupplier.get().execute(getFileMethod);

            java.io.File downloadedFile = ((TelegramLongPollingBot) botSupplier.get()).downloadFile(file);

            File processedFile = validateAndProcessImage(downloadedFile, chatId);
            if (processedFile == null) {
                downloadedFile.delete();
                return;
            }

            // If the photo also has a caption, update the text as well
            String caption = message.getCaption();
            if (caption != null && !caption.trim().isEmpty()) {
                Long challengeId = getCurrentChallengeId(chatId);
                messageTemplateService.updateText(challengeId, templateKey, caption.trim());
                log.info("Updated template text from photo caption for {}: {}", templateKey, caption.trim());
            }

            saveImage(chatId, message.getMessageId(), templateKey, processedFile);

        } catch (Exception e) {
            log.error("Error handling photo upload", e);
            String backCallback = STATE_AWAITING_INFORMATION_IMAGE.equals(stateManager.getState(chatId))
                    ? CALLBACK_SETTINGS_INFORMATION
                    : CALLBACK_SETTINGS_SET_FORWARD_MESSAGE;
            InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                    .addButton("🔙 Orqaga", backCallback)
                    .build();
            sendMessage(chatId, "❌ Rasmni yuklashda xatolik yuz berdi. Qaytadan urinib ko'ring.", keyboard);
        }
    }

    /**
     * Handle document upload for message template images
     */
    public void handleDocumentUpload(Long chatId, Message message) {
        String state = stateManager.getState(chatId);

        MessageKey templateKey;
        if (STATE_AWAITING_INFORMATION_MESSAGE.equals(state) || STATE_AWAITING_INFORMATION_IMAGE.equals(state)) {
            templateKey = MessageKey.INFORMATION_MESSAGE;
        } else if (STATE_AWAITING_FORWARD_MESSAGE.equals(state) || STATE_AWAITING_FORWARD_IMAGE.equals(state)) {
            templateKey = MessageKey.FORWARD_MESSAGE;
        } else {
            InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                    .addButton("🔙 Orqaga", CALLBACK_MENU_SETTINGS)
                    .build();
            sendMessage(chatId, "❌ Rasm kutilmayapti. Iltimos sozlamalar menyusidan boshqa harakatni tanlang.",
                    keyboard);
            return;
        }

        try {
            Document document = message.getDocument();

            if (document == null) {
                String backCallback = templateKey == MessageKey.INFORMATION_MESSAGE
                        ? CALLBACK_SETTINGS_INFORMATION
                        : CALLBACK_SETTINGS_SET_FORWARD_MESSAGE;
                InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                        .addButton("🔙 Orqaga", backCallback)
                        .build();
                sendMessage(chatId, "❌ Fayl topilmadi. Qaytadan urinib ko'ring.", keyboard);
                return;
            }

            String mimeType = document.getMimeType();
            String fileName = document.getFileName();

            if (mimeType == null || (!mimeType.startsWith("image/") && !isImageFile(fileName))) {
                String backCallback = templateKey == MessageKey.INFORMATION_MESSAGE
                        ? CALLBACK_SETTINGS_INFORMATION
                        : CALLBACK_SETTINGS_SET_FORWARD_MESSAGE;
                InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                        .addButton("🔙 Orqaga", backCallback)
                        .build();
                sendMessage(chatId, "❌ Iltimos, faqat rasm faylini yuboring (JPG, PNG, JPEG, etc.).", keyboard);
                return;
            }

            GetFile getFileMethod = new GetFile();
            getFileMethod.setFileId(document.getFileId());
            org.telegram.telegrambots.meta.api.objects.File file = botSupplier.get().execute(getFileMethod);

            java.io.File downloadedFile = ((TelegramLongPollingBot) botSupplier.get()).downloadFile(file);

            File processedFile = validateAndProcessImage(downloadedFile, chatId);
            if (processedFile == null) {
                downloadedFile.delete();
                return;
            }

            // If the document also has a caption, update the text as well
            String caption = message.getCaption();
            if (caption != null && !caption.trim().isEmpty()) {
                Long challengeId = getCurrentChallengeId(chatId);
                messageTemplateService.updateText(challengeId, templateKey, caption.trim());
                log.info("Updated template text from document caption for {}: {}", templateKey, caption.trim());
            }

            saveImage(chatId, message.getMessageId(), templateKey, processedFile);

        } catch (Exception e) {
            log.error("Error handling document upload", e);
            String backCallback = STATE_AWAITING_INFORMATION_IMAGE.equals(stateManager.getState(chatId))
                    ? CALLBACK_SETTINGS_INFORMATION
                    : CALLBACK_SETTINGS_SET_FORWARD_MESSAGE;
            InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                    .addButton("🔙 Orqaga", backCallback)
                    .build();
            sendMessage(chatId, "❌ Faylni yuklashda xatolik yuz berdi. Qaytadan urinib ko'ring.", keyboard);
        }
    }

    /**
     * Save image file to disk and update template
     */
    private void saveImage(Long chatId, Integer messageId, MessageKey templateKey, File processedFile)
            throws IOException {
        String prefix = (templateKey == MessageKey.INFORMATION_MESSAGE ? "info_" : "forward_");
        String fileName = prefix + System.currentTimeMillis() + ".jpg";
        String relativePath = IMAGES_RELATIVE_DIRECTORY + fileName;
        String fullPath = botUtils.getFullImagePath(relativePath);
        Path destinationPath = Paths.get(fullPath);

        // Ensure parent directory exists!
        if (destinationPath.getParent() != null) {
            Files.createDirectories(destinationPath.getParent());
        }

        Long challengeId = getCurrentChallengeId(chatId);
        messageTemplateService.getImagePath(challengeId, templateKey).ifPresent(oldPath -> {
            try {
                String fullOldPath = botUtils.getFullImagePath(oldPath);
                Files.deleteIfExists(Paths.get(fullOldPath));
                log.info("Deleted old image for {}: {}", templateKey, fullOldPath);
            } catch (IOException e) {
                log.error("Failed to delete old image file", e);
            }
        });

        // Safely copy across filesystem boundaries and remove temp file
        Files.copy(processedFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.deleteIfExists(processedFile.toPath());
        } catch (Exception ignored) {}

        messageTemplateService.updateImagePath(challengeId, templateKey, relativePath);

        botUtils.deleteMessage(botSupplier.get(), chatId, messageId);
        cleanupTempMessages(chatId);

        String successMessage;
        if (templateKey == MessageKey.INFORMATION_MESSAGE) {
            successMessage = "✅ *Information rasmi muvaffaqiyatli saqlandi!*";
        } else {
            successMessage = "✅ *Forward xabar rasmi muvaffaqiyatli saqlandi!*";
        }
        sendMessage(chatId, successMessage, navigationHandler.createBackButton());

        stateManager.clearState(chatId);
        stateManager.clearMenuMessageId(chatId);

        log.info("Image uploaded successfully: {} -> {}", relativePath, fullPath);
    }

    /**
     * Validate uploaded image
     */
    private File validateAndProcessImage(File sourceFile, Long chatId) {
        String currentState = stateManager.getState(chatId);
        String backCallback;
        if (STATE_AWAITING_INFORMATION_MESSAGE.equals(currentState)
                || STATE_AWAITING_INFORMATION_IMAGE.equals(currentState)) {
            backCallback = CALLBACK_SETTINGS_INFORMATION;
        } else if (STATE_AWAITING_FORWARD_MESSAGE.equals(currentState)
                || STATE_AWAITING_FORWARD_IMAGE.equals(currentState)) {
            backCallback = CALLBACK_SETTINGS_SET_FORWARD_MESSAGE;
        } else {
            backCallback = CALLBACK_MENU_SETTINGS;
        }

        try {
            long fileSize = sourceFile.length();
            if (fileSize > MAX_FILE_SIZE_BYTES) {
                InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                        .addButton("🔙 Orqaga", backCallback)
                        .build();
                sendMessage(chatId,
                        String.format(
                                "❌ *Fayl hajmi juda katta!*\n\nMaksimal hajm: *%d MB*\nSizning faylingiz: *%.2f MB*",
                                MAX_FILE_SIZE_MB, fileSize / (1024.0 * 1024.0)),
                        keyboard);
                return null;
            }

            BufferedImage originalImage = ImageIO.read(sourceFile);
            if (originalImage == null) {
                InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                        .addButton("🔙 Orqaga", backCallback)
                        .build();
                sendMessage(chatId,
                        "❌ *Rasm faylini o'qib bo'lmadi!*\n\nIltimos, to'g'ri rasm formatida fayl yuklang (JPG, PNG).",
                        keyboard);
                return null;
            }

            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();

            log.info("Image dimensions: {}x{}", originalWidth, originalHeight);
            log.info("Image validation passed");
            return sourceFile;

        } catch (IOException e) {
            log.error("Error processing image", e);
            InlineKeyboardMarkup keyboard = new KeyboardBuilder()
                    .addButton("🔙 Orqaga", backCallback)
                    .build();
            sendMessage(chatId, "❌ *Rasmni qayta ishlashda xatolik yuz berdi!*\n\nIltimos, boshqa rasm yuklang.",
                    keyboard);
            return null;
        }
    }

    /**
     * Check if filename has image extension
     */
    private boolean isImageFile(String fileName) {
        if (fileName == null)
            return false;
        String lowerFileName = fileName.toLowerCase();
        return lowerFileName.endsWith(".jpg") ||
                lowerFileName.endsWith(".jpeg") ||
                lowerFileName.endsWith(".png") ||
                lowerFileName.endsWith(".gif") ||
                lowerFileName.endsWith(".bmp") ||
                lowerFileName.endsWith(".webp");
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

    private void cleanupTempMessages(Long chatId) {
        List<Integer> messageIds = stateManager.getTempMessageIds(chatId);
        if (messageIds != null && !messageIds.isEmpty()) {
            for (Integer msgId : messageIds) {
                botUtils.deleteMessage(botSupplier.get(), chatId, msgId);
            }
            stateManager.clearTempMessages(chatId);
        }
    }

    private void sendMessage(Long chatId, String text) {
        sendMessage(chatId, text, null);
    }

    private void sendMessage(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        if (keyboard != null) {
            botUtils.sendMessageWithKeyboard(botSupplier.get(), chatId, text, keyboard);
        } else {
            botUtils.sendMessage(botSupplier.get(), chatId, text);
        }
    }
}
