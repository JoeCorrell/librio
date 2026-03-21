package com.librio.ui.theme

data class ThemeCategory(
    val title: String,
    val themes: List<AppTheme>
)

fun themeCategoriesByColor(): List<ThemeCategory> {
    return listOf(
        ThemeCategory("Themes", listOf(
            AppTheme.PARCHMENT,
            AppTheme.ROSEWOOD,
            AppTheme.SAGE,
            AppTheme.SAND,
            AppTheme.LAVENDER,
            AppTheme.SKY,
            AppTheme.PEACH,
            AppTheme.MINT
        )),
        ThemeCategory("Custom", listOf(AppTheme.CUSTOM))
    )
}
