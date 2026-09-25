package com.agent.app.platform

import com.agent.shared.chat.attention.ConversationAttentionEvent
import com.sun.jna.Platform
import com.sun.jna.platform.win32.Ole32
import java.awt.EventQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** 在专用 COM MTA 线程展示 Windows Toast，点击回调交回桌面 UI 线程。 */
internal object WindowsToastService {
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "mulehang-toast-com").apply { isDaemon = true }
    }
    private val activationLock = Any()
    private var activationHandler: ((ToastActivationTarget) -> Unit)? = null
    private val pendingActivations = ArrayDeque<ToastActivationTarget>()
    private var activator: WindowsToastActivator? = null
    @Volatile private var ready = false

    /** 只在真实 Windows 安装包中启用原生通知；失败时侧栏关注事件仍可用。 */
    fun start() {
        if (!Platform.isWindows() || !started.compareAndSet(false, true)) return
        worker.execute {
            var comInitialized = false
            var winRtInitialized = false
            try {
                checkHresult(Ole32.INSTANCE.CoInitializeEx(null, 0).toInt(), "CoInitializeEx")
                comInitialized = true
                winRtInitialized = initializeWindowsRuntime()
                if (!winRtInitialized || !WindowsToastRegistration.installForCurrentProcess()) return@execute
                activator = WindowsToastActivator(::activated).also(WindowsToastActivator::register)
                ready = true
            } catch (failure: Exception) {
                System.err.println("Windows Toast 初始化失败：${failure.message}")
            } finally {
                if (!ready) {
                    if (winRtInitialized) uninitializeWindowsRuntime()
                    if (comInitialized) Ole32.INSTANCE.CoUninitialize()
                }
            }
        }
    }

    /** 后台会话有新关注事件时，显示带精确目标参数的系统通知。 */
    fun show(event: ConversationAttentionEvent) {
        start()
        if (!Platform.isWindows()) return
        runCatching {
            worker.execute {
                if (!ready) return@execute
                runCatching { WindowsToastWinRt.show(event) }
                    .onFailure { System.err.println("Windows Toast 显示失败：${it.message}") }
            }
        }
    }

    /** 窗口创建后接收热启动或冷启动期间积压的激活请求。 */
    fun setActivationHandler(handler: ((ToastActivationTarget) -> Unit)?) {
        val pending = synchronized(activationLock) {
            activationHandler = handler
            if (handler == null) emptyList() else pendingActivations.toList().also { pendingActivations.clear() }
        }
        if (handler != null) pending.forEach { target -> EventQueue.invokeLater { handler(target) } }
    }

    /** 退出应用时按线程顺序撤销 COM 类对象。 */
    fun close() {
        if (!started.get() || !closed.compareAndSet(false, true)) return
        worker.execute {
            activator?.revoke()
            activator = null
            if (ready) {
                uninitializeWindowsRuntime()
                Ole32.INSTANCE.CoUninitialize()
            }
            ready = false
        }
        worker.shutdown()
    }

    /** COM 激活线程只解析参数，UI 更新留给 AWT 事件分发线程。 */
    private fun activated(arguments: String) {
        val target = ToastActivationTarget.parse(arguments) ?: return
        val handler = synchronized(activationLock) {
            activationHandler.also { if (it == null) pendingActivations += target }
        }
        if (handler != null) EventQueue.invokeLater { handler(target) }
    }
}
