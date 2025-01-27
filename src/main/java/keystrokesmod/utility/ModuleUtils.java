package keystrokesmod.utility;

import keystrokesmod.event.NoEventPacketEvent;
import keystrokesmod.event.PreMotionEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.module.impl.movement.LongJump;
import keystrokesmod.module.impl.render.HUD;
import net.minecraft.client.Minecraft;
import keystrokesmod.module.ModuleManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

public class ModuleUtils {
    private final Minecraft mc;

    public ModuleUtils(Minecraft mc) {
        this.mc = mc;
    }

    public static boolean isBreaking;
    public static boolean threwFireball, threwFireballLow;
    private int isBreakingTick;
    public static long MAX_EXPLOSION_DIST_SQ = 10;
    private long FIREBALL_TIMEOUT = 500L, fireballTime = 0;
    public static int inAirTicks, groundTicks;
    public static int fadeEdge;
    public static int lastFaceDifference;
    private int lastFace;
    public static float offsetValue = 1E-14F;
    public static boolean isAttacking;
    private int attackingTicks;
    private int unTargetTicks;
    public static int profileTicks = -1;

    public static boolean isBlocked;

    @SubscribeEvent
    public void onSendPacketNoEvent(NoEventPacketEvent e) {
        if (!Utils.nullCheck()) {
            return;
        }

        // isBlocked
        EntityLivingBase g = Utils.raytrace(3);

        if (e.getPacket() instanceof C08PacketPlayerBlockPlacement && Utils.holdingSword() && !BlockUtils.isInteractable(mc.objectMouseOver) && !isBlocked) {
            isBlocked = true;
        }
        else if (e.getPacket() instanceof C07PacketPlayerDigging && isBlocked) {
            C07PacketPlayerDigging c07 = (C07PacketPlayerDigging) e.getPacket();
            String edger;
            edger = String.valueOf(c07.getStatus());
            if (Objects.equals(edger, "RELEASE_USE_ITEM")) {
                isBlocked = false;
            }
        }
        else if (e.getPacket() instanceof C09PacketHeldItemChange && isBlocked) {
            isBlocked = false;
        }

        //
         // isAttacking

            if (e.getPacket() instanceof C02PacketUseEntity) {
                isAttacking = true;
                attackingTicks = 5;
            }

        //

    }

    @SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if (!Utils.nullCheck()) {
            return;
        }

        // isBlocked
        EntityLivingBase g = Utils.raytrace(3);

        if (e.getPacket() instanceof C08PacketPlayerBlockPlacement && Utils.holdingSword() && !BlockUtils.isInteractable(mc.objectMouseOver) && !isBlocked) {
            isBlocked = true;
        }
        else if (e.getPacket() instanceof C07PacketPlayerDigging && isBlocked) {
            C07PacketPlayerDigging c07 = (C07PacketPlayerDigging) e.getPacket();
            String edger;
            edger = String.valueOf(c07.getStatus());
            if (Objects.equals(edger, "RELEASE_USE_ITEM")) {
                isBlocked = false;
            }
        }
        else if (e.getPacket() instanceof C09PacketHeldItemChange && isBlocked) {
            isBlocked = false;
        }

        //
         // isAttacking

            if (e.getPacket() instanceof C02PacketUseEntity) {
                isAttacking = true;
                attackingTicks = 5;
            }

        //






        if (e.getPacket() instanceof C07PacketPlayerDigging) {
            isBreaking = true;
        }

        if (e.getPacket() instanceof C08PacketPlayerBlockPlacement && Utils.holdingFireball()) {
            if (Utils.keybinds.isMouseDown(1)) {
                fireballTime = System.currentTimeMillis();
                threwFireball = true;
                if (mc.thePlayer.rotationPitch > 50F) {
                    threwFireballLow = true;
                }
            }
        }

