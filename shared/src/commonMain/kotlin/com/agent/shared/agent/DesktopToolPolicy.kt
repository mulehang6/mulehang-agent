package com.agent.shared.agent

import com.agent.shared.state.PermissionPreset

/**
 * 桌面本地工具的权限矩阵。
 */
object DesktopToolPolicy {
    /**
     * 只读工具默认允许。
     */
    fun canRunRead(permissionPreset: PermissionPreset): Boolean = when (permissionPreset) {
        PermissionPreset.AUTO,
        PermissionPreset.DEFAULT,
        PermissionPreset.EDIT_ALLOW,
        PermissionPreset.PLAN,
        PermissionPreset.BRAVE,
        -> true
    }

    /**
     * 当前档位是否自动放行写入工具。
     */
    fun canAutoApproveWrite(permissionPreset: PermissionPreset): Boolean = when (permissionPreset) {
        PermissionPreset.EDIT_ALLOW,
        PermissionPreset.BRAVE,
        -> true

        PermissionPreset.AUTO,
        PermissionPreset.DEFAULT,
        PermissionPreset.PLAN,
        -> false
    }

    /**
     * 当前档位是否自动放行执行工具。
     */
    fun canAutoApproveExecute(permissionPreset: PermissionPreset): Boolean = when (permissionPreset) {
        PermissionPreset.BRAVE -> true
        PermissionPreset.AUTO,
        PermissionPreset.DEFAULT,
        PermissionPreset.EDIT_ALLOW,
        PermissionPreset.PLAN,
        -> false
    }

    /**
     * 当前档位是否禁止写入。
     */
    fun isWriteDenied(permissionPreset: PermissionPreset): Boolean = permissionPreset == PermissionPreset.PLAN

    /**
     * 当前档位是否禁止执行。
     */
    fun isExecuteDenied(permissionPreset: PermissionPreset): Boolean = permissionPreset == PermissionPreset.PLAN
}
