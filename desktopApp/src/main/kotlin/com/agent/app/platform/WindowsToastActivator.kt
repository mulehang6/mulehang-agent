package com.agent.app.platform

import com.sun.jna.CallbackReference
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary

private const val E_NOINTERFACE = 0x80004002.toInt()
private const val CLASS_E_NOAGGREGATION = 0x80040110.toInt()
private const val CLASS_CONTEXT_LOCAL_SERVER = 4
private const val REGISTER_CLASS_MULTIPLE_USE = 1
private val unknownIid = GUID("00000000-0000-0000-C000-000000000046").nativeBytes()
private val classFactoryIid = GUID("00000001-0000-0000-C000-000000000046").nativeBytes()
private val notificationCallbackIid = GUID("53E31837-6600-4A81-9395-75CFFE746F94").nativeBytes()

/** JNA 实现 Windows 通知中心所需的 IClassFactory 和 INotificationActivationCallback。 */
internal class WindowsToastActivator(private val onActivation: (String) -> Unit) {
    private val ole32: ToastOle32 = Native.load("ole32", ToastOle32::class.java)
    private val callbacks = ArrayList<Any>()
    private val factoryVtable = Memory(5L * Native.POINTER_SIZE)
    private val activatorVtable = Memory(4L * Native.POINTER_SIZE)
    private val factoryObject = Memory(Native.POINTER_SIZE.toLong())
    private val activatorObject = Memory(Native.POINTER_SIZE.toLong())
    private var registrationCookie = 0

    init {
        factoryObject.setPointer(0, factoryVtable)
        activatorObject.setPointer(0, activatorVtable)
        put(factoryVtable, 0, QueryCallback { _, iid, result ->
            query(iid, result, classFactoryIid, factoryObject)
        })
        put(factoryVtable, 1, ReferenceCallback { 2 })
        put(factoryVtable, 2, ReferenceCallback { 1 })
        put(factoryVtable, 3, CreateCallback { _, outer, iid, result ->
            if (outer != null) {
                result.value = null
                CLASS_E_NOAGGREGATION
            } else {
                query(iid, result, notificationCallbackIid, activatorObject)
            }
        })
        put(factoryVtable, 4, LockCallback { _, _ -> 0 })
        put(activatorVtable, 0, QueryCallback { _, iid, result ->
            query(iid, result, notificationCallbackIid, activatorObject)
        })
        put(activatorVtable, 1, ReferenceCallback { 2 })
        put(activatorVtable, 2, ReferenceCallback { 1 })
        put(activatorVtable, 3, ActivateCallback { _, appId, arguments, _, _ ->
            if (appId?.getWideString(0) == WINDOWS_TOAST_APP_ID) {
                arguments?.getWideString(0)?.let(onActivation)
            }
            0
        })
    }

    /** 在已初始化的 COM MTA 线程注册热启动类工厂。 */
    fun register() {
        val cookie = IntByReference()
        checkHresult(
            ole32.CoRegisterClassObject(
                GUID(WINDOWS_TOAST_ACTIVATOR_CLSID),
                factoryObject,
                CLASS_CONTEXT_LOCAL_SERVER,
                REGISTER_CLASS_MULTIPLE_USE,
                cookie,
            ),
            "CoRegisterClassObject",
        )
        registrationCookie = cookie.value
    }

    /** 退出前注销当前进程的类工厂。 */
    fun revoke() {
        if (registrationCookie != 0) {
            ole32.CoRevokeClassObject(registrationCookie)
            registrationCookie = 0
        }
    }

    /** IUnknown 与目标接口均可复用固定生命周期的对象。 */
    private fun query(iid: Pointer, result: PointerByReference, expected: ByteArray, instance: Pointer): Int {
        result.value = null
        if (iid.getByteArray(0, 16).contentEquals(unknownIid) ||
            iid.getByteArray(0, 16).contentEquals(expected)
        ) {
            result.value = instance
            return 0
        }
        return E_NOINTERFACE
    }

    /** 将回调放入固定 vtable，同时强引用保证 JNA trampoline 不被回收。 */
    private fun put(vtable: Memory, slot: Int, callback: com.sun.jna.Callback) {
        callbacks += callback
        vtable.setPointer(slot.toLong() * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(callback))
    }
}

/** GUID 写入原生结构后按内存布局比较 IID。 */
private fun GUID.nativeBytes(): ByteArray {
    write()
    return pointer.getByteArray(0, 16)
}

private fun interface QueryCallback : StdCallLibrary.StdCallCallback {
    fun invoke(self: Pointer, iid: Pointer, result: PointerByReference): Int
}

private fun interface ReferenceCallback : StdCallLibrary.StdCallCallback {
    fun invoke(self: Pointer): Int
}

private fun interface CreateCallback : StdCallLibrary.StdCallCallback {
    fun invoke(self: Pointer, outer: Pointer?, iid: Pointer, result: PointerByReference): Int
}

private fun interface LockCallback : StdCallLibrary.StdCallCallback {
    fun invoke(self: Pointer, locked: Int): Int
}

private fun interface ActivateCallback : StdCallLibrary.StdCallCallback {
    fun invoke(self: Pointer, appId: Pointer?, arguments: Pointer?, data: Pointer?, count: Int): Int
}

/** COM 本地服务器的进程级类对象注册。 */
private interface ToastOle32 : StdCallLibrary {
    fun CoRegisterClassObject(clsid: GUID, factory: Pointer, context: Int, flags: Int, cookie: IntByReference): Int
    fun CoRevokeClassObject(cookie: Int): Int
}
