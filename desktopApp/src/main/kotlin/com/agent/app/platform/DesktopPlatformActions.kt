package com.agent.app.platform

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.COM.COMUtils
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Guid.CLSID
import com.sun.jna.platform.win32.Guid.IID
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.awt.EventQueue
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.Window

private const val COM_INITIALIZATION_SUCCEEDED = 0
private const val COM_ALREADY_INITIALIZED = 1
private const val COM_ERROR_CANCELLED = 0x800704C7.toInt()
private const val CLSCTX_INPROC_SERVER = 0x0001
private const val FOS_PICK_FOLDERS = 0x0020
private const val FOS_FORCE_FILE_SYSTEM = 0x0040
private const val FOS_ALLOW_MULTI_SELECT = 0x0200
private const val FOS_FILE_MUST_EXIST = 0x1000
private const val SIGDN_FILE_SYSTEM_PATH = 0x80058000.toInt()
private const val VTABLE_I_FILE_DIALOG_SHOW = 3
private const val VTABLE_I_FILE_DIALOG_SET_OPTIONS = 9
private const val VTABLE_I_FILE_DIALOG_GET_OPTIONS = 10
private const val VTABLE_I_FILE_DIALOG_SET_TITLE = 17
private const val VTABLE_I_FILE_OPEN_DIALOG_GET_RESULTS = 27
private const val VTABLE_I_SHELL_ITEM_GET_DISPLAY_NAME = 5
private const val VTABLE_I_SHELL_ITEM_ARRAY_GET_COUNT = 7
private const val VTABLE_I_SHELL_ITEM_ARRAY_GET_ITEM_AT = 8
private const val DIALOG_CENTERING_RETRY_DELAY_MILLIS = 10L
private const val DIALOG_CENTERING_MAX_RETRIES = 100
private const val CENTER_DIALOG_WINDOW_FLAGS =
    WinUser.SWP_NOSIZE or WinUser.SWP_NOZORDER or WinUser.SWP_NOACTIVATE

private val FILE_OPEN_DIALOG_CLSID = CLSID("DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7")
private val FILE_OPEN_DIALOG_IID = IID("D57C7288-D4AD-4768-BE02-9D969532D960")

/**
 * 打开 Windows Shell 原生附件选择器并返回已选择文件的绝对路径。
 */
internal fun pickFiles(): List<String> =
    pickWindowsFileSystemPaths(
        title = "选择附件",
        allowMultiple = true,
        pickFolders = false,
    )

/**
 * 打开 Windows Shell 原生目录选择器并返回已选择目录的绝对路径。
 */
internal fun pickWorkspaceDirectory(): String? =
    pickWindowsFileSystemPaths(
        title = "选择工作区文件夹",
        allowMultiple = false,
        pickFolders = true,
    ).singleOrNull()

/**
 * 通过同一 Windows IFileOpenDialog 实现文件和目录选择，并在对话框显示后相对于所有者窗口居中。
 */
private fun pickWindowsFileSystemPaths(
    title: String,
    allowMultiple: Boolean,
    pickFolders: Boolean,
): List<String> =
    onAwtEventDispatchThread {
        onWindowsComThread {
            val dialog = createWindowsFileOpenDialog()
            try {
                val configuredOptions = dialog.options() or FOS_FORCE_FILE_SYSTEM or
                        (FOS_ALLOW_MULTI_SELECT.takeIf { allowMultiple } ?: 0) or
                        (FOS_PICK_FOLDERS.takeIf { pickFolders } ?: FOS_FILE_MUST_EXIST)
                dialog.setOptions(configuredOptions)
                dialog.setTitle(title)
                val owner = activeFrame()?.let(::toNativeWindowHandle)
                val showResult = dialog.showCenteredOnOwner(owner, title)
                if (showResult.toInt() == COM_ERROR_CANCELLED) {
                    emptyList()
                } else {
                    requireSuccess(showResult)
                    val results = dialog.results()
                    try {
                        results.paths()
                    } finally {
                        results.Release()
                    }
                }
            } finally {
                dialog.Release()
            }
        }
    }

/**
 * 在 AWT 事件线程调用 Windows 原生模态对话框，避免跨线程创建原生窗口。
 */
private fun <T> onAwtEventDispatchThread(action: () -> T): T {
    if (EventQueue.isDispatchThread()) {
        return action()
    }
    var result: Result<T>? = null
    EventQueue.invokeAndWait {
        result = runCatching(action)
    }
    return requireNotNull(result).getOrThrow()
}

/**
 * 在当前 AWT 线程初始化 COM，并在本次调用完成后平衡释放其初始化计数。
 */
