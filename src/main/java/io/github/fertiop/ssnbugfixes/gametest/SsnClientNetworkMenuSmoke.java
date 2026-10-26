package io.github.fertiop.ssnbugfixes.gametest;

import com.lothrazar.storagenetwork.block.request.ContainerNetworkCraftingTable;
import com.lothrazar.storagenetwork.registry.SsnRegistry;
import io.github.fertiop.ssnbugfixes.SsnBugFixes;

import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side acceptance fixture ONLY. Real Forge multiplayer handshake,
 * actual server-side BlockRequest.use, NetworkHooks.openScreen, client
 * container packet, menu creation, and screen transition are all exercised.
 * This test class is excluded from the distributable JAR.
 */
@Mod.EventBusSubscriber(modid = SsnBugFixes.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SsnClientNetworkMenuSmoke {
    private static final BlockPos REQUEST = new BlockPos(0, 80, 0);
    private static boolean opened;
    private static int ticks;
    private static int attempts;

    private SsnClientNetworkMenuSmoke() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent tick) {
        if (!Boolean.getBoolean("ssnbugfixes.clientJoinGui")
                || opened || tick.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || minecraft.gameMode == null || minecraft.getConnection() == null) {
            return;
        }
        ticks++;

        if (minecraft.player.containerMenu instanceof ContainerNetworkCraftingTable
                && minecraft.screen instanceof AbstractContainerScreen<?>) {
            try {
                Files.writeString(Path.of("multiplayer-gui-opened.txt"),
                        "PASS: real client opened SSN request terminal "
                        + "through server-side NetworkHooks.openScreen\n");
                System.out.println("SSN_MULTIPLAYER_GUI PASS: client received real SSN menu and screen");
                opened = true;
            }
            catch (Exception e) {
                throw new IllegalStateException("Could not record SSN GUI smoke PASS", e);
            }
            return;
        }

        if (ticks % 35 != 0 || attempts >= 10) {
            return;
        }
        if (!minecraft.level.getBlockState(REQUEST).is(SsnRegistry.Blocks.REQUEST.get())) {
            return;
        }
        if (minecraft.player.distanceToSqr(Vec3.atCenterOf(REQUEST)) > 36.0) {
            return;
        }
        attempts++;
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(REQUEST), Direction.UP, REQUEST, false);
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
    }
}
