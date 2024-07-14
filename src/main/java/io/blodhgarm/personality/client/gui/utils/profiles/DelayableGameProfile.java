package io.blodhgarm.personality.client.gui.utils.profiles;

import com.mojang.authlib.GameProfile;

import org.jetbrains.annotations.Nullable;
import java.util.Objects;
import java.util.function.Consumer;

public class DelayableGameProfile extends WrappedGameProfile implements Runnable {
    @Nullable
    private final Runnable runnable;

    public DelayableGameProfile(GameProfile profile, @Nullable Runnable runnable) {
        super(profile);

        this.runnable = runnable;
    }

    public boolean isRunnable() {
        return runnable != null;
    }

    public Runnable wrapRunnable(Consumer<GameProfile> consumer) {
        return () -> {
            if (isRunnable()) runnable.run();

            consumer.accept(this);
        };
    }

    @Override
    public void run() {
        if (isRunnable()) this.runnable.run();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (DelayableGameProfile) obj;
        return Objects.equals(this.wrappedProfile, that.wrappedProfile) &&
                Objects.equals(this.runnable, that.runnable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(wrappedProfile, runnable);
    }

    @Override
    public String toString() {
        return "GameProfileFiller[" +
                "profile=" + wrappedProfile + ", " +
                "runnable=" + runnable + ']';
    }

}
