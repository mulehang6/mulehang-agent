package com.agent.app.platform

import com.sun.jna.platform.win32.Ole32
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/** 临时快捷方式验证原生 ShellLink、属性存储和保存流程。 */
class WindowsToastRegistrationTest {
    /** 写入带通知身份属性的 .lnk，不碰真实开始菜单。 */
    @Test
    fun `creates shortcut with COM property store`() {
        checkHresult(Ole32.INSTANCE.CoInitializeEx(null, 0).toInt(), "测试初始化 COM")
        try {
            val executable = Path.of(requireNotNull(ProcessHandle.current().info().command().orElse(null)))
            val shortcut = Files.createTempDirectory("mulehang-toast-shortcut").resolve("app.lnk")
            WindowsToastRegistration.createShortcut(executable, shortcut)
            assertTrue(Files.isRegularFile(shortcut))
            assertTrue(Files.size(shortcut) > 0)
            assertEquals(WINDOWS_TOAST_APP_ID, readStringProperty(shortcut, 5))
            assertEquals(
                GUID(WINDOWS_TOAST_ACTIVATOR_CLSID).toGuidString(),
                readGuidProperty(shortcut, 26).toGuidString(),
            )
        } finally {
            Ole32.INSTANCE.CoUninitialize()
        }
    }

    /** 从已保存的快捷方式读取 AppUserModel 属性，验证属性真正落盘。 */
    private fun readStringProperty(shortcut: Path, propertyId: Int): String = withProperty(shortcut, propertyId) { variant ->
        assertEquals(31, variant.getShort(0).toInt())
        requireNotNull(variant.getPointer(8)).getWideString(0)
    }

    /** Toast activator 属性必须是原生 CLSID 类型。 */
    private fun readGuidProperty(shortcut: Path, propertyId: Int): GUID = withProperty(shortcut, propertyId) { variant ->
        assertEquals(72, variant.getShort(0).toInt())
        GUID(requireNotNull(variant.getPointer(8)))
    }

    /** 通过 Windows Shell 重新打开 .lnk 的属性存储。 */
    private fun <T> withProperty(shortcut: Path, propertyId: Int, read: (Memory) -> T): T {
        val shell = Native.load("shell32", ShortcutPropertyApi::class.java)
        val ole = Native.load("ole32", PropertyVariantApi::class.java)
        val storeRef = PointerByReference()
        checkHresult(shell.SHGetPropertyStoreFromParsingName(
            WString(shortcut.toString()), null, 0,
            GUID("886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99"), storeRef,
        ), "读取快捷方式属性")
        val store = requireNotNull(storeRef.value)
        try {
            val propertyGuid = GUID("9F4C2855-9F79-4B39-A8D0-E1D42DE1D5F3").also(GUID::write)
            val key = Memory(20).apply {
                write(0, propertyGuid.pointer.getByteArray(0, 16), 0, 16)
                setInt(16, propertyId)
            }
            val variant = Memory(24).apply { clear() }
            checkHresult(comCall(store, 5, store, key, variant), "IPropertyStore.GetValue")
            return try { read(variant) } finally { ole.PropVariantClear(variant) }
        } finally {
            releaseComObject(store)
        }
    }
}

/** Shell32 的快捷方式属性读取入口。 */
private interface ShortcutPropertyApi : StdCallLibrary {
    fun SHGetPropertyStoreFromParsingName(path: WString, bindContext: Pointer?, flags: Int, iid: GUID, result: PointerByReference): Int
}

/** 清理由 GetValue 分配的 PROPVARIANT 内容。 */
private interface PropertyVariantApi : StdCallLibrary {
    fun PropVariantClear(variant: Pointer): Int
}
