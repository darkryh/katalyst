package com.ead.boshi_client.ui.dashboard.models

import com.ead.boshi_client.ui.util.models.DrawableRes

data class EmailAccountNavItem(
    val id : Long,
    val title : String,
    val res : DrawableRes,
    val contentDescription : String?
) {
    fun toNavItem() : NavItem {
        return NavItem(
            title = title,
            res = res,
            contentDescription = contentDescription
        )
    }
}