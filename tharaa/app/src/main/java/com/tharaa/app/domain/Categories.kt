package com.tharaa.app.domain

data class Category(val key: String, val title: String, val emoji: String, val fund: Fund)

object Categories {
    val all = listOf(
        Category("food", "أكل", "🍔", Fund.ESSENTIALS),
        Category("groceries", "بقالة وسوق", "🛒", Fund.ESSENTIALS),
        Category("transport", "مواصلات", "🚕", Fund.ESSENTIALS),
        Category("bills", "فواتير", "💡", Fund.ESSENTIALS),
        Category("rent", "إيجار", "🏠", Fund.ESSENTIALS),
        Category("phone", "اتصالات وإنترنت", "📱", Fund.ESSENTIALS),
        Category("health", "صحة ودواء", "💊", Fund.ESSENTIALS),
        Category("work", "مصاريف الشغل", "💼", Fund.ESSENTIALS),
        Category("cafe", "مطاعم وقهوة", "☕", Fund.PERSONAL),
        Category("fun", "ترفيه وطلعات", "🎮", Fund.PERSONAL),
        Category("clothes", "ملابس", "👕", Fund.PERSONAL),
        Category("gifts", "هدايا ومناسبات", "🎁", Fund.PERSONAL),
        Category("smoking", "دخان وأرجيلة", "🚬", Fund.PERSONAL),
        Category("learning", "كتب ودورات", "📚", Fund.EDUCATION),
        Category("charity", "صدقة", "🤲", Fund.GIVING),
        Category("emergency", "طارئ", "🚑", Fund.EMERGENCY),
        Category("goal", "مصاريف هدف", "🎯", Fund.GOALS),
        Category("other", "أخرى", "📦", Fund.PERSONAL),
    )

    fun of(key: String?): Category = all.firstOrNull { it.key == key } ?: all.last()
}