        if (e.getPacket() instanceof C08PacketPlayerBlockPlacement && Utils.scaffoldDiagonal(false)) {
            if (((C08PacketPlayerBlockPlacement) e.getPacket()).getPlacedBlockDirection() != 1) {
                int currentFace = ((C08PacketPlayerBlockPlacement) e.getPacket()).getPlacedBlockDirection();

                if (currentFace == lastFace) {
                    lastFaceDifference++;
                }
                else {
                   lastFaceDifference = 0;
                }

                lastFace = currentFace;
            }
        }

    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {

        profileTicks++;

        if (isAttacking) {
            if (attackingTicks <= 0) {
                isAttacking = false;
            }
            else {
                --attackingTicks;
            }
        }

        if (LongJump.slotReset && ++LongJump.slotResetTicks >= 2) {
            LongJump.stopModules = false;
            LongJump.slotResetTicks = 0;
            LongJump.slotReset = false;
        }

        if (fireballTime > 0 && (System.currentTimeMillis() - fireballTime) > FIREBALL_TIMEOUT / 3) {
            threwFireballLow = false;
            ModuleManager.velocity.disableVelo = false;
        }

        if (fireballTime > 0 && (System.currentTimeMillis() - fireballTime) > FIREBALL_TIMEOUT) {
            threwFireball = threwFireballLow = false;
            fireballTime = 0;
            ModuleManager.velocity.disableVelo = false;
        }

        if (isBreaking && ++isBreakingTick >= 1) {
            isBreaking = false;
            isBreakingTick = 0;
        }

        if (ModuleManager.killAura.justUnTargeted) {
            if (++unTargetTicks >= 2) {
                unTargetTicks = 0;
                ModuleManager.killAura.justUnTargeted = false;
            }
        }


    }

    @SubscribeEvent
    public void onPreMotion(PreMotionEvent e) {
        int simpleY = (int) Math.round((e.posY % 1) * 10000);

        if (inAirTicks <= 20) {
            inAirTicks = mc.thePlayer.onGround ? 0 : ++inAirTicks;
        }
        else {
            inAirTicks = 19;
        }
        groundTicks = !mc.thePlayer.onGround ? 0 : ++groundTicks;

        // 7 tick needs to always finish the motion or itll lag back
        if (!ModuleManager.bhop.isEnabled() && ModuleManager.bhop.mode.getInput() == 3 && ModuleManager.bhop.didMove) {

            if (mc.thePlayer.hurtTime == 0) {
                switch (simpleY) {
                    case 4200:
                        mc.thePlayer.motionY = 0.39;
                        break;
                    case 1138:
                        mc.thePlayer.motionY = mc.thePlayer.motionY - 0.13;
                        ModuleManager.bhop.lowhop = false;
                        ModuleManager.bhop.didMove = false;
                        Utils.print("7 tick");
                        break;
                    /*case 2031:
                        mc.thePlayer.motionY = mc.thePlayer.motionY - 0.2;
                        didMove = false;
                        break;*/
                }
            }
        }

        if (ModuleManager.bhop.setRotation) {
            if (!ModuleManager.killAura.isTargeting && !ModuleManager.scaffold.isEnabled) {
                float yaw = mc.thePlayer.rotationYaw - 55;
                e.setYaw(yaw);
            }
            if (mc.thePlayer.onGround) {
                ModuleManager.bhop.setRotation = false;
            }
        }

        if (ModuleManager.scaffold.canBlockFade && !ModuleManager.scaffold.isEnabled && ++fadeEdge >= 45) {
            ModuleManager.scaffold.canBlockFade = false;
            fadeEdge = 0;
            ModuleManager.scaffold.highlight.clear();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!ModuleManager.scaffold.canBlockFade) {
            return;
        }
        if (!Utils.nullCheck() || !ModuleManager.scaffold.highlightBlocks.isToggled() || ModuleManager.scaffold.highlight.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<BlockPos, Timer>> iterator = ModuleManager.scaffold.highlight.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Timer> entry = iterator.next();
            if (entry.getValue() == null) {
                entry.setValue(new Timer(750));
                entry.getValue().start();
            }
            int alpha = entry.getValue() == null ? 210 : 210 - entry.getValue().getValueInt(0, 210, 1);
            if (alpha == 0) {
                iterator.remove();
                continue;
            }
            RenderUtils.renderBlock(entry.getKey(), Utils.mergeAlpha(Theme.getGradient((int) HUD.theme.getInput(), 0), alpha), true, false);
        }
    }
}