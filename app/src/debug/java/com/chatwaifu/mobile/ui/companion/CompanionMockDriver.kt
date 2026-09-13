package com.chatwaifu.mobile.ui.companion

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class CompanionMockDriver : CompanionResponseDriver {
    override fun responses(request: CompanionRequest): Flow<CompanionResponseEvent> = flow {
        delay(700)
        if (request.input == "/error") {
            emit(CompanionResponseEvent.Failed("这是隔离 Mock 的可恢复错误，草稿已保留"))
            return@flow
        }
        emit(
            CompanionResponseEvent.Reply(
                "我听见了：“${request.input}”。这是本地演示回复，不会访问网络或写入聊天记录。"
            )
        )
        delay(2_600)
        emit(CompanionResponseEvent.Finished)
    }
}
