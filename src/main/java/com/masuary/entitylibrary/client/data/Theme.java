package com.masuary.entitylibrary.client.data;

public record Theme(
        int panelBackground,
        int panelBorder,
        int titleColor,
        int primaryText,
        int secondaryText,
        int statsText,
        int commandText,
        int errorText,
        int hintText,
        int separatorColor,
        int listSelectedText,
        int listUnselectedText,
        int listSelectedIdText,
        int listUnselectedIdText,
        int listDivider,
        int countText,
        int noSelectionText,
        int favoriteStarColor
) {
    public static final Theme DARK = new Theme(
            0xCC0A0A0A,
            0xFF333333,
            0xFFFFFF,
            0xFFFFFF,
            0x808080,
            0xC0C0C0,
            0x55FF55,
            0xFF8080,
            0x606060,
            0xFF333333,
            0xFFFFFF,
            0xE0E0E0,
            0xA0A0A0,
            0x808080,
            0xFF333333,
            0x808080,
            0x808080,
            0xFFD700
    );

    public static final Theme LIGHT = new Theme(
            0xEEE8E8E8,
            0xFF999999,
            0x222222,
            0x222222,
            0x555555,
            0x444444,
            0x006600,
            0xCC0000,
            0x777777,
            0xFF999999,
            0x000000,
            0x333333,
            0x555555,
            0x777777,
            0xFF999999,
            0x666666,
            0x666666,
            0xDAA520
    );
}
