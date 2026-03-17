package com.roshan.book_peek

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.ToolWindowManager

/**
 * 上一章 Action
 */
class PrevChapterAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("BookPeek") ?: return
        
        // 获取阅读器面板并切换章节
        val content = toolWindow.contentManager.getContent(0) ?: return
        val panel = content.component as? NovelReaderPanel ?: return
        panel.goToPreviousChapter()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

/**
 * 下一章 Action
 */
class NextChapterAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("BookPeek") ?: return
        
        // 获取阅读器面板并切换章节
        val content = toolWindow.contentManager.getContent(0) ?: return
        val panel = content.component as? NovelReaderPanel ?: return
        panel.goToNextChapter()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

/**
 * 切换控制按钮显示 Action
 */
class ToggleControlsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val settings = ReaderSettings.getInstance()
        val currentState = settings.state
        
        // 切换显示状态
        settings.loadState(currentState.copy(showControls = !currentState.showControls))
        
        // 通知阅读器刷新
        ApplicationManager.getApplication().messageBus
            .syncPublisher(ReaderNotifier.TOPIC)
            .settingsChanged()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
