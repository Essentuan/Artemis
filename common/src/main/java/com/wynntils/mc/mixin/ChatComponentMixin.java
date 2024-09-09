/*
 * Copyright © Wynntils 2024.
 * This file is released under LGPLv3. See LICENSE for full license details.
 */
package com.wynntils.mc.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.wynntils.core.WynntilsMod;
import com.wynntils.core.components.Managers;
import com.wynntils.features.chat.ChatTimestampFeature;
import com.wynntils.mc.event.AddGuiMessageLineEvent;
import com.wynntils.mc.extension.ChatComponentExtension;
import com.wynntils.mc.extension.GuiMessageExtension;
import com.wynntils.mc.extension.GuiMessageLineExtension;
import com.wynntils.utils.type.Pair;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin implements ChatComponentExtension {
    @Shadow
    @Final
    private List<GuiMessage> allMessages;

    @Shadow
    private void refreshTrimmedMessages() {}

    @Shadow
    @Final
    private List<GuiMessage.Line> trimmedMessages;

    @Shadow
    public abstract double getScale();

    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private ChatTimestampFeature timestampFeature;

    @Unique
    private int timestampWidth = 0;

    @WrapOperation(
            method = "addMessageToDisplayQueue",
            at = @At(value = "INVOKE", target = "Ljava/util/List;add(ILjava/lang/Object;)V"))
    private void addMessageToDisplayQueue(
            List<GuiMessage.Line> instance,
            int i,
            Object line,
            Operation<Void> original,
            @Local(name = "j") int index,
            @Local(name = "message") GuiMessage message) {
        GuiMessageExtension messageExtension = (GuiMessageExtension) (Object) message;

        ((GuiMessageLineExtension) line).setCreated(messageExtension.getCreated());

        WynntilsMod.postEvent(new AddGuiMessageLineEvent((GuiMessage.Line) line, index));

        original.call(trimmedMessages, i, line);
    }

    @Override
    public void deleteMessage(Component component) {
        allMessages.removeIf(guiMessage -> guiMessage.content().equals(component));
        refreshTrimmedMessages();
    }

    @Unique
    private ChatTimestampFeature getTimestampFeature() {
        if (timestampFeature == null) {
            timestampFeature = Managers.Feature.getFeatureInstance(ChatTimestampFeature.class);
        }

        return timestampFeature;
    }

    @Inject(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/util/profiling/ProfilerFiller;push(Ljava/lang/String;)V"))
    private void setupRender(
            GuiGraphics guiGraphics, int tickCount, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        timestampWidth = 0;

        if (!getTimestampFeature().isEnabled()) {
            return;
        }

        for (GuiMessage.Line line : trimmedMessages) {
            GuiMessageLineExtension extension = (GuiMessageLineExtension) (Object) line;
            Optional<Pair<Component, Integer>> timestamp = extension.getTimestamp();

            if (timestamp.isEmpty()) continue;

            timestampWidth = Math.max(timestampWidth, timestamp.get().b());
        }
    }

    @ModifyArg(
            method = "render",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0),
            index = 0)
    private float offsetChatBox(float x) {
        if (timestampWidth == 0) return x;

        return x + 4 + timestampWidth;
    }

    @WrapMethod(method = "screenToChatX")
    private double screenToChatX(double x, Operation<Double> original) {
        if (timestampWidth == 0) return original.call(x);

        return original.call(x - (4 + timestampWidth));
    }

    @Inject(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", ordinal = 0))
    private void renderTimestampBackground(
            GuiGraphics guiGraphics,
            int tickCount,
            int mouseX,
            int mouseY,
            boolean focused,
            CallbackInfo ci,
            @Local(name = "x") int x,
            @Local(name = "o") int o,
            @Local(name = "v") int v) {
        if (timestampWidth == 0) return;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate((float) -(timestampWidth + 4), 0f, 0f);

        guiGraphics.fill(-2, x - o, timestampWidth - 2, x, v << 24);

        guiGraphics.pose().popPose();
    }

    @Inject(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"))
    private void renderTimestamp(
            GuiGraphics guiGraphics,
            int tickCount,
            int mouseX,
            int mouseY,
            boolean focused,
            CallbackInfo ci,
            @Local GuiMessage.Line line,
            @Local(name = "y") int y,
            @Local(name = "u") int u,
            @Local(name = "f") float scale) {
        if (timestampWidth == 0) return;

        GuiMessageLineExtension extension = (GuiMessageLineExtension) (Object) line;

        if (extension.getTimestamp().isEmpty()) return;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(-(extension.getTimestamp().get().b() + 4f), 0f, 0f);

        guiGraphics.drawString(
                this.minecraft.font, extension.getTimestamp().get().a(), 0, y, 16777215 + (u << 24));

        guiGraphics.pose().popPose();
    }
}
