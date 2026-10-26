package io.github.fertiop.ssnbugfixes.gametest;

import com.lothrazar.storagenetwork.item.ItemBuilder;
import com.lothrazar.storagenetwork.registry.SsnRegistry;
import io.github.fertiop.ssnbugfixes.SsnBugFixes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only, TEST-ONLY Forge entry point for an actual headless Minecraft
 * client render-thread tooltip check. Production JAR excludes gametest/**.
 *
 * It runs only with -Dssnbugfixes.clientSmoke=true (set by ForgeGradle), and
 * uses ItemStack.getTooltipLines(null) which is how vanilla constructs item
 * descriptions for search-tree initialization while there is no Player/Level.
 */
@Mod.EventBusSubscriber(modid = SsnBugFixes.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SsnClientTooltipSmoke {
    private static boolean finished;

    private SsnClientTooltipSmoke() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent tick) {
        if (!Boolean.getBoolean("ssnbugfixes.clientSmoke")
                || finished || tick.phase != TickEvent.Phase.END) {
            return;
        }
        finished = true;
        Path marker = Path.of("client-tooltip-smoke-ok.txt");
        try {
            ItemStack withState = new ItemStack(SsnRegistry.Items.BUILDER_REMOTE.get());
            ItemBuilder.setBlockState(withState, Blocks.STONE.defaultBlockState());

            // Call the REAL vanilla/Forge client item-tooltip production path.
            List<Component> lines = withState.getTooltipLines(null, TooltipFlag.Default.NORMAL);
            if (lines.size() < 2) {
                throw new AssertionError("Builder tooltip missing expected descriptive lines: " + lines);
            }

            // Call the SSN method directly as a focused negative regression.
            List<Component> direct = new ArrayList<>();
            ((ItemBuilder) withState.getItem()).appendHoverText(
                    withState, null, direct, TooltipFlag.Default.NORMAL);
            if (direct.size() < 2) {
                throw new AssertionError("Builder remote did not preserve fallback tooltip text");
            }

            ItemStack withoutState = new ItemStack(SsnRegistry.Items.BUILDER_REMOTE.get());
            List<Component> unconfigured = withoutState.getTooltipLines(
                    null, TooltipFlag.Default.NORMAL);
            if (unconfigured.isEmpty()) {
                throw new AssertionError("Normal empty-builder tooltip unexpectedly disappeared");
            }

            Files.writeString(marker,
                    "PASS: vanilla ItemStack.getTooltipLines(null) and ItemBuilder.appendHoverText"
                            + " survived with/without blockstate tag on real Minecraft client\n");
            System.out.println("SSN_CLIENT_TOOLTIP_SMOKE PASS");
        }
        catch (Throwable error) {
            error.printStackTrace(System.err);
            System.err.println("SSN_CLIENT_TOOLTIP_SMOKE FAIL: " + error);
            throw new RuntimeException("SSN client tooltip test failed", error);
        }
        finally {
            Minecraft.getInstance().stop();
        }
    }
}
