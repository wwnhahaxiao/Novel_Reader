package com.roshan.book_peek

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import java.io.File
import javax.swing.*

/**
 * 阅读器设置配置界面
 */
class ReaderSettingsConfigurable : Configurable {

    private var settingsPanel: JPanel? = null
    private var bookFileField: TextFieldWithBrowseButton? = null
    private var fontSizeSpinner: JSpinner? = null
    private var showControlsCheckBox: JCheckBox? = null
    private var chapterPatternField: JBTextField? = null

    override fun getDisplayName(): String = "BookPeek"

    override fun createComponent(): JComponent {
        val settings = ReaderSettings.getInstance().state

        // 打开文件选择器 - 默认定位到上次书籍的目录
        val fileDescriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("txt").apply {
            title = "选择小说文件"
            description = "请选择一个 TXT 格式的小说文件"
        }
        
        bookFileField = TextFieldWithBrowseButton().apply {
            text = settings.lastBookPath
            addBrowseFolderListener(object : TextBrowseFolderListener(fileDescriptor) {
                override fun getInitialFile(): com.intellij.openapi.vfs.VirtualFile? {
                    // 默认打开上次书籍所在的目录
                    val lastPath = settings.lastBookPath
                    if (lastPath.isNotBlank()) {
                        val file = File(lastPath)
                        val parentDir = if (file.exists()) file.parentFile else file.parentFile
                        if (parentDir?.exists() == true) {
                            return LocalFileSystem.getInstance().findFileByIoFile(parentDir)
                        }
                    }
                    return super.getInitialFile()
                }
            })
        }

        // 字号配置
        fontSizeSpinner = JSpinner(SpinnerNumberModel(settings.fontSize, 8, 48, 1))
        
        // 显示控制按钮开关
        showControlsCheckBox = JCheckBox("显示控制按钮", settings.showControls)

        chapterPatternField = JBTextField(settings.chapterPattern, 40)

        settingsPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("打开书籍文件:"), bookFileField!!, 1, false)
            .addTooltip("选择要阅读的 TXT 小说文件")
            .addSeparator()
            .addLabeledComponent(JBLabel("字号大小:"), fontSizeSpinner!!, 1, false)
            .addTooltip("阅读器内容显示的字号 (8-48)")
            .addSeparator()
            .addComponent(showControlsCheckBox!!)
            .addTooltip("显示章节目录、上一章、下一章按钮 (快捷键 Alt+C 切换)")
            .addSeparator()
            .addLabeledComponent(JBLabel("章节标题正则:"), chapterPatternField!!, 1, false)
            .addTooltip("用于识别章节标题的正则表达式")
            .addSeparator()
            .addComponent(JBLabel("快捷键: Alt+A (上一章), Alt+D (下一章), Alt+C (切换按钮显示)"))
            .addTooltip("如需修改快捷键，请到 Settings → Keymap 搜索 BookPeek")
            .addComponentFillVertically(JPanel(), 0)
            .panel

        val wrapper = JPanel(BorderLayout())
        wrapper.add(settingsPanel, BorderLayout.NORTH)
        return wrapper
    }

    override fun isModified(): Boolean {
        val settings = ReaderSettings.getInstance().state
        return bookFileField?.text != settings.lastBookPath ||
                (fontSizeSpinner?.value as? Int) != settings.fontSize ||
                showControlsCheckBox?.isSelected != settings.showControls ||
                chapterPatternField?.text != settings.chapterPattern
    }

    override fun apply() {
        val settings = ReaderSettings.getInstance()
        val currentState = settings.state
        val newBookPath = bookFileField?.text ?: ""
        
        // 如果书籍路径改变了，重置章节索引
        val newChapterIndex = if (newBookPath != currentState.lastBookPath) 0 else currentState.lastChapterIndex
        
        settings.loadState(
            ReaderSettings.State(
                prevChapterShortcut = currentState.prevChapterShortcut,
                nextChapterShortcut = currentState.nextChapterShortcut,
                chapterPattern = chapterPatternField?.text ?: "^第[零一二三四五六七八九十百千万\\d]+[章节回集卷].*",
                fontSize = (fontSizeSpinner?.value as? Int) ?: 14,
                showControls = showControlsCheckBox?.isSelected ?: true,
                lastBookPath = newBookPath,
                lastChapterIndex = newChapterIndex
            )
        )
        
        // 通知阅读器刷新
        ApplicationManager.getApplication().messageBus
            .syncPublisher(ReaderNotifier.TOPIC)
            .settingsChanged()
    }

    override fun reset() {
        val settings = ReaderSettings.getInstance().state
        bookFileField?.text = settings.lastBookPath
        fontSizeSpinner?.value = settings.fontSize
        showControlsCheckBox?.isSelected = settings.showControls
        chapterPatternField?.text = settings.chapterPattern
    }

    override fun disposeUIResources() {
        settingsPanel = null
        bookFileField = null
        fontSizeSpinner = null
        showControlsCheckBox = null
        chapterPatternField = null
    }
}
