package com.dupemod;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class DupeMod implements ClientModInitializer {
    private static DupeMod instance;
    private static final String PREFIX = "[DupeMod] ";
    private static final int OFFHAND_SLOT = 45;
    public static DupeMod getInstance() { return instance; }

    @Override
    public void onInitializeClient() {
        instance = this;
        System.out.println("[DupeMod] Loaded! Type .dupehelp");
        ClientSendMessageEvents.ALLOW_CHAT.register(this::onChatMessage);
    }

    private boolean onChatMessage(String message) {
        String cmd = message.trim().toLowerCase();
        if (cmd.equals(".dupe")) { dupeAuto(); return false; }
        if (cmd.equals(".dupe 1") || cmd.equals(".dupe creative")) { dupeCreative(); return false; }
        if (cmd.equals(".dupe 2") || cmd.equals(".dupe desync")) { dupeDesync(); return false; }
        if (cmd.equals(".dupe 3") || cmd.equals(".dupe relog")) { dupeDropRelog(); return false; }
        if (cmd.equals(".dupehelp")) { showHelp(); return false; }
        return true;
    }

    public void dupeAuto() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (!validate(player)) return;
        ItemStack offhand = player.getOffHandStack();
        if (!validateOffhand(player, offhand)) return;
        if (player.getAbilities().creativeMode) {
            sendMsg(player, "Mode: Creative", Formatting.AQUA);
            dupeCreative();
        } else if (mc.isIntegratedServerRunning() && mc.getServer() != null) {
            sendMsg(player, "Mode: Singleplayer", Formatting.AQUA);
            dupeSinglePlayer(mc, player, offhand);
        } else {
            sendMsg(player, "Mode: Server (desync)", Formatting.AQUA);
            dupeDesync();
        }
    }

    public void dupeCreative() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (!validate(player)) return;
        ItemStack offhand = player.getOffHandStack();
        if (!validateOffhand(player, offhand)) return;
        ItemStack duped = offhand.copy();
        duped.setCount(duped.getMaxCount());
        int slot = findEmptySlot(player, 0);
        if (slot == -1) { sendMsg(player, "Inventory full!", Formatting.RED); return; }
        mc.getNetworkHandler().sendPacket(new CreativeInventoryActionC2SPacket(localToNetworkSlot(slot), duped));
        sendMsg(player, "Creative sent! " + itemInfo(duped), Formatting.GREEN);
    }

    public void dupeDesync() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (!validate(player)) return;
        ItemStack offhand = player.getOffHandStack();
        if (!validateOffhand(player, offhand)) return;
        ScreenHandler handler = player.playerScreenHandler;
        int syncId = handler.syncId;
        int rev = handler.getRevision();
        ItemStack duped = offhand.copy();
        int emptySlot = findEmptySlot(player, 0);
        if (emptySlot == -1) { sendMsg(player, "Need empty slot!", Formatting.RED); return; }
        int netEmpty = localToNetworkSlot(emptySlot);
        Int2ObjectMap<ItemStack> m1 = new Int2ObjectOpenHashMap<>();
        m1.put(OFFHAND_SLOT, ItemStack.EMPTY);
        mc.getNetworkHandler().sendPacket(new ClickSlotC2SPacket(syncId, rev, OFFHAND_SLOT, 0, SlotActionType.PICKUP, duped.copy(), m1));
        Int2ObjectMap<ItemStack> m2 = new Int2ObjectOpenHashMap<>();
        m2.put(netEmpty, duped.copy());
        mc.getNetworkHandler().sendPacket(new ClickSlotC2SPacket(syncId, rev+1, netEmpty, 0, SlotActionType.PICKUP, ItemStack.EMPTY, m2));
        Int2ObjectMap<ItemStack> m3 = new Int2ObjectOpenHashMap<>();
        m3.put(OFFHAND_SLOT, duped.copy());
        mc.getNetworkHandler().sendPacket(new ClickSlotC2SPacket(syncId, rev+2, OFFHAND_SLOT, 0, SlotActionType.PICKUP, ItemStack.EMPTY, m3));
        sendMsg(player, "Desync sent! " + itemInfo(duped), Formatting.YELLOW);
    }

    public void dupeDropRelog() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (!validate(player)) return;
        ItemStack offhand = player.getOffHandStack();
        if (!validateOffhand(player, offhand)) return;
        ScreenHandler handler = player.playerScreenHandler;
        Int2ObjectMap<ItemStack> mods = new Int2ObjectOpenHashMap<>();
        mods.put(OFFHAND_SLOT, ItemStack.EMPTY);
        mc.getNetworkHandler().sendPacket(new ClickSlotC2SPacket(handler.syncId, handler.getRevision(), OFFHAND_SLOT, 1, SlotActionType.THROW, ItemStack.EMPTY, mods));
        sendMsg(player, "Dropped! Disconnecting...", Formatting.YELLOW);
        new Thread(() -> {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            mc.execute(() -> {
                if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() != null)
                    mc.getNetworkHandler().getConnection().disconnect(Text.literal("DupeMod relog"));
            });
        }).start();
    }

    public void dupeSinglePlayer(MinecraftClient mc, ClientPlayerEntity player, ItemStack offhand) {
        ItemStack duped = offhand.copy();
        duped.setCount(duped.getMaxCount());
        mc.getServer().execute(() -> {
            ServerPlayerEntity sp = mc.getServer().getPlayerManager().getPlayer(player.getUniqueId());
            if (sp == null) return;
            boolean ok = sp.getInventory().insertStack(duped.copy());
            sp.playerScreenHandler.sendContentUpdates();
            mc.execute(() -> {
                if (ok) sendMsg(player, "Duped! " + itemInfo(duped), Formatting.GREEN);
                else sendMsg(player, "Inventory full!", Formatting.RED);
            });
        });
    }

    private void showHelp() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null) return;
        player.sendMessage(Text.literal("====== DupeMod Help ======"), false);
        player.sendMessage(Text.literal(".dupe - auto select"), false);
        player.sendMessage(Text.literal(".dupe 1 - creative"), false);
        player.sendMessage(Text.literal(".dupe 2 - desync"), false);
        player.sendMessage(Text.literal(".dupe 3 - drop+relog"), false);
        player.sendMessage(Text.literal("Dupe All - button in inventory (E)"), false);
    }

    public boolean validate(ClientPlayerEntity player) {
        return player != null && MinecraftClient.getInstance().getNetworkHandler() != null;
    }
    public boolean validateOffhand(ClientPlayerEntity player, ItemStack offhand) {
        if (offhand == null || offhand.isEmpty()) { sendMsg(player, "Offhand empty! Press F", Formatting.RED); return false; }
        return true;
    }
    public int findEmptySlot(ClientPlayerEntity player, int from) {
        for (int i = Math.max(0, from); i < 36; i++) if (player.getInventory().getStack(i).isEmpty()) return i;
        return -1;
    }
    public int localToNetworkSlot(int slot) { return slot < 9 ? slot + 36 : slot; }
    public String itemInfo(ItemStack s) { return s.getName().getString() + " x" + s.getCount(); }
    public void sendMsg(ClientPlayerEntity player, String text, Formatting color) {
        player.sendMessage(Text.literal(PREFIX).formatted(Formatting.GOLD).append(Text.literal(text).formatted(color)), false);
    }
}
