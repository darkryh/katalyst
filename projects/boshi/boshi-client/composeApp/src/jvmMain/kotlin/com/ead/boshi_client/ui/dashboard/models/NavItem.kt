package com.ead.boshi_client.ui.dashboard.models

import com.ead.boshi_client.ui.util.models.DrawableRes

data class NavItem(
    val title : String,
    val res : DrawableRes,
    val contentDescription : String?
)