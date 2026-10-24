package com.muzaffar.telegram.service;

import com.muzaffar.telegram.entity.Challenge;
import com.muzaffar.telegram.entity.MessageTemplate;
import com.muzaffar.telegram.enums.MessageKey;
import com.muzaffar.telegram.repository.ChallengeRepository;
import com.muzaffar.telegram.repository.MessageTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing challenge-specific message templates
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageTemplateService {

    private final MessageTemplateRepository repository;
    private final ChallengeRepository challengeRepository;

    private Challenge resolveChallenge(Long challengeId) {
        if (challengeId != null) {
            Optional<Challenge> opt = challengeRepository.findById(challengeId);
            if (opt.isPresent()) {
                return opt.get();
            }
        }
        return challengeRepository.findMainChallenge()
                .orElseGet(() -> challengeRepository.findAll().stream().findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Challenge topilmadi: " + challengeId)));
    }

    /**
     * Get message template by challenge and key
     */
    public Optional<MessageTemplate> getTemplate(Long challengeId, MessageKey messageKey) {
        Challenge challenge = resolveChallenge(challengeId);
        return repository.findByChallengeAndMessageKey(challenge, messageKey);
    }

    /**
     * Get message text by challenge and key
     */
    public String getText(Long challengeId, MessageKey messageKey) {
        return getTemplate(challengeId, messageKey)
                .map(MessageTemplate::getText)
                .orElse("");
    }

    /**
     * Get image path by challenge and key
     */
    public Optional<String> getImagePath(Long challengeId, MessageKey messageKey) {
        return getTemplate(challengeId, messageKey)
                .map(MessageTemplate::getImagePath)
                .filter(path -> path != null && !path.isEmpty());
    }

    /**
     * Get all templates for a challenge
     */
    public List<MessageTemplate> getTemplatesForChallenge(Long challengeId) {
        Challenge challenge = resolveChallenge(challengeId);
        return repository.findByChallenge(challenge);
    }

    /**
     * Update message text for a challenge
     */
    @Transactional
    public void updateText(Long challengeId, MessageKey messageKey, String text) {
        Challenge challenge = resolveChallenge(challengeId);

        MessageTemplate template = repository.findByChallengeAndMessageKey(challenge, messageKey)
                .orElseGet(() -> {
                    MessageTemplate newTemplate = new MessageTemplate();
                    newTemplate.setChallenge(challenge);
                    newTemplate.setMessageKey(messageKey);
                    return newTemplate;
                });

        template.setText(text);
        repository.save(template);
        log.info("Updated text for challenge {} - {}: {}", challenge.getId(), messageKey,
                text.substring(0, Math.min(50, text.length())));
    }

    /**
     * Update image path for a challenge
     */
    @Transactional
    public void updateImagePath(Long challengeId, MessageKey messageKey, String imagePath) {
        Challenge challenge = resolveChallenge(challengeId);

        MessageTemplate template = repository.findByChallengeAndMessageKey(challenge, messageKey)
                .orElseGet(() -> {
                    MessageTemplate newTemplate = new MessageTemplate();
                    newTemplate.setChallenge(challenge);
                    newTemplate.setMessageKey(messageKey);
                    return newTemplate;
                });

        template.setImagePath(imagePath);
        repository.save(template);
        log.info("Updated image path for challenge {} - {}: {}", challenge.getId(), messageKey, imagePath);
    }

    /**
     * Update both text and image path for a challenge
     */
    @Transactional
    public void updateTemplate(Long challengeId, MessageKey messageKey, String text, String imagePath) {
        Challenge challenge = resolveChallenge(challengeId);

        MessageTemplate template = repository.findByChallengeAndMessageKey(challenge, messageKey)
                .orElseGet(() -> {
                    MessageTemplate newTemplate = new MessageTemplate();
                    newTemplate.setChallenge(challenge);
                    newTemplate.setMessageKey(messageKey);
                    return newTemplate;
                });

        if (text != null) {
            template.setText(text);
        }
        if (imagePath != null) {
            template.setImagePath(imagePath);
        }

        repository.save(template);
        log.info("Updated template for challenge {} - {}", challenge.getId(), messageKey);
    }

    /**
     * Initialize default templates for a challenge if they don't exist
     */
    @Transactional
    public void initializeDefaultTemplatesForChallenge(Long challengeId) {
        Challenge challenge = resolveChallenge(challengeId);

        // Initialize INFORMATION_MESSAGE
        if (!repository.existsByChallengeAndMessageKey(challenge, MessageKey.INFORMATION_MESSAGE)) {
            MessageTemplate informationTemplate = new MessageTemplate();
            informationTemplate.setChallenge(challenge);
            informationTemplate.setMessageKey(MessageKey.INFORMATION_MESSAGE);
            informationTemplate.setText(
                    "🎉 Assalomu alaykum!\n\n" +
                            "Matematikadan Milliy sertifikat imtihoniga bepul tayyorlanish imkoniyati!\n" +
                            "Va albatta siz uchun *1 ta bonus kitob* tayyorlab qo'ydim 📚✨\n\n" +
                            "🌟 Har bir yangi do'st taklif qilsangiz — *bonus ball* beriladi!\n" +
                            "🚀 Har bir taklif sizni sertifikatga yaqinlashtiradi!\n\n" +
                            "💥 Do'stingiz botga kirib kanalga a'zo bo'lsa — *+1 ball avto qo'shiladi!*\n\n" +
                            "🎯 " + challenge.getRequiredReferralCount() + " ta matematikachi do'st taklif qilsangiz:\n"
                            +
                            "🔥 *Shaxsiy kanalga bepul umrbod kirish!*\n\n" +
                            "📘 Bonus mukofotlar faqat faol ishtirokchilarga!\n\n" +
                            "✨ Imkoniyatni qo'ldan boy bermang!\n\n" +
                            "❗ *Muhim:*\n" +
                            "Ball olish uchun do'stingiz botga kirib, majburiy kanallarga a'zo bo'lishi kerak\n\n" +
                            "💡 Do'stlaringizni hozir taklif qiling:\n" +
                            "(Havolani olish uchun Takliflarim tugmasini bosing...)\n\n" +
                            "📘 Asosiy menyuga xush kelibsiz!");
            repository.save(informationTemplate);
            log.info("Initialized default INFORMATION_MESSAGE template for challenge {}", challengeId);
        }

        // Initialize FORWARD_MESSAGE
        if (!repository.existsByChallengeAndMessageKey(challenge, MessageKey.FORWARD_MESSAGE)) {
            MessageTemplate forwardTemplate = new MessageTemplate();
            forwardTemplate.setChallenge(challenge);
            forwardTemplate.setMessageKey(MessageKey.FORWARD_MESSAGE);
            forwardTemplate.setText(
                    "🔗 *Sizning referal havolangiz:*\n" +
                            "💡 Ko'proq do'stlarni taklif qilish uchun havolangizni ulashing!");
            repository.save(forwardTemplate);
            log.info("Initialized default FORWARD_MESSAGE template for challenge {}", challengeId);
        }
    }

    /**
     * Initialize default templates for all existing challenges
     */
    @Transactional
    public void initializeDefaultTemplatesForAllChallenges() {
        List<Challenge> allChallenges = challengeRepository.findAll();
        for (Challenge challenge : allChallenges) {
            initializeDefaultTemplatesForChallenge(challenge.getId());
        }
        log.info("Initialized default templates for {} challenges", allChallenges.size());
    }
}
