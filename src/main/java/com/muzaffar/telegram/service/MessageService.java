package com.muzaffar.telegram.service;

import com.muzaffar.telegram.enums.MessageKey;
import com.muzaffar.telegram.util.KeyboardBuilder;

import static com.muzaffar.telegram.constants.BotConstants.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageTemplateService messageTemplateService;
    private final ChallengeService challengeService;
    private final com.muzaffar.telegram.util.TelegramBotUtils botUtils;

    /**
     * Create introduction message for first-time users
     */
    public SendMessage createIntroductionMessage(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(
                "*What can this bot do?*\n\n" +
                        "Maxsus guruhda Milliy Sertifikat testlarini ishlang va rash modelida tahlil qiling.\n" +
                        "Ega: @Muzaffar\\_Sakhadinov");
        message.setParseMode("Markdown");

        // Create START button using KeyboardBuilder
        InlineKeyboardMarkup keyboard = KeyboardBuilder.singleButton(BUTTON_START, CALLBACK_START_BOT);
        message.setReplyMarkup(keyboard);

        return message;
    }

    /**
     * Create greeting message (sent first)
     */
    public SendMessage createGreetingMessage(Long chatId, String firstName) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(String.format("🌟 Botga xush kelibsiz, %s!", firstName != null ? firstName : ""));
        return message;
    }

    /**
     * Create main information message with share button (sent after greeting) -
     * uses challenge-specific text from database
     */
    public SendMessage createMainInformationMessage(Long challengeId, Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());

        // Get information text from database for this challenge
        String text = messageTemplateService.getText(challengeId, MessageKey.INFORMATION_MESSAGE);

        message.setText(text);
        message.setParseMode("Markdown");

        // Create keyboard with "Davom etish" button using KeyboardBuilder
        InlineKeyboardMarkup keyboard = KeyboardBuilder.singleButton(BUTTON_CONTINUE, CALLBACK_MY_REFERRALS);
        message.setReplyMarkup(keyboard);

        return message;
    }

    /**
     * Create stats message with share button, showing active and left referrals
     */
    public SendMessage createStatsMessageWithButton(Long challengeId, Long chatId, String botUsername,
            String referralCode,
            int totalReferralCount, int indirectReferralCount,
            int activeCount, int leftCount, int requiredCount) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());

        String referralLink = String.format("https://t.me/%s?start=%s", botUsername, referralCode);
        int remaining = Math.max(0, requiredCount - activeCount);
        int totalInvites = totalReferralCount + indirectReferralCount;

        StringBuilder text = new StringBuilder();

        text.append(String.format("👥 *Jami takliflaringiz:* %d (To'g'ridan-to'g'ri: %d, Bilvosita: %d)\n\n",
                totalInvites, totalReferralCount, indirectReferralCount));
        text.append(String.format("✅ *Aktiv referallar:* %d\n", activeCount));

        if (leftCount > 0) {
            text.append(String.format("❌ *Kanaldan chiqib ketganlar:* %d\n", leftCount));
        }

        text.append(String.format("🔄 *Bilvosita takliflar:* %d ta\n", indirectReferralCount));
        text.append(String.format("🎯 *Mukofot uchun kerak:* %d\n\n", requiredCount));

        if (remaining > 0) {
            text.append(String.format("⏳ *Yana kerak:* %d ta aktiv referal", remaining));
        } else {
            text.append("🎉 *Tabriklaymiz!* Siz kerakli referallarga erishdingiz!");
        }

        message.setText(text.toString());
        message.setParseMode("Markdown");

        // Build share message
        StringBuilder shareMessageBuilder = new StringBuilder();
        shareMessageBuilder.append("Yopiq kanalga bepul kirish uchun shu botga kiring 👉\n");
        shareMessageBuilder.append(referralLink);

        String shareMessage = shareMessageBuilder.toString();

        String shareUrl = String.format(
                "https://t.me/share/url?url=%s&text=%s",
                java.net.URLEncoder.encode(referralLink, java.nio.charset.StandardCharsets.UTF_8),
                java.net.URLEncoder.encode(shareMessage, java.nio.charset.StandardCharsets.UTF_8));

        log.info("Share URL length: {}, URL: {}", shareUrl.length(), shareUrl);

        InlineKeyboardMarkup keyboard = KeyboardBuilder.singleUrlButton("📤 Do'stlarimga ulashish", shareUrl);
        message.setReplyMarkup(keyboard);

        return message;
    }

    /**
     * Create stats message (text only, for backward compatibility)
     */
    public String createStatsMessage(String botUsername, String referralCode, int referralCount, int requiredCount) {
        String referralLink = String.format("https://t.me/%s?start=%s", botUsername, referralCode);
        int remaining = Math.max(0, requiredCount - referralCount);

        StringBuilder message = new StringBuilder();
        message.append("📊 *Sizning statistikangiz*\n\n");
        message.append(String.format("✅ *Muvaffaqiyatli referallar:* %d\n", referralCount));
        message.append(String.format("🎯 *Mukofot uchun kerak:* %d\n\n", requiredCount));

        if (remaining > 0) {
            message.append(String.format("⏳ *Qolgan referallar:* %d\n\n", remaining));
        } else {
            message.append("🎉 *Tabriklaymiz!* Siz kerakli referallarga erishdingiz!\n\n");
        }

        message.append("🔗 *Sizning referal havolangiz:*\n");
        message.append(String.format("`%s`\n\n", referralLink));
        message.append("💡 Ko'proq do'stlarni taklif qilish uchun havolangizni ulashing!");

        return message.toString();
    }

    /**
     * Create referral success message with new user info and Takliflarim button
     */
    public SendMessage createReferralSuccessMessage(Long chatId, int newCount, int requiredCount, String newUserName,
            Long newUserId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());

        int remaining = requiredCount - newCount;

        String text = String.format(
                "🎊 *Ajoyib xabar!*\n\n" +
                        "Kimdir sizning referal havolangiz orqali qo'shildi!\n\n" +
                        "👤 %s (ID: %d) sizning taklif havolangiz orqali botga qo'shildi!\n\n" +
                        "👥 *Sizning referallaringiz:* %d/%d\n",
                newUserName != null ? newUserName : "Foydalanuvchi",
                newUserId,
                newCount, requiredCount);

        if (remaining > 0) {
            text += String.format("\n⏳ Shaxsiy kanalni ochish uchun yana *%d ta* kerak!", remaining);
        } else {
            text += "\n✨ Siz kerakli referallarga erishdingiz!";
        }

        message.setText(text);
        message.setParseMode("Markdown");

        // Add Takliflarim button using KeyboardBuilder
        InlineKeyboardMarkup keyboard = KeyboardBuilder.singleButton(BUTTON_MY_REFERRALS, CALLBACK_MY_REFERRALS);
        message.setReplyMarkup(keyboard);

        return message;
    }

    /**
     * Create private channel invite message
     */
    public String createPrivateChannelMessage(String inviteLink, int count) {
        // Escape underscores in the invite link to prevent Markdown parsing issues
        String escapedLink = inviteLink.replace("_", "\\_");
        return String.format(
                "🎉 *Tabriklaymiz!*\n\n" +
                        "Siz muvaffaqiyatli %s ta do'stingizni taklif qildingiz va bizning maxsus shaxsiy kanalimizga kirish huquqiga ega bo'ldingiz!\n\n"
                        +
                        "🔓 *Qo'shilish uchun quyidagi havolani bosing:*\n%s\n\n" +
                        "🌟 Hamjamiyatimizni o'stirishda yordam berish uchun do'stlaringizni taklif qilishda davom eting!",
                count,
                escapedLink);
    }

    /**
     * Create challenge completion message (for users who already received their
     * reward)
     */
    /**
     * Create challenge completion message (for users who already received their
     * reward)
     * Shows the full congratulations message with stats and the invite link
     */
    public SendMessage createChallengeCompletionMessage(Long chatId, int requiredCount, String inviteLink) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());

        // Escape underscores in the invite link to prevent Markdown parsing issues
        String escapedLink = inviteLink.replace("_", "\\_");

        StringBuilder text = new StringBuilder();
        text.append("🎉 *Tabriklaymiz!*\n\n");
        text.append(String.format(
                "Siz muvaffaqiyatli %d ta do'stingizni taklif qildingiz va bizning maxsus shaxsiy kanalimizga kirish huquqiga ega bo'ldingiz!\n\n",
                requiredCount));
        text.append("🔓 *Qo'shilish uchun quyidagi havolani bosing:*\n");
        text.append(escapedLink + "\n\n");
        text.append("🌟 Hamjamiyatimizni o'stirishda yordam berish uchun do'stlaringizni taklif qilishda davom eting!");

        message.setText(text.toString());
        message.setParseMode("Markdown");

        return message;
    }

    /**
     * Create referral info message with share button (sent after main information)
     */
    public SendMessage createReferralInfoMessage(Long chatId, String botUsername, String referralCode) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());

        String referralLink = String.format("https://t.me/%s?start=%s", botUsername, referralCode);

        String text = String.format(
                "🔗 *Sizning referal havolangiz:*\n`%s`\n\n" +
                        "Do'stlaringizga ulashing va bonuslar qo'lga kiriting!",
                referralLink);

        message.setText(text);
        message.setParseMode("Markdown");

        // Create share button
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton shareButton = new InlineKeyboardButton();
        shareButton.setText("📤 Do'stlarimga ulashish");

        String shareMessage = String.format(
                "Assalomu alaykum! Yopiq kanalga bepul kirish uchun shu botga kiring 👉\n%s",
                referralLink);

        String shareUrl = String.format(
                "https://t.me/share/url?url=%s&text=%s",
                java.net.URLEncoder.encode(referralLink, java.nio.charset.StandardCharsets.UTF_8),
                java.net.URLEncoder.encode(shareMessage, java.nio.charset.StandardCharsets.UTF_8));

        shareButton.setUrl(shareUrl);

        rows.add(List.of(shareButton));
        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        return message;
    }

    /**
     * Create admin help message
     */
    public String createAdminHelpMessage() {
        return "👨‍💼 *Admin buyruqlari*\n\n" +
                "/admin_broadcast <xabar> - Barcha foydalanuvchilarga xabar yuborish\n" +
                "/admin_stats - Bot statistikasini ko'rish";
    }

    /**
     * Create admin stats message
     */
    public String createAdminStatsMessage(long totalUsers) {
        return String.format(
                "📈 *Bot statistikasi*\n\n" +
                        "👥 *Jami foydalanuvchilar:* %d\n",
                totalUsers);
    }

    /**
     * Create channel subscription request message with buttons
     */
    public SendMessage createChannelSubscriptionMessage(Long chatId,
            List<com.muzaffar.telegram.entity.ChallengeChannel> channels) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());

        StringBuilder text = new StringBuilder();
        text.append("Bizning loyihamizda ishtirok etish uchun bizning sahifalarimizga a'zo bo'ling.\n\n");
        text.append("*Keyin \"✅ A'zo bo'ldim\" tugmasini bosing!*\n\n");

        message.setText(text.toString());
        message.setParseMode("Markdown");
        message.setDisableWebPagePreview(true); // Disable preview to prevent channel cards

        // Create inline keyboard with channel buttons and confirmation button
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Add channel buttons
        for (com.muzaffar.telegram.entity.ChallengeChannel channel : channels) {
            InlineKeyboardButton channelButton = new InlineKeyboardButton();
            channelButton.setText(channel.getChannelName());
            String rawUsername = channel.getChannelUsername() != null ? channel.getChannelUsername().trim() : "";
            if (rawUsername.startsWith("http://") || rawUsername.startsWith("https://")) {
                channelButton.setUrl(rawUsername);
            } else {
                channelButton.setUrl("https://t.me/" + rawUsername.replace("@", ""));
            }

            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(channelButton);
            rows.add(row);
        }


        // Add confirmation button
        InlineKeyboardButton confirmButton = new InlineKeyboardButton();
        confirmButton.setText(BUTTON_I_JOINED);
        confirmButton.setCallbackData(CALLBACK_VERIFY_CHANNELS);

        List<InlineKeyboardButton> confirmRow = new ArrayList<>();
        confirmRow.add(confirmButton);
        rows.add(confirmRow);

        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        return message;
    }

    /**
     * Create SendMessage with Markdown formatting
     */
    public SendMessage createMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("Markdown");
        return message;
    }

    /**
     * Create message when user doesn't have enough active referrals with
     * Takliflarim button
     */
    public SendMessage createInsufficientActiveReferralsMessage(Long chatId, int totalReferralCount,
            int indirectReferralCount,
            int activeCount, int leftCount, int requiredCount) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());

        int remaining = requiredCount - activeCount;

        StringBuilder text = new StringBuilder();
        text.append("⚠️ *Shaxsiy kanal uchun yetarli emas!*\n\n");
        text.append(String.format("👥 *Jami takliflaringiz:* %d (To'g'ridan-to'g'ri: %d, Bilvosita: %d)\n\n",
                totalReferralCount + indirectReferralCount, totalReferralCount, indirectReferralCount));
        text.append(String.format("✅ *Aktiv referallar:* %d\n", activeCount));
        text.append(String.format("❌ *Kanaldan chiqib ketganlar:* %d\n", leftCount));
        text.append(String.format("🎯 *Kerak:* %d\n\n", requiredCount));
        text.append(String.format("⏳ *Yana kerak:* %d ta aktiv referal\n\n", remaining));
        text.append("💡 Kanaldan chiqib ketgan do'stlaringizni qaytadan taklif qiling yoki yanglarini taklif qiling!");

        message.setText(text.toString());
        message.setParseMode("Markdown");

        // Add Takliflarim button
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton referralsButton = new InlineKeyboardButton();
        referralsButton.setText(BUTTON_MY_REFERRALS);
        referralsButton.setCallbackData(CALLBACK_MY_REFERRALS);

        rows.add(List.of(referralsButton));
        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        return message;
    }

    /**
     * Send main information message with optional image (convenience method)
     * 
     * @param bot         The bot instance to use for sending
     * @param challengeId The challenge ID to get templates for
     * @param chatId      The chat ID to send to
     */
    public void sendInformationMessageWithImage(org.telegram.telegrambots.bots.TelegramLongPollingBot bot,
            Long challengeId, Long chatId) {
        try {
            String informationText = messageTemplateService.getText(challengeId, MessageKey.INFORMATION_MESSAGE);
            if (informationText == null || informationText.trim().isEmpty()) {
                log.warn("Information message text is empty for challenge={}. Skipping message to chatId={}",
                        challengeId, chatId);
                return;
            }
            var imagePathOptional = messageTemplateService.getImagePath(challengeId, MessageKey.INFORMATION_MESSAGE);

            // Create keyboard with "Davom etish" button
            InlineKeyboardMarkup keyboard = KeyboardBuilder.singleButton(BUTTON_CONTINUE, CALLBACK_MY_REFERRALS);

            // Check if there's an image
            if (imagePathOptional.isPresent()) {
                String imagePath = imagePathOptional.get();
                botUtils.sendPhotoWithCaptionAndKeyboard(bot, chatId, imagePath, informationText, keyboard);
            } else {
                // No image, send as text message
                botUtils.sendMessageWithKeyboard(bot, chatId, informationText, keyboard);
            }
        } catch (Exception e) {
            log.error("Error sending information message with image to chatId={} for challenge={}",
                    chatId, challengeId, e);
        }
    }
}
