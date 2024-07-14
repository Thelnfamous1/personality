package io.blodhgarm.personality.utils;

import com.mojang.authlib.GameProfile;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

public class Constants {

    //Credit to ObeyTheFist on SkinIndex [Link: https://www.minecraftskins.com/skin/9965036/requests/]
    public static final Identifier MISSING_SKIN_TEXTURE_ID = new Identifier("personality", "textures/skins/question_mark_2.png");

    public static final int WEEK_IN_MILLISECONDS = 604_800_000;
    public static final int HOUR_IN_MILLISECONDS =   3_600_000;

    public static final GameProfile ERROR_PROFILE = new GameProfile(Util.NIL_UUID, "ERROR");
    public static final GameProfile NONE_PROFILE = new GameProfile(Util.NIL_UUID, "NONE");

    public static Formatting CHARACTER_FORMATTING;
    public static boolean shouldFilterUnimportantComponents = false;

    public static boolean isErrored(GameProfile profile){
        return ERROR_PROFILE.getName().equals(profile.getName());
    }
}
