package com.muzaffar.telegram.constants;

/**
 * Constants used throughout the Telegram bot application.
 * Centralizes magic numbers and strings for better maintainability.
 */
public final class BotConstants {

    private BotConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }

    // ==================== Timing Constants ====================

    /**
     * Delay between broadcast messages to avoid Telegram rate limits (milliseconds)
     */
    public static final int BROADCAST_DELAY_MS = 50;

    /**
     * Delay between information sequence messages for better UX (milliseconds)
     */
    public static final int INFORMATION_SEQUENCE_DELAY_MS = 500;

    /**
     * Delay before retrying failed operations (milliseconds)
     */
    public static final int RETRY_DELAY_MS = 1000;

    // ==================== Command Constants ====================

    /**
     * Start command text
     */
    public static final String START_COMMAND = "/start";

    /**
     * Length of "/start " including space
     */
    public static final int START_COMMAND_PREFIX_LENGTH = 7;

    /**
     * Admin broadcast command text
     */
    public static final String ADMIN_BROADCAST_COMMAND = "/admin_broadcast";

    /**
     * Length of "/admin_broadcast " including space
     */
    public static final int ADMIN_BROADCAST_PREFIX_LENGTH = 16;

    /**
     * Stats command text
     */
    public static final String STATS_COMMAND = "/stats";

    /**
     * Menu command text (admin)
     */
    public static final String MENU_COMMAND = "/menu";

    /**
     * Admin stats command text
     */
    public static final String ADMIN_STATS_COMMAND = "/admin_stats";

    // ==================== Image Upload Constants ====================

    /**
     * Maximum file size for image uploads in megabytes
     */
    public static final int MAX_FILE_SIZE_MB = 5;

    /**
     * Maximum file size for image uploads in bytes
     */
    public static final long MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024L * 1024L;

    /**
     * Relative directory for storing bot images
     */
    public static final String IMAGES_RELATIVE_DIRECTORY = "bot_images/stats_headers/";

    // ==================== Button Text Constants ====================

    /**
     * "My Referrals" button text in Uzbek
     */
    public static final String BUTTON_MY_REFERRALS = "📊 Takliflarim";

    /**
     * "Continue" button text in Uzbek
     */
    public static final String BUTTON_CONTINUE = "▶️ Qatnashishni xohlayman";

    /**
     * "Share" button text in Uzbek
     */
    public static final String BUTTON_SHARE = "📤 Ulashish";

    /**
     * "Check Subscription" button text in Uzbek
     */
    public static final String BUTTON_CHECK_SUBSCRIPTION = "✅ Obunani tekshirish";

    /**
     * "START" button text
     */
    public static final String BUTTON_START = "START";

    /**
     * "I joined" button text in Uzbek
     */
    public static final String BUTTON_I_JOINED = "✅ A'zo bo'ldim";

    /**
     * "Menu" button text in Uzbek (Admin)
     */
    public static final String BUTTON_MENU = "📋 Menyu";

    /**
     * "Cancel" button text in Uzbek (Admin)
     */
    public static final String BUTTON_CANCEL = "❌ Bekor qilish";


    // ==================== Callback Data Constants ====================

    // User Bot Callbacks
    public static final String CALLBACK_START_BOT = "start_bot";
    public static final String CALLBACK_VERIFY_CHANNELS = "verify_channels";
    public static final String CALLBACK_MY_REFERRALS = "my_referrals";
    public static final String CALLBACK_GET_PRIVATE_CHANNEL = "get_private_channel";
    public static final String CALLBACK_CHECK_SUBSCRIPTION = "check_subscription";

    // Admin Bot Callbacks - Menu Navigation
    public static final String CALLBACK_MENU_MAIN = "menu_main";
    public static final String CALLBACK_MAIN_MENU = "main_menu";
    public static final String CALLBACK_BACK = "back"; // Back button to previous menu
    public static final String CALLBACK_MENU_CHANNELS = "menu_channels";
    public static final String CALLBACK_MENU_SETTINGS = "menu_settings";
    public static final String CALLBACK_MENU_BROADCAST = "menu_broadcast";
    public static final String CALLBACK_CANCEL_OPERATION = "cancel_operation";

    // Admin Bot Callbacks - Channel Management
    public static final String CALLBACK_CHANNEL_ADD = "channel_add";
    public static final String CALLBACK_CHANNEL_LIST = "channel_list";
    public static final String CALLBACK_CHANNEL_REMOVE = "channel_remove";

    // Admin Bot Callbacks - Settings
    public static final String CALLBACK_SETTINGS_SET_REFERRALS = "settings_set_referrals";
    public static final String CALLBACK_SETTINGS_SET_PRIVATE_LINK = "settings_set_private_link";
    public static final String CALLBACK_SETTINGS_INFORMATION = "settings_information";
    public static final String CALLBACK_SETTINGS_SET_FORWARD_MESSAGE = "settings_set_forward_message";
    public static final String CALLBACK_SETTINGS_SET_VIDEO_GUIDE = "settings_set_video_guide";
    public static final String CALLBACK_SETTINGS_DELETE_VIDEO_GUIDE = "settings_delete_video_guide";

    // Admin Bot Callbacks - Information Message
    public static final String CALLBACK_EDIT_INFORMATION_TEXT = "edit_information_text";
    public static final String CALLBACK_EDIT_INFORMATION_IMAGE = "edit_information_image";
    public static final String CALLBACK_DELETE_INFORMATION_IMAGE = "delete_information_image";

    // Admin Bot Callbacks - Forward Message
    public static final String CALLBACK_EDIT_FORWARD_TEXT = "edit_forward_text";
    public static final String CALLBACK_EDIT_FORWARD_IMAGE = "edit_forward_image";
    public static final String CALLBACK_DELETE_FORWARD_IMAGE = "delete_forward_image";

    // Admin Bot Callbacks - Broadcast
    public static final String CALLBACK_BROADCAST_INFORMATION_ALL = "broadcast_information_all";
    public static final String CALLBACK_BROADCAST_INFORMATION_ALL_CONFIRM = "broadcast_information_all_confirm";
    public static final String CALLBACK_BROADCAST_INFORMATION_SINGLE = "broadcast_information_single";
    public static final String CALLBACK_BROADCAST_CUSTOM_ALL = "broadcast_custom_all";
    public static final String CALLBACK_BROADCAST_CUSTOM_ALL_CONFIRM = "broadcast_custom_all_confirm";

    // Admin Bot Callbacks - Stats
    public static final String CALLBACK_ADMIN_STATS = "admin_stats_menu";

    // Admin Bot Callbacks - Challenge Management
    public static final String CALLBACK_CHALLENGE_MANAGEMENT = "challenge_management";
    public static final String CALLBACK_CHALLENGE_CREATE = "challenge_create";
    public static final String CALLBACK_CHALLENGE_LIST = "challenge_list";
    public static final String CALLBACK_CHALLENGE_SELECT_PREFIX = "challenge_select_";
    public static final String CALLBACK_CHALLENGE_SET_MAIN = "challenge_set_main";
    public static final String CALLBACK_CHALLENGE_TOGGLE_STATUS = "challenge_toggle_status";
    public static final String CALLBACK_CHALLENGE_DELETE = "challenge_delete";
    public static final String CALLBACK_CHALLENGE_DELETE_CONFIRM = "challenge_delete_confirm";

    // ==================== Admin State Constants ====================

    public static final String STATE_AWAITING_CHANNEL_ID = "AWAITING_CHANNEL_ID";
    public static final String STATE_AWAITING_CHANNEL_NAME = "AWAITING_CHANNEL_NAME";
    public static final String STATE_AWAITING_CHANNEL_USERNAME = "AWAITING_CHANNEL_USERNAME";
    public static final String STATE_AWAITING_REFERRAL_COUNT = "AWAITING_REFERRAL_COUNT";
    public static final String STATE_AWAITING_PRIVATE_LINK = "AWAITING_PRIVATE_LINK";
    public static final String STATE_AWAITING_INFORMATION_MESSAGE = "AWAITING_INFORMATION_MESSAGE";
    public static final String STATE_AWAITING_INFORMATION_IMAGE = "AWAITING_INFORMATION_IMAGE";
    public static final String STATE_AWAITING_FORWARD_MESSAGE = "AWAITING_FORWARD_MESSAGE";
    public static final String STATE_AWAITING_FORWARD_IMAGE = "AWAITING_FORWARD_IMAGE";
    public static final String STATE_AWAITING_BROADCAST_MESSAGE = "AWAITING_BROADCAST_MESSAGE";
    public static final String STATE_AWAITING_VIDEO_GUIDE_URL = "AWAITING_VIDEO_GUIDE_URL";
    public static final String STATE_AWAITING_CUSTOM_BROADCAST_MESSAGE = "AWAITING_CUSTOM_BROADCAST_MESSAGE";

    // Challenge Creation States
    public static final String STATE_AWAITING_CHALLENGE_NAME = "AWAITING_CHALLENGE_NAME";
    public static final String STATE_AWAITING_CHALLENGE_REFERRAL_COUNT = "AWAITING_CHALLENGE_REFERRAL_COUNT";
    public static final String STATE_AWAITING_CHALLENGE_PRIVATE_LINK = "AWAITING_CHALLENGE_PRIVATE_LINK";
}
