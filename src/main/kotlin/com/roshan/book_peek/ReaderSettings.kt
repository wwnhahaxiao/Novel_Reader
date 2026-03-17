package com.roshan.book_peek

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * 阅读器配置状态
 */
@State(
    name = "NovelReaderSettings",
    storages = [Storage("NovelReaderSettings.xml")]
)
@Service(Service.Level.APP)
class ReaderSettings : PersistentStateComponent<ReaderSettings.State> {

    private var myState = State()

    data class State(
        /** 上一章快捷键 */
        var prevChapterShortcut: String = "alt A",
        /** 下一章快捷键 */
        var nextChapterShortcut: String = "alt D",
        /** 章节目录正则表达式 */
        var chapterPattern: String = "^第[零一二三四五六七八九十百千万\\d]+[章节回集卷].*",
        /** 字号大小 */
        var fontSize: Int = 14,
        /** 是否显示控制按钮（章节目录、上一章、下一章） */
        var showControls: Boolean = true,
        /** 上次阅读的书籍文件路径 */
        var lastBookPath: String = "",
        /** 上次阅读的章节索引 */
        var lastChapterIndex: Int = 0
    )

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        @JvmStatic
        fun getInstance(): ReaderSettings {
            return ApplicationManager.getApplication().getService(ReaderSettings::class.java)
        }
    }
}
