package com.roshan.book_peek

import com.intellij.util.messages.Topic

/**
 * 阅读器配置变化通知接口
 */
interface ReaderNotifier {
    companion object {
        val TOPIC = Topic.create("NovelReaderSettingsChanged", ReaderNotifier::class.java)
    }

    /**
     * 配置变化时调用
     */
    fun settingsChanged()
}
