package com.yongqimac.j2meplayer.ui;

import android.content.SharedPreferences;
import android.content.Context;

/**
 * 9-skin manager matching the skins from player.html
 * Each skin is a set of color values for the virtual keyboard.
 */
public class SkinManager {

    public static class Skin {
        public final String id;
        public final String name;
        public final int bgColor;       // keypad-part background
        public final int keyBg;         // key background
        public final int keyBorder;     // key border
        public final int keyFg;         // key text color
        public final int keyActiveBg;   // key active background
        public final int keySubFg;      // key subtitle color
        public final int okBg;          // OK key background

        Skin(String id, String name, int bg, int kbg, int kbd, int kfg, int kabg, int ksf, int okb) {
            this.id = id; this.name = name;
            this.bgColor = bg; this.keyBg = kbg; this.keyBorder = kbd;
            this.keyFg = kfg; this.keyActiveBg = kabg; this.keySubFg = ksf; this.okBg = okb;
        }
    }

    public static final Skin[] SKINS = {
        new Skin("",          "默认",   0xFFD0D0D0, 0xFF3A3A3A, 0xFF555555, 0xFFE0E0E0, 0xFF6A6A6A, 0xFFAAAAAA, 0xFF3A3A3A),
        new Skin("vibrant",   "活力橙", 0xFF1A0800, 0xFFE0551F, 0xFFFF7840, 0xFFFFFFFF, 0xFFFF7840, 0xFFFFB080, 0xFFC04010),
        new Skin("ocean",     "深海蓝", 0xFF080E1A, 0xFF1A3A60, 0xFF2A5A90, 0xFFC8E0FF, 0xFF2A5A90, 0xFF6090C0, 0xFF102848),
        new Skin("retro",     "复古棕", 0xFF0D0804, 0xFF7A5030, 0xFF3A2010, 0xFFF0D8B8, 0xFF4A2A18, 0xFFA08060, 0xFF887050),
        new Skin("purple",    "暗夜紫", 0xFF10081A, 0xFF382050, 0xFF583080, 0xFFE0C8FF, 0xFF583080, 0xFF9070C0, 0xFF281840),
        new Skin("neon",      "霓虹橙", 0xFF0A0A0A, 0xFF1A0A00, 0xFFFF4400, 0xFFFF8844, 0xFF2A1800, 0xFFCC6622, 0xFF220800),
        new Skin("crystal",   "3D冰蓝", 0xFF060E16, 0xFF1A3A60, 0xFF2A6AAA, 0xFF88C8FF, 0xFF0D2240, 0xFF4488AA, 0xFF224870),
        new Skin("frost",     "磨砂黑", 0xFF0D0D0D, 0x4200001E, 0x1AFFFFFF, 0xFFCCCCCC, 0x75000032, 0xFF666666, 0x70000028),
        new Skin("aurum",     "暗金紫", 0xFF0E0814, 0xFF2A1840, 0xFF8060C0, 0xFFD0B8FF, 0xFF3A2050, 0xFF7058A0, 0xFF382058),
    };

    private static final String PREF_KEY = "j2me_kbd_skin";

    public static Skin getCurrentSkin(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("j2me_player", Context.MODE_PRIVATE);
        String skinId = prefs.getString(PREF_KEY, "");
        for (Skin s : SKINS) {
            if (s.id.equals(skinId)) return s;
        }
        return SKINS[0]; // default
    }

    public static void saveSkin(Context ctx, String skinId) {
        ctx.getSharedPreferences("j2me_player", Context.MODE_PRIVATE)
                .edit().putString(PREF_KEY, skinId).apply();
    }
}
