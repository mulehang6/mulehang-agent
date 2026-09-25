package com.agent.app.platform

import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.ptr.PointerByReference
import kotlin.test.Test
import kotlin.test.assertEquals

/** 以真实 Windows COM 调用验证 Java/JNA 类工厂和激活回调的 ABI。 */
class WindowsToastActivatorTest {
    /** 注册后的 CLSID 应能创建回调接口并交付 launch 参数。 */
    @Test
    fun `registered activator receives COM callback`() {
        checkHresult(Ole32.INSTANCE.CoInitializeEx(null, 0).toInt(), "测试初始化 COM")
        var received: String? = null
        val activator = WindowsToastActivator { received = it }
        try {
            activator.register()
            val callback = PointerByReference()
            checkHresult(Ole32.INSTANCE.CoCreateInstance(
                GUID(WINDOWS_TOAST_ACTIVATOR_CLSID), null, 4,
                GUID("53E31837-6600-4A81-9395-75CFFE746F94"), callback,
            ).toInt(), "CoCreateInstance")
            val instance = requireNotNull(callback.value)
            try {
                checkHresult(comCall(instance, 3, instance, WString(WINDOWS_TOAST_APP_ID), WString("eventId=e&conversationId=c"), null, 0),
                    "INotificationActivationCallback.Activate")
                assertEquals("eventId=e&conversationId=c", received)
            } finally {
                releaseComObject(instance)
            }
        } finally {
            activator.revoke()
            Ole32.INSTANCE.CoUninitialize()
        }
    }
}