private fun <T> onWindowsComThread(action: () -> T): T {
    val initializationResult = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED)
    val shouldUninitialize = initializationResult.toInt() in setOf(
        COM_INITIALIZATION_SUCCEEDED,
        COM_ALREADY_INITIALIZED,
    )
    try {
        return action()
    } finally {
        if (shouldUninitialize) {
            Ole32.INSTANCE.CoUninitialize()
        }
    }
}

/**
 * 取得当前活动或可见的顶层 Frame，使 Windows Shell 对话框保持在应用窗口之上并自动居中。
 */
private fun activeFrame(): Frame? =
    generateSequence(KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow) { window: Window ->
        window.owner
    }.filterIsInstance<Frame>().firstOrNull()
        ?: Window.getWindows().filterIsInstance<Frame>().firstOrNull { it.isActive || it.isFocused }
        ?: Window.getWindows().filterIsInstance<Frame>().firstOrNull { it.isShowing }

/**
 * 将 AWT Frame 的原生句柄交给 Windows Shell 作为对话框所有者。
 */
private fun toNativeWindowHandle(frame: Frame): WinDef.HWND =
    WinDef.HWND(Native.getComponentPointer(frame))

/**
 * 在 Windows Shell 对话框完成创建后，按主窗口的屏幕坐标将其移动到视觉中心。
 *
 * IFileDialog::Show 的 owner 参数只建立模态归属，不保证初始位置；Shell 还会复用已持久化的位置。
 */
private fun WindowsFileOpenDialog.showCenteredOnOwner(
    owner: WinDef.HWND?,
    title: String,
): WinNT.HRESULT {
    val centeringThread = owner?.let { startNativeDialogCentering(title, it) }
    return try {
        show(owner)
    } finally {
        centeringThread?.interrupt()
    }
}

/**
 * 启动短生命周期守护线程，在 Shell 完成延后布局前持续将其定位至主窗口中心。
 */
private fun startNativeDialogCentering(
    title: String,
    owner: WinDef.HWND,
): Thread = Thread(
    {
        repeat(DIALOG_CENTERING_MAX_RETRIES) {
            if (Thread.currentThread().isInterrupted) {
                return@Thread
            }
            val dialogWindow = User32.INSTANCE.FindWindow(null, title)
            if (dialogWindow != null && nativeWindowIsOwnedBy(dialogWindow, owner)) {
                centerNativeWindowOnOwner(dialogWindow, owner)
            }
            try {
                Thread.sleep(DIALOG_CENTERING_RETRY_DELAY_MILLIS)
            } catch (_: InterruptedException) {
                return@Thread
            }
        }
    },
    "windows-file-dialog-centering",
).apply {
    isDaemon = true
    start()
}

/**
 * 判断候选原生窗口是否由当前 Compose 主窗口拥有，避免移动同标题的其他应用窗口。
 */
private fun nativeWindowIsOwnedBy(
    candidate: WinDef.HWND,
    owner: WinDef.HWND,
): Boolean = User32.INSTANCE.GetWindow(candidate, WinDef.DWORD(WinUser.GW_OWNER.toLong()))?.pointer == owner.pointer

/**
 * 保持 Shell 对话框原有尺寸与层级，仅移动至主窗口外框的中心。
 */
private fun centerNativeWindowOnOwner(
    dialogWindow: WinDef.HWND,
    owner: WinDef.HWND,
) {
    val ownerBounds = WinDef.RECT()
    val dialogBounds = WinDef.RECT()
    if (!User32.INSTANCE.GetWindowRect(owner, ownerBounds) ||
        !User32.INSTANCE.GetWindowRect(dialogWindow, dialogBounds)
    ) {
        return
    }
    val centeredLeft =
        ownerBounds.left + (ownerBounds.right - ownerBounds.left - dialogBounds.right + dialogBounds.left) / 2
    val centeredTop =
        ownerBounds.top + (ownerBounds.bottom - ownerBounds.top - dialogBounds.bottom + dialogBounds.top) / 2
    User32.INSTANCE.SetWindowPos(
        dialogWindow,
        null,
        centeredLeft,
        centeredTop,
        0,
        0,
        CENTER_DIALOG_WINDOW_FLAGS,
    )
}

/**
 * 创建 Windows Vista 及以上版本提供的 IFileOpenDialog COM 对象。
 */
private fun createWindowsFileOpenDialog(): WindowsFileOpenDialog {
    val reference = PointerByReference()
    requireSuccess(
        Ole32.INSTANCE.CoCreateInstance(
            FILE_OPEN_DIALOG_CLSID,
            null,
            CLSCTX_INPROC_SERVER,
            FILE_OPEN_DIALOG_IID,
            reference,
        ),
    )
    return WindowsFileOpenDialog(requireNotNull(reference.value))
}

/**
 * 将 COM HRESULT 转换为 JNA 的标准异常，保留 Windows 的具体错误码。
 */
