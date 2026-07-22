package com.agent.app.platform

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
 * 构造内嵌终端默认使用的 Windows PowerShell 命令。
 */
internal fun buildPowerShellCommand(): List<String> = listOf("powershell.exe", "-NoLogo")
