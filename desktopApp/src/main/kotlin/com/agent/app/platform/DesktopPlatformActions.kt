package com.agent.app.platform

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileSystemView

/**
 * 打开桌面文件选择器并返回已选择文件的绝对路径。
 */
internal fun pickFiles(): List<String> {
    val chooser = JFileChooser(FileSystemView.getFileSystemView().defaultDirectory).apply {
        isMultiSelectionEnabled = true
        fileSelectionMode = JFileChooser.FILES_ONLY
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFiles.map(File::getAbsolutePath)
    } else {
        emptyList()
    }
}

/**
 * 打开桌面目录选择器并返回已选择目录的绝对路径。
 */
internal fun pickWorkspaceDirectory(): String? {
    val chooser = JFileChooser(FileSystemView.getFileSystemView().defaultDirectory).apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.absolutePath
    } else {
        null
    }
}

/**
 * 打开对话记录保存选择器并返回用户选择的文件。
 */
internal fun pickTranscriptSaveFile(defaultFileName: String): File? {
    val chooser = JFileChooser(FileSystemView.getFileSystemView().defaultDirectory).apply {
        dialogTitle = "Save transcript"
        selectedFile = File(defaultFileName)
    }
    return if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile
    } else {
        null
    }
}

/**
 * 把文本写入系统剪贴板。
 */
internal fun copyTextToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}