private fun requireSuccess(result: WinNT.HRESULT) {
    COMUtils.checkRC(result)
}

/**
 * IFileOpenDialog 的最小 COM vtable 映射，只覆盖本应用的文件系统选择需求。
 */
private class WindowsFileOpenDialog(pointer: Pointer) : Unknown(pointer) {
    /**
     * 读取 Shell 提供的默认选项，避免覆盖系统默认可用性行为。
     */
    fun options(): Int {
        val options = IntByReference()
        requireSuccess(invoke(VTABLE_I_FILE_DIALOG_GET_OPTIONS, options))
        return options.value
    }

    /**
     * 配置文件、目录及多选行为。
     */
    fun setOptions(options: Int) {
        requireSuccess(invoke(VTABLE_I_FILE_DIALOG_SET_OPTIONS, options))
    }

    /**
     * 设置原生窗口标题，使用宽字符确保中文正确显示。
     */
    fun setTitle(title: String) {
        requireSuccess(invoke(VTABLE_I_FILE_DIALOG_SET_TITLE, WString(title)))
    }

    /**
     * 以主窗口句柄为 owner 显示 Windows Shell 对话框。
     */
    fun show(owner: WinDef.HWND?): WinNT.HRESULT = invoke(VTABLE_I_FILE_DIALOG_SHOW, owner)

    /**
     * 在对话框成功关闭后取得全部已选择的 Shell 项。
     */
    fun results(): WindowsShellItemArray {
        val reference = PointerByReference()
        requireSuccess(invoke(VTABLE_I_FILE_OPEN_DIALOG_GET_RESULTS, reference))
        return WindowsShellItemArray(requireNotNull(reference.value))
    }

    /**
     * 调用 COM vtable 并自动将当前接口指针置为第一个参数。
     */
    private fun invoke(vtableIndex: Int, vararg arguments: Any?): WinNT.HRESULT =
        _invokeNativeObject(
            vtableIndex,
            arrayOf(pointer, *arguments),
            WinNT.HRESULT::class.java,
        ) as WinNT.HRESULT
}

/**
 * IShellItemArray 的最小 COM vtable 映射，用于遍历选择结果。
 */
private class WindowsShellItemArray(pointer: Pointer) : Unknown(pointer) {
    /**
     * 将全部 Shell 项转换为文件系统绝对路径，并释放每个 COM 项。
     */
    fun paths(): List<String> {
        val count = IntByReference()
        requireSuccess(invoke(VTABLE_I_SHELL_ITEM_ARRAY_GET_COUNT, count))
        return buildList(count.value) {
            repeat(count.value) { index ->
                val item = itemAt(index)
                try {
                    add(item.fileSystemPath())
                } finally {
                    item.Release()
                }
            }
        }
    }

    /**
     * 从结果数组取得指定位置的 Shell 项。
     */
    private fun itemAt(index: Int): WindowsShellItem {
        val reference = PointerByReference()
        requireSuccess(invoke(VTABLE_I_SHELL_ITEM_ARRAY_GET_ITEM_AT, index, reference))
        return WindowsShellItem(requireNotNull(reference.value))
    }

    /**
     * 调用 COM vtable 并自动将当前接口指针置为第一个参数。
     */
    private fun invoke(vtableIndex: Int, vararg arguments: Any?): WinNT.HRESULT =
        _invokeNativeObject(
            vtableIndex,
            arrayOf(pointer, *arguments),
            WinNT.HRESULT::class.java,
        ) as WinNT.HRESULT
}

/**
 * IShellItem 的最小 COM vtable 映射，用于获取文件系统路径。
 */
private class WindowsShellItem(pointer: Pointer) : Unknown(pointer) {
    /**
     * 读取并释放由 Shell 分配的 Unicode 文件系统路径。
     */
    fun fileSystemPath(): String {
        val reference = PointerByReference()
        requireSuccess(invokeGetDisplayName(SIGDN_FILE_SYSTEM_PATH, reference))
        val pathPointer = requireNotNull(reference.value)
        return try {
            pathPointer.getWideString(0)
        } finally {
            Ole32.INSTANCE.CoTaskMemFree(pathPointer)
        }
    }

    /**
     * 调用 COM vtable 并自动将当前接口指针置为第一个参数。
     */
    private fun invokeGetDisplayName(vararg arguments: Any?): WinNT.HRESULT =
        _invokeNativeObject(
            VTABLE_I_SHELL_ITEM_GET_DISPLAY_NAME,
            arrayOf(pointer, *arguments),
            WinNT.HRESULT::class.java,
        ) as WinNT.HRESULT
}

/**
 * 构造内嵌终端默认使用的 Windows PowerShell 命令。
 */
internal fun buildPowerShellCommand(): List<String> = listOf("powershell.exe", "-NoLogo")
