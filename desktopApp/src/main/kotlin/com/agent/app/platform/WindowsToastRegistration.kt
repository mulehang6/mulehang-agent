package com.agent.app.platform

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import java.nio.file.Files
import java.nio.file.Path

/** 稳定的通知身份，安装后的快捷方式和 COM 注册必须使用同一组值。 */
internal const val WINDOWS_TOAST_APP_ID = "Mulehang.Agent"
internal const val WINDOWS_TOAST_ACTIVATOR_CLSID = "{A9EBB375-64B4-43F7-BC92-716692BE8D44}"

private val shellLinkClsid = GUID("00021401-0000-0000-C000-000000000046")
private val shellLinkIid = GUID("000214F9-0000-0000-C000-000000000046")
private val propertyStoreIid = GUID("886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99")
private val persistFileIid = GUID("0000010B-0000-0000-C000-000000000046")
private val appUserModelPropertyGuid = GUID("9F4C2855-9F79-4B39-A8D0-E1D42DE1D5F3")

/** 为当前安装包注册 AppUserModelID、冷启动 COM 服务器及开始菜单快捷方式。 */
internal object WindowsToastRegistration {
    private val shell32: ToastShell32 by lazy { Native.load("shell32", ToastShell32::class.java) }

    /** 开发模式的 java.exe 不登记为用户应用，已安装的 launcher 才能承载冷启动。 */
    fun installForCurrentProcess(): Boolean {
        val executable = ProcessHandle.current().info().command().orElse(null)?.let(Path::of) ?: return false
        if (!Files.isRegularFile(executable) ||
            !executable.fileName.toString().equals("mulehang-agent.exe", ignoreCase = true)
        ) return false
        checkHresult(shell32.SetCurrentProcessExplicitAppUserModelID(WString(WINDOWS_TOAST_APP_ID)), "设置 AppUserModelID")
        registerComServer(executable)
        createStartShortcut(executable)
        return true
    }

    /** HKCU 的 LocalServer32 允许用户退出应用后点击旧通知冷启动。 */
    private fun registerComServer(executable: Path) {
        val clsidKey = "Software\\Classes\\CLSID\\$WINDOWS_TOAST_ACTIVATOR_CLSID\\LocalServer32"
        Advapi32Util.registryCreateKey(HKEY_CURRENT_USER, clsidKey)
        Advapi32Util.registrySetStringValue(HKEY_CURRENT_USER, clsidKey, "", "\"$executable\" --toast-com-server")

        val appKey = "Software\\Classes\\AppUserModelId\\$WINDOWS_TOAST_APP_ID"
        Advapi32Util.registryCreateKey(HKEY_CURRENT_USER, appKey)
        Advapi32Util.registrySetStringValue(HKEY_CURRENT_USER, appKey, "DisplayName", "Mulehang Agent")
        Advapi32Util.registrySetStringValue(HKEY_CURRENT_USER, appKey, "CustomActivator", WINDOWS_TOAST_ACTIVATOR_CLSID)
    }

    /** 开始菜单 .lnk 的两项属性使通知中心能找到该应用与精确激活器。 */
    private fun createStartShortcut(executable: Path) {
        val appData = System.getenv("APPDATA")?.let(Path::of) ?: return
        val shortcut = appData.resolve("Microsoft/Windows/Start Menu/Programs/mulehang-agent.lnk")
        createShortcut(executable, shortcut)
    }

    /** 允许测试在临时目录验证 ShellLink 与属性存储的原生调用。 */
    internal fun createShortcut(executable: Path, shortcut: Path) {
        Files.createDirectories(shortcut.parent)
        val shellLinkRef = PointerByReference()
        checkHresult(
            Ole32.INSTANCE.CoCreateInstance(shellLinkClsid, null, 1, shellLinkIid, shellLinkRef).toInt(),
            "创建开始菜单快捷方式",
        )
        val shellLink = requireNotNull(shellLinkRef.value)
        try {
            checkHresult(comCall(shellLink, 20, shellLink, WString(executable.toString())), "IShellLink.SetPath")
            checkHresult(comCall(shellLink, 9, shellLink, WString(executable.parent.toString())), "IShellLink.SetWorkingDirectory")
            val propertyStore = query(shellLink, propertyStoreIid)
            try {
                setProperty(propertyStore, 5, 31, wideString(WINDOWS_TOAST_APP_ID))
                val clsid = GUID(WINDOWS_TOAST_ACTIVATOR_CLSID).also(GUID::write)
                setProperty(propertyStore, 26, 72, clsid.pointer)
                checkHresult(comCall(propertyStore, 7, propertyStore), "IPropertyStore.Commit")
            } finally {
                releaseComObject(propertyStore)
            }
            val persistFile = query(shellLink, persistFileIid)
            try {
                checkHresult(comCall(persistFile, 6, persistFile, WString(shortcut.toString()), 1), "IPersistFile.Save")
            } finally {
                releaseComObject(persistFile)
            }
        } finally {
            releaseComObject(shellLink)
        }
    }

    /** 写入 Windows PROPERTYKEY 对应的 PROPVARIANT。 */
    private fun setProperty(store: Pointer, propertyId: Int, variantType: Int, value: Pointer) {
        appUserModelPropertyGuid.write()
        val key = Memory(20).apply {
            write(0, appUserModelPropertyGuid.pointer.getByteArray(0, 16), 0, 16)
            setInt(16, propertyId)
        }
        val variant = Memory(24).apply {
            clear()
            setShort(0, variantType.toShort())
            setPointer(8, value)
        }
        checkHresult(comCall(store, 6, store, key, variant), "IPropertyStore.SetValue($propertyId)")
    }

    /** 查询一个经典 COM 接口。 */
    private fun query(source: Pointer, iid: GUID): Pointer {
        val reference = PointerByReference()
        checkHresult(comCall(source, 0, source, iid, reference), "QueryInterface")
        return requireNotNull(reference.value)
    }

    /** PROPVARIANT VT_LPWSTR 指向以零结尾的 UTF-16 内容。 */
    private fun wideString(value: String): Memory = Memory((value.length + 1L) * 2).apply { setWideString(0, value) }
}

/** Shell32 的进程级应用身份入口。 */
private interface ToastShell32 : StdCallLibrary {
    fun SetCurrentProcessExplicitAppUserModelID(appId: WString): Int
}
