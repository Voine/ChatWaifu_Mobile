package com.chatwaifu.mobile.data


/**
 * Description: Constant
 * Author: Voine
 * Date: 2023/2/25
 */
object Constant {
    const val SAVED_CHAT_KEY = "saved_chat_key"
    const val SAVED_TRANSLATE_APP_ID = "saved_translate_id"
    const val SAVED_TRANSLATE_KEY = "saved_translate_key"
    const val SAVED_STORE = "saved_store"
    const val SAVED_CHAT_NAME = "saved_chat_name"
    const val SAVED_YUUKA_SETTING = "saved_yuuka_setting"
    const val SAVED_AMADEUS_SETTING = "saved_amadeus_setting"
    const val SAVED_ATRI_SETTING = "saved_atri_setting"
    const val SAVED_USE_TRANSLATE = "saved_use_translate"
    const val SAVED_USE_DARKMODE = "saved_use_darkmode"
    const val SAVED_USE_CHATGPT_PROXY = "saved_use_chatgpt_proxy"
    const val SAVED_USE_CHATGPT_PROXY_URL = "saved_use_chatgpt_proxy_url"

    /**
     * 当前选中的聊天基座，存 [com.chatwaifu.chat.core.ProviderId.key]。
     */
    const val SAVED_ACTIVE_CHAT_PROVIDER = "saved_active_chat_provider"

    /**
     * 每个基座的配置按 provider 分开存：`saved_provider_<id>_key` / `_base_url` / `_model`。
     * 以前只有一个 [SAVED_CHAT_KEY]，换基座就要重填。
     */
    const val SAVED_PROVIDER_PREFIX = "saved_provider_"
    const val SAVED_PROVIDER_SUFFIX_KEY = "_key"
    const val SAVED_PROVIDER_SUFFIX_BASE_URL = "_base_url"
    const val SAVED_PROVIDER_SUFFIX_MODEL = "_model"

    /**
     * 老配置（[SAVED_CHAT_KEY] + [SAVED_USE_CHATGPT_PROXY_URL]）是否已经搬到新结构。
     * 只搬一次，搬完老 key 保留不删 —— 万一要回滚版本还能用。
     */
    const val SAVED_PROVIDER_MIGRATED = "saved_provider_migrated"

    /**
     * 导入模型的人物设定按角色名分开存，key 是 [SAVED_SYSTEM_PROMPT_PREFIX] + 角色名。
     * 以前所有外部模型共用一个 saved_external_setting，多个模型只能有一份设定。
     */
    const val SAVED_SYSTEM_PROMPT_PREFIX = "saved_system_prompt_"

    /** 已解出的内置模型版本，和 [BUILT_IN_MODEL_VERSION] 比对决定要不要重解 */
    const val SAVED_BUILT_IN_MODEL_VERSION = "saved_built_in_model_version"

    /**
     * 内置模型资源版本，**改动 assets 里的内置模型时手动 +1**。
     *
     * 不用 BuildConfig.VERSION_CODE：app/build.gradle 的 defaultConfig 从来没声明
     * versionCode，AGP 会兜底成 1 且永不变化，拿它当 gate 会永久失效。
     */
    const val BUILT_IN_MODEL_VERSION = 1

    const val LIVE2D_BASE_PATH = "Live2DModels"
    const val VITS_BASE_PATH = "VITSModels"

    const val LOCAL_MODEL_YUUKA = "Yuuka"
    const val LOCAL_MODEL_AMADEUS = "Amadeus"
    const val LOCAL_MODEL_ATRI = "ATRI"

    const val LOCAL_MODEL_TRANSLATE_X_PREFIX = "local_model_translate_x_"
    const val LOCAL_MODEL_TRANSLATE_Y_PREFIX = "local_model_translate_y_"
    const val LOCAL_MODEL_TRANSLATE_SCALE_PREFIX = "local_model_translate_scale_"
}
