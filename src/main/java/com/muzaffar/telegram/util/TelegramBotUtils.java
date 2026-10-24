package com.muzaffar.telegram.util;

import com.muzaffar.telegram.config.AppConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.nio.file.Paths;

/**
 * Utility service for common Telegram bot operations.
 * Eliminates code duplication across different bot classes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramBotUtils {
    
    private final AppConfig appConfig;
    
    /**
     * Get full absolute path for an image file.
     * 
     * @param relativePath The relative path to the image
     * @return Full absolute path to the image file
     */
    public String getFullImagePath(String relativePath) {
        String basePath = appConfig.getImages().getBasePath();
        if (basePath == null || basePath.isEmpty()) {
            basePath = System.getProperty("user.dir");
        }
        return Paths.get(basePath, relativePath).toString();
    }
    
    /**
     * Check if a user is an admin.
     * 
     * @param chatId The chat ID to check
     * @return true if the user is an admin, false otherwise
     */
    public boolean isAdmin(Long chatId) {
        return appConfig.getAdmin().getChatIds().contains(chatId);
    }
    
    /**
     * Send a simple text message.
     * 
     * @param bot The bot instance to use
     * @param chatId The chat ID to send to
     * @param text The message text
     */
    public void sendMessage(TelegramLongPollingBot bot, Long chatId, String text) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text);
            message.setParseMode("Markdown");
            bot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send message to chatId={}", chatId, e);
        }
    }
    
    /**
     * Send a text message with inline keyboard.
     * 
     * @param bot The bot instance to use
     * @param chatId The chat ID to send to
     * @param text The message text
     * @param keyboard The inline keyboard markup
     */
    public Message sendMessageWithKeyboard(TelegramLongPollingBot bot, Long chatId, String text, InlineKeyboardMarkup keyboard) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text);
            message.setParseMode("Markdown");
            message.setReplyMarkup(keyboard);
            return bot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send message with keyboard to chatId={}", chatId, e);
            return null;
        }
    }
    
    /**
     * Edit an existing message.
     * 
     * @param bot The bot instance to use
     * @param chatId The chat ID
     * @param messageId The message ID to edit
     * @param newText The new text
     */
    public void editMessage(TelegramLongPollingBot bot, Long chatId, Integer messageId, String newText) {
        try {
            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(chatId.toString());
            editMessage.setMessageId(messageId);
            editMessage.setText(newText);
            editMessage.setParseMode("Markdown");
            bot.execute(editMessage);
        } catch (TelegramApiException e) {
            log.error("Failed to edit message chatId={}, messageId={}", chatId, messageId, e);
        }
    }

    /**
     * Edit an existing message text and keyboard.
     * 
     * @param bot The bot instance to use
     * @param chatId The chat ID
     * @param messageId The message ID to edit
     * @param newText The new text
     * @param keyboard The new inline keyboard
     */
    public void editMessageWithKeyboard(TelegramLongPollingBot bot, Long chatId, Integer messageId, String newText, InlineKeyboardMarkup keyboard) {
        try {
            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(chatId.toString());
            editMessage.setMessageId(messageId);
            editMessage.setText(newText);
            editMessage.setParseMode("Markdown");
            editMessage.setReplyMarkup(keyboard);
            bot.execute(editMessage);
        } catch (TelegramApiException e) {
            log.error("Failed to edit message with keyboard chatId={}, messageId={}", chatId, messageId, e);
        }
    }

    /**
     * Answer callback query to remove spinner and optionally show toast/alert.
     * 
     * @param bot The bot instance to use
     * @param callbackQueryId The callback query ID
     * @param text Optional text to display
     * @param showAlert true for modal alert popup, false for notification toast
     */
    public void answerCallbackQuery(TelegramLongPollingBot bot, String callbackQueryId, String text, boolean showAlert) {
        if (callbackQueryId == null || callbackQueryId.isEmpty()) {
            return;
        }
        try {
            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackQueryId);
            if (text != null && !text.isEmpty()) {
                answer.setText(text);
            }
            answer.setShowAlert(showAlert);
            bot.execute(answer);
        } catch (TelegramApiException e) {
            log.warn("Failed to answer callback query id={}: {}", callbackQueryId, e.getMessage());
        }
    }
    
    /**
     * Delete a message.
     * 
     * @param bot The bot instance to use
     * @param chatId The chat ID
     * @param messageId The message ID to delete
     */
    public void deleteMessage(TelegramLongPollingBot bot, Long chatId, Integer messageId) {
        try {
            DeleteMessage deleteMessage = new DeleteMessage();
            deleteMessage.setChatId(chatId.toString());
            deleteMessage.setMessageId(messageId);
            bot.execute(deleteMessage);
        } catch (TelegramApiException e) {
            log.error("Failed to delete message chatId={}, messageId={}", chatId, messageId, e);
        }
    }
    
    /**
     * Send a photo from file path.
     * 
     * @param bot The bot instance to use
     * @param chatId The chat ID to send to
     * @param relativePath The relative path to the image file
     */
    public Message sendPhotoFromFile(TelegramLongPollingBot bot, Long chatId, String relativePath) {
        try {
            String fullPath = getFullImagePath(relativePath);
            File imageFile = new File(fullPath);
            
            if (!imageFile.exists()) {
                log.warn("Image file not found: {}", fullPath);
                sendMessage(bot, chatId, "❌ Image not found: " + relativePath);
                return null;
            }
            
            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(chatId.toString());
            sendPhoto.setPhoto(new InputFile(imageFile));
            return bot.execute(sendPhoto);
        } catch (TelegramApiException e) {
            log.error("Failed to send photo from file to chatId={}, file={}", chatId, relativePath, e);
            return null;
        }
    }
    
    /**
     * Send a photo with caption.
     * 
     * @param bot The bot instance to use
     * @param chatId The chat ID to send to
     * @param relativePath The relative path to the image file
     * @param caption The caption text
     */
    public Message sendPhotoWithCaption(TelegramLongPollingBot bot, Long chatId, String relativePath, String caption) {
        try {
            String fullPath = getFullImagePath(relativePath);
            File imageFile = new File(fullPath);
            
            if (!imageFile.exists()) {
                log.warn("Image file not found: {}", fullPath);
                sendMessage(bot, chatId, caption + "\n\n❌ Image not found: " + relativePath);
                return null;
            }
            
            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(chatId.toString());
            sendPhoto.setPhoto(new InputFile(imageFile));
            sendPhoto.setCaption(caption);
            sendPhoto.setParseMode("Markdown");
            return bot.execute(sendPhoto);
        } catch (TelegramApiException e) {
            log.error("Failed to send photo with caption to chatId={}, file={}", chatId, relativePath, e);
            return null;
        }
    }
    
    /**
     * Send a photo with caption and keyboard.
     * 
     * @param bot The bot instance to use
     * @param chatId The chat ID to send to
     * @param relativePath The relative path to the image file
     * @param caption The caption text
     * @param keyboard The inline keyboard markup
     */
    public void sendPhotoWithCaptionAndKeyboard(TelegramLongPollingBot bot, Long chatId, String relativePath, 
                                                  String caption, InlineKeyboardMarkup keyboard) {
        try {
            String fullPath = getFullImagePath(relativePath);
            File imageFile = new File(fullPath);
            
            if (!imageFile.exists()) {
                log.warn("Image file not found: {}", fullPath);
                sendMessageWithKeyboard(bot, chatId, caption, keyboard);
                return;
            }
            
            SendPhoto photo = new SendPhoto();
            photo.setChatId(chatId.toString());
            photo.setPhoto(new InputFile(imageFile));
            photo.setCaption(caption);
            photo.setParseMode("Markdown");
            photo.setReplyMarkup(keyboard);
            bot.execute(photo);
        } catch (TelegramApiException e) {
            log.error("Failed to send photo with caption and keyboard to chatId={}", chatId, e);
        }
    }
}
