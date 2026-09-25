package com.agent.app.platform

import com.agent.shared.chat.attention.ConversationAttentionEvent
import com.sun.jna.Function
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary

/** Windows Runtime 中展示 Toast 所需的最小 ABI。 */
internal object WindowsToastWinRt {
    private val combase: Combase by lazy { Native.load("combase", Combase::class.java) }
    private val managerIid = GUID("50AC103F-D235-4598-BBEF-98FE4D1A3AD4")
    private val notificationFactoryIid = GUID("04124B20-82C6-4229-B109-FD9ED4662B53")
    private val xmlIoIid = GUID("6CD0E74E-EE65-4489-9EBF-CA43E87BA637")

    /** 在当前 COM MTA 线程创建 XML、通知和 notifier 并交给 Windows 显示。 */
    fun show(event: ConversationAttentionEvent) {
        val liveObjects = ArrayList<Pointer>()
        val liveStrings = ArrayList<Pointer>()
        try {
            val managerClass = hstring("Windows.UI.Notifications.ToastNotificationManager", liveStrings)
            val notificationClass = hstring("Windows.UI.Notifications.ToastNotification", liveStrings)
            val xmlClass = hstring("Windows.Data.Xml.Dom.XmlDocument", liveStrings)
            val appId = hstring(WINDOWS_TOAST_APP_ID, liveStrings)
            val content = hstring(toastXml(event), liveStrings)

            val manager = activationFactory(managerClass, managerIid).also(liveObjects::add)
            val notifierRef = PointerByReference()
            checkHresult(comCall(manager, 7, manager, appId, notifierRef), "CreateToastNotifierWithId")
            val notifier = requireNotNull(notifierRef.value).also(liveObjects::add)

            val factory = activationFactory(notificationClass, notificationFactoryIid).also(liveObjects::add)
            val xmlInspectableRef = PointerByReference()
            checkHresult(combase.RoActivateInstance(xmlClass, xmlInspectableRef), "RoActivateInstance(XmlDocument)")
            val xmlInspectable = requireNotNull(xmlInspectableRef.value).also(liveObjects::add)
            val xmlIo = queryInterface(xmlInspectable, xmlIoIid).also(liveObjects::add)
            checkHresult(comCall(xmlIo, 6, xmlIo, content), "IXmlDocumentIO.LoadXml")

            val notificationRef = PointerByReference()
            checkHresult(comCall(factory, 6, factory, xmlInspectable, notificationRef), "CreateToastNotification")
            val notification = requireNotNull(notificationRef.value).also(liveObjects::add)
            checkHresult(comCall(notifier, 6, notifier, notification), "IToastNotifier.Show")
        } finally {
            liveObjects.asReversed().forEach(::releaseComObject)
            liveStrings.asReversed().forEach(combase::WindowsDeleteString)
        }
    }

    /** 对 XML 属性和文本使用同一转义，防止通知正文破坏 Toast 结构。 */
    internal fun toastXml(event: ConversationAttentionEvent): String {
        val launch = xmlEscape(event.toToastActivationTarget().toArguments())
        val title = xmlEscape(event.title.take(100))
        val body = xmlEscape(event.body.take(240))
        return """<toast activationType="foreground" launch="$launch"><visual><binding template="ToastGeneric"><text>$title</text><text>$body</text></binding></visual></toast>"""
    }

    /** WindowsCreateString 持有独立 HSTRING，避免临时字符缓冲区失效。 */
    private fun hstring(value: String, strings: MutableList<Pointer>): Pointer {
        val reference = PointerByReference()
        checkHresult(combase.WindowsCreateString(WString(value), value.length, reference), "WindowsCreateString")
        return requireNotNull(reference.value).also(strings::add)
    }

    /** 从运行时类名取得其静态工厂。 */
    private fun activationFactory(className: Pointer, iid: GUID): Pointer {
        val reference = PointerByReference()
        checkHresult(combase.RoGetActivationFactory(className, iid, reference), "RoGetActivationFactory")
        return requireNotNull(reference.value)
    }

    /** 查询 XML IO 接口，保留初始 IInspectable 供释放。 */
    private fun queryInterface(source: Pointer, iid: GUID): Pointer {
        val reference = PointerByReference()
        checkHresult(comCall(source, 0, source, iid, reference), "QueryInterface")
        return requireNotNull(reference.value)
    }
}

/** COM 与 WinRT 返回的负值为失败 HRESULT。 */
internal fun checkHresult(result: Int, operation: String) {
    check(result >= 0) { "$operation 失败 (HRESULT 0x${result.toUInt().toString(16)})" }
}

/** 通过 JNA 调用指定的 COM vtable 槽位。 */
internal fun comCall(instance: Pointer, slot: Int, vararg args: Any?): Int {
    val vtable = requireNotNull(instance.getPointer(0))
    val method = requireNotNull(vtable.getPointer(slot.toLong() * Native.POINTER_SIZE))
    return Function.getFunction(method, Function.ALT_CONVENTION).invokeInt(args)
}

/** 按 IUnknown 的 Release 约定释放一个接口引用。 */
internal fun releaseComObject(instance: Pointer) {
    comCall(instance, 2, instance)
}

/** Toast 内容必须保持单行可解析 XML。 */
internal fun xmlEscape(value: String): String = buildString(value.length) {
    value.forEach { character ->
        append(when (character) {
            '&' -> "&amp;"
            '<' -> "&lt;"
            '>' -> "&gt;"
            '"' -> "&quot;"
            '\'' -> "&apos;"
            else -> if (character >= ' ' || character == '\t' || character == '\n' || character == '\r') {
                character.toString()
            } else {
                " "
            }
        })
    }
}

/** WinRT 激活与 HSTRING 操作，使用系统 combase.dll。 */
private interface Combase : StdCallLibrary {
    fun RoInitialize(initType: Int): Int
    fun RoUninitialize()
    fun RoGetActivationFactory(className: Pointer, iid: GUID, result: PointerByReference): Int
    fun RoActivateInstance(className: Pointer, result: PointerByReference): Int
    fun WindowsCreateString(value: WString, length: Int, result: PointerByReference): Int
    fun WindowsDeleteString(value: Pointer): Int
}

/** 与 COM MTA 一致地初始化本线程的 Windows Runtime。 */
internal fun initializeWindowsRuntime(): Boolean = runCatching {
    checkHresult(Native.load("combase", Combase::class.java).RoInitialize(1), "RoInitialize")
    true
}.getOrDefault(false)

/** 与初始化线程配对的 WinRT 清理。 */
internal fun uninitializeWindowsRuntime() {
    Native.load("combase", Combase::class.java).RoUninitialize()
}
