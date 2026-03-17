package com.roshan.book_peek

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.*

/**
 * 小说阅读器工具窗口工厂
 */
class NovelReaderToolWindowFactory : ToolWindowFactory {
    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // 设置窗口失焦时自动隐藏
        toolWindow.setAutoHide(true)
        
        val readerPanel = NovelReaderPanel(project, toolWindow)
        val content = ContentFactory.getInstance().createContent(readerPanel, "阅读器", false)
        toolWindow.contentManager.addContent(content)
    }
}

/**
 * 章节数据类
 */
data class Chapter(val title: String, val content: String)

/**
 * 阅读器主面板 (Swing UI)
 */
class NovelReaderPanel(
    private val project: Project?,
    private val toolWindow: ToolWindow? = null
) : JPanel(BorderLayout()), Disposable {
    private var currentChapterIndex = 0
    private var chapters: MutableList<Chapter> = mutableListOf()
    private var currentBookPath: String = ""
    private var lastLoadedBookPath: String = ""
    
    private val titleLabel: JBLabel
    private val contentArea: JTextArea
    private val pageLabel: JBLabel
    private val prevButton: JButton
    private val nextButton: JButton
    private val chapterListButton: JButton
    private var currentBookName: String = "未加载书籍"
    
    // 控制面板（顶部面板和底部导航）
    private val topPanel: JPanel
    private val topToolBar: JPanel
    private val bottomPanel: JPanel
    
    // CardLayout 用于切换阅读视图和章节目录视图
    private val cardLayout = CardLayout()
    private val contentPanel: JPanel
    private val chapterListPanel: JPanel
    private val chapterJList: JBList<String>
    private val chapterListModel = DefaultListModel<String>()
    
    companion object {
        private const val CARD_READER = "reader"
        private const val CARD_CHAPTERS = "chapters"
    }

    init {
        val settings = ReaderSettings.getInstance().state
        
        // 订阅配置变化通知
        ApplicationManager.getApplication().messageBus
            .connect(this)
            .subscribe(ReaderNotifier.TOPIC, object : ReaderNotifier {
                override fun settingsChanged() {
                    onSettingsChanged()
                }
            })
        
        // 顶部面板（包含标题和工具栏）
        topPanel = JPanel(BorderLayout())
        
        // 标题
        titleLabel = JBLabel(currentBookName, SwingConstants.CENTER).apply {
            font = font.deriveFont(Font.BOLD, 16f)
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }
        
        // 工具按钮
        topToolBar = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))

        chapterListButton = JButton("章节目录").apply {
            addActionListener { toggleChapterList() }
        }
        topToolBar.add(chapterListButton)
        
        topPanel.add(topToolBar, BorderLayout.WEST)
        topPanel.add(titleLabel, BorderLayout.CENTER)
        add(topPanel, BorderLayout.NORTH)

        // 使用 CardLayout 的内容面板
        contentPanel = JPanel(cardLayout)
        
        // 阅读视图
        val readerPanel = JPanel(BorderLayout())
        contentArea = JTextArea().apply {
            text = "请在设置中选择书籍文件开始阅读...\n\nSettings → Tools → BookPeek"
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            font = Font("Microsoft YaHei", Font.PLAIN, settings.fontSize)
            border = BorderFactory.createEmptyBorder(10, 15, 10, 15)
        }
        val readerScrollPane = JBScrollPane(contentArea).apply {
            border = BorderFactory.createEmptyBorder()
        }
        readerPanel.add(readerScrollPane, BorderLayout.CENTER)
        
        // 章节目录视图
        chapterListPanel = JPanel(BorderLayout())
        chapterJList = JBList(chapterListModel).apply {
            font = Font("Microsoft YaHei", Font.PLAIN, 14)
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            // 双击选择章节
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {
                        selectChapter(selectedIndex)
                    }
                }
            })
            // 回车选择章节
            addKeyListener(object : java.awt.event.KeyAdapter() {
                override fun keyPressed(e: java.awt.event.KeyEvent) {
                    if (e.keyCode == java.awt.event.KeyEvent.VK_ENTER) {
                        selectChapter(selectedIndex)
                    }
                }
            })
        }
        val chapterScrollPane = JBScrollPane(chapterJList).apply {
            border = BorderFactory.createEmptyBorder()
        }
        chapterListPanel.add(chapterScrollPane, BorderLayout.CENTER)
        
        // 章节目录底部提示
        val chapterTip = JBLabel("双击或按回车选择章节", SwingConstants.CENTER).apply {
            border = BorderFactory.createEmptyBorder(5, 0, 5, 0)
        }
        chapterListPanel.add(chapterTip, BorderLayout.SOUTH)
        
        contentPanel.add(readerPanel, CARD_READER)
        contentPanel.add(chapterListPanel, CARD_CHAPTERS)
        add(contentPanel, BorderLayout.CENTER)

        // 底部导航
        bottomPanel = JPanel(FlowLayout(FlowLayout.CENTER, 20, 10))
        
        prevButton = JButton("上一章").apply {
            isEnabled = false
            addActionListener { goToPreviousChapter() }
        }
        bottomPanel.add(prevButton)

        pageLabel = JBLabel("0 / 0")
        bottomPanel.add(pageLabel)

        nextButton = JButton("下一章").apply {
            isEnabled = false
            addActionListener { goToNextChapter() }
        }
        bottomPanel.add(nextButton)

        add(bottomPanel, BorderLayout.SOUTH)
        
        // 恢复上次阅读进度
        restoreLastReadingProgress()
        
        // 初始化控制按钮可见性
        updateControlsVisibility()
    }
    
    /**
     * 切换章节目录显示
     */
    private fun toggleChapterList() {
        if (chapters.isEmpty()) {
            return
        }
        
        // 检查当前显示的是哪个视图
        val isShowingChapters = chapterListButton.text == "返回阅读"
        
        if (isShowingChapters) {
            // 切换回阅读视图
            cardLayout.show(contentPanel, CARD_READER)
            chapterListButton.text = "章节目录"
            titleLabel.text = "$currentBookName - ${chapters[currentChapterIndex].title}"
        } else {
            // 显示章节目录
            updateChapterListModel()
            chapterJList.selectedIndex = currentChapterIndex
            chapterJList.ensureIndexIsVisible(currentChapterIndex)
            cardLayout.show(contentPanel, CARD_CHAPTERS)
            chapterListButton.text = "返回阅读"
            titleLabel.text = "$currentBookName - 章节目录"
        }
    }
    
    /**
     * 更新章节列表模型
     */
    private fun updateChapterListModel() {
        chapterListModel.clear()
        chapters.forEachIndexed { index, chapter ->
            val prefix = if (index == currentChapterIndex) "▶ " else "   "
            chapterListModel.addElement("$prefix${index + 1}. ${chapter.title}")
        }
    }
    
    /**
     * 选择章节
     */
    private fun selectChapter(index: Int) {
        if (index in chapters.indices) {
            currentChapterIndex = index
            updateContent()
            saveReadingProgress()
            // 切换回阅读视图
            cardLayout.show(contentPanel, CARD_READER)
            chapterListButton.text = "章节目录"
        }
    }

    /**
     * 恢复上次阅读进度
     */
    private fun restoreLastReadingProgress() {
        val settings = ReaderSettings.getInstance().state
        val lastBookPath = settings.lastBookPath
        
        if (lastBookPath.isNotBlank()) {
            val file = File(lastBookPath)
            if (file.exists() && file.isFile) {
                try {
                    val content = file.readText(Charsets.UTF_8)
                    parseBookContent(file.nameWithoutExtension, content, file.absolutePath)
                    lastLoadedBookPath = file.absolutePath
                    
                    // 恢复章节位置
                    val lastIndex = settings.lastChapterIndex
                    if (lastIndex in chapters.indices) {
                        currentChapterIndex = lastIndex
                        updateContent()
                    }
                } catch (e: Exception) {
                    // 恢复失败，清空内容
                    chapters.clear()
                    lastLoadedBookPath = ""
                    currentBookName = "未加载书籍"
                    updateContent()
                }
            } else {
                chapters.clear()
                lastLoadedBookPath = ""
                currentBookName = "未加载书籍"
                updateContent()
            }
        } else {
            chapters.clear()
            lastLoadedBookPath = ""
            currentBookName = "未加载书籍"
            updateContent()
        }
    }

    /**
     * 配置变化时调用
     */
    private fun onSettingsChanged() {
        val settings = ReaderSettings.getInstance().state
        val newBookPath = settings.lastBookPath
        
        // 书籍路径变化，重新加载
        if (newBookPath != lastLoadedBookPath) {
            restoreLastReadingProgress()
        } else {
            // 只更新显示（字号、控制按钮可见性等）
            updateContent()
        }
        
        // 更新控制按钮可见性
        updateControlsVisibility()
    }
    
    /**
     * 更新控制按钮的可见性
     */
    private fun updateControlsVisibility() {
        val settings = ReaderSettings.getInstance().state
        val showControls = settings.showControls
        
        // 隐藏/显示顶部面板（包括标题和工具栏）和底部导航
        topPanel.isVisible = showControls
        bottomPanel.isVisible = showControls
        
        // 如果隐藏控制按钮且当前在章节目录视图，切换回阅读视图
        if (!showControls && chapterListButton.text == "返回阅读") {
            cardLayout.show(contentPanel, CARD_READER)
            chapterListButton.text = "章节目录"
        }
        
        revalidate()
        repaint()
    }

    override fun dispose() {
        // 清理资源
    }

    /**
     * 保存阅读进度
     */
    private fun saveReadingProgress() {
        val settings = ReaderSettings.getInstance()
        val state = settings.state
        settings.loadState(
            state.copy(
                lastBookPath = currentBookPath,
                lastChapterIndex = currentChapterIndex
            )
        )
    }

    /**
     * 解析书籍内容，按章节分割
     */
    private fun parseBookContent(bookName: String, content: String, filePath: String = "") {
        val settings = ReaderSettings.getInstance().state
        val chapterPattern = Regex(settings.chapterPattern, RegexOption.MULTILINE)
        
        chapters.clear()
        currentChapterIndex = 0
        currentBookName = bookName
        currentBookPath = filePath
        
        val lines = content.lines()
        var currentTitle = "序章"
        val currentContent = StringBuilder()
        
        for (line in lines) {
            if (chapterPattern.matches(line.trim())) {
                // 保存上一章
                if (currentContent.isNotBlank()) {
                    chapters.add(Chapter(currentTitle, currentContent.toString().trim()))
                }
                // 开始新章节
                currentTitle = line.trim()
                currentContent.clear()
            } else {
                currentContent.appendLine(line)
            }
        }
        
        // 保存最后一章
        if (currentContent.isNotBlank()) {
            chapters.add(Chapter(currentTitle, currentContent.toString().trim()))
        }
        
        // 如果没有识别到章节，将整个内容作为一章
        if (chapters.isEmpty()) {
            chapters.add(Chapter(bookName, content))
        }
        
        updateContent()
        saveReadingProgress()
    }


    /**
     * 上一章 (public 供 Action 调用)
     */
    fun goToPreviousChapter() {
        if (currentChapterIndex > 0) {
            currentChapterIndex--
            updateContent()
            saveReadingProgress()
        }
    }

    /**
     * 下一章 (public 供 Action 调用)
     */
    fun goToNextChapter() {
        if (currentChapterIndex < chapters.size - 1) {
            currentChapterIndex++
            updateContent()
            saveReadingProgress()
        }
    }

    /**
     * 更新显示内容
     */
    private fun updateContent() {
        // 更新字号
        val settings = ReaderSettings.getInstance().state
        contentArea.font = Font("Microsoft YaHei", Font.PLAIN, settings.fontSize)
        
        if (chapters.isEmpty()) {
            titleLabel.text = "未加载书籍"
            contentArea.text = "请在设置中选择书籍文件开始阅读...\n\nSettings → Tools → BookPeek"
            pageLabel.text = "0 / 0"
            prevButton.isEnabled = false
            nextButton.isEnabled = false
            return
        }
        
        val chapter = chapters[currentChapterIndex]
        titleLabel.text = "$currentBookName - ${chapter.title}"
        contentArea.text = chapter.content
        contentArea.caretPosition = 0  // 滚动到顶部
        pageLabel.text = "${currentChapterIndex + 1} / ${chapters.size}"
        prevButton.isEnabled = currentChapterIndex > 0
        nextButton.isEnabled = currentChapterIndex < chapters.size - 1
    }
}