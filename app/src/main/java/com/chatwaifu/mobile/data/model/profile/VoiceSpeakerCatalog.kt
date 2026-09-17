package com.chatwaifu.mobile.data.model.profile

import com.chatwaifu.vits.utils.SoundGenerateHelper
import com.chatwaifu.vits.utils.file.ConfigParseResult
import com.chatwaifu.vits.utils.file.FileUtils
import java.io.File

/**
 * Description: 从声学模型目录的 config.json 读 `spk2id`。
 *
 * 和 `Bv2ModelInstaller.availableSpeakers` 的区别：那个只认共享目录、只服务
 * 「给内置角色分配 speaker」这一件事。语音设置页要同时支持导入角色**自带的**
 * `vits/config.json`，而 BV2 的两种目录布局在这一点上是同构的（都有 config.json），
 * 所以这里按任意目录取，两条路共用一份解析。
 *
 * 读不到就返回空列表：UI 要退化成「只读显示当前 speaker」而不是崩。
 *
 * Author: Voine
 * Date: 2026/9/17
 */
internal object VoiceSpeakerCatalog {

    /** 按 speaker id 升序返回 `名字 → id`。顺序稳定，UI 列表才不会每次抖动。 */
    fun speakers(vitsDir: String?): List<VoiceSpeaker> {
        val dir = vitsDir ?: return emptyList()
        val config = File(dir, SoundGenerateHelper.CONFIG_JSON)
        if (!config.isFile) return emptyList()
        val spk2id = (FileUtils.parseConfig(config.absolutePath) as? ConfigParseResult.Success)
            ?.config?.data?.spk2id
            ?: return emptyList()
        return spk2id.toList()
            .sortedBy { it.second }
            .map { (name, id) -> VoiceSpeaker(name = name, id = id) }
    }
}

data class VoiceSpeaker(val name: String, val id: Int)
