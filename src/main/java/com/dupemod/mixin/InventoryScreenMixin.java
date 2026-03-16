package com.dupemod.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends HandledScreen<PlayerScreenHandler> {
    @Unique private ButtonWidget dupeAllButton;
    private InventoryScreenMixin() { super(null, null, null); }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        int bw = 60, bh = 16;
        int bx = this.x + (this.backgroundWidth / 2) - (bw / 2);
        int by = this.y - bh - 4;
        dupeAllButton = ButtonWidget.builder(
            Text.literal("Dupe All").formatted(Formatting.GREEN),
            button -> onDupeAllPressed()
        ).dimensions(bx, by, bw, bh).build();
        this.addDrawableChild(dupeAllButton);
    }

    @Inject(method = "handledScreenTick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        if (dupeAllButton != null) {
            dupeAllButton.setX(this.x + (this.backgroundWidth / 2) - 30);
            dupeAllButton.setY(this.y - 20);
        }
    }

    @Unique
    private void onDupeAllPressed() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (mc.isIntegratedServerRunning() && mc.getServer() != null) { dupeAllSP(mc); }
        else if (mc.player.getAbilities().creativeMode) { dupeAllCR(mc); }
        else { mc.player.sendMessage(Text.literal("[DupeMod] Singleplayer/Creative only").formatted(Formatting.RED), false); }
    }

    @Unique
    private void dupeAllSP(MinecraftClient mc) {
        mc.getServer().execute(() -> {
            ServerPlayerEntity sp = mc.getServer().getPlayerManager().getPlayer(mc.player.getUniqueId());
            if (sp == null) return;
            int count = 0;
            for (int i = 0; i < 36; i++) {
                ItemStack stack = sp.getInventory().getStack(i);
                if (!stack.isEmpty()) {
                    ItemStack duped = stack.copy();
                    duped.setCount(duped.getMaxCount());
                    if (sp.getInventory().insertStack(duped)) count++;
                }
            }
            ItemStack offhand = sp.getOffHandStack();
            if (!offhand.isEmpty()) {
                ItemStack duped = offhand.copy();
                duped.setCount(duped.getMaxCount());
                if (sp.getInventory().insertStack(duped)) count++;
            }
            sp.playerScreenHandler.sendContentUpdates();
            int fc = count;
            mc.execute(() -> mc.player.sendMessage(
                Text.literal("[DupeMod] ").formatted(Formatting.GOLD)
                    .append(Text.literal("Duped " + fc + " items!").formatted(Formatting.GREEN)), false));
        });
    }

    @Unique
    private void dupeAllCR(MinecraftClient mc) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                int e = findEmpty(mc, i + 1);
                if (e == -1) break;
                ItemStack duped = stack.copy();
                duped.setCount(duped.getMaxCount());
                int ns = e < 9 ? e + 36 : e;
                mc.getNetworkHandler().sendPacket(new CreativeInventoryActionC2SPacket(ns, duped));
                count++;
            }
        }
        mc.player.sendMessage(
            Text.literal("[DupeMod] ").formatted(Formatting.GOLD)
                .append(Text.literal("Sent " + count + " packets!").formatted(Formatting.GREEN)), false);
    }

    @Unique
    private int findEmpty(MinecraftClient mc, int from) {
        for (int i = Math.max(0, from); i < 36; i++)
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        return -1;
    }
}
