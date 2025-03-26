package keystrokesmod.module.impl.movement;

import keystrokesmod.Raven;
import keystrokesmod.event.*;
import keystrokesmod.mixin.impl.accessor.IAccessorItemFood;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.player.Safewalk;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.*;
import net.minecraft.block.*;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

public class NoSlow extends Module {
    public SliderSetting mode;
    public static SliderSetting slowed;
    public static ButtonSetting disableBow;
    public static ButtonSetting disablePotions;
    public static ButtonSetting swordOnly;
    public static ButtonSetting vanillaSword;
    private String[] modes = new String[]{"Vanilla", "Pre", "Post", "Alpha", "Float"};
    private boolean postPlace;
    public static boolean canFloat;
    private boolean reSendConsume, requireJump;
    public static boolean noSlowing, offset, fix;
    private int ticksOffStairs = 30;
    private boolean setCancelled, didC;
    private int grounded, offsetDelay;

    public NoSlow() {
        super("NoSlow", category.movement, 0);
        this.registerSetting(new DescriptionSetting("Default is 80% motion reduction."));
        this.registerSetting(mode = new SliderSetting("Mode", 0, modes));
        this.registerSetting(slowed = new SliderSetting("Slow %", 80.0D, 0.0D, 80.0D, 1.0D));
        this.registerSetting(disableBow = new ButtonSetting("Disable bow", false));
        this.registerSetting(disablePotions = new ButtonSetting("Disable potions", false));
        this.registerSetting(swordOnly = new ButtonSetting("Sword only", false));
        this.registerSetting(vanillaSword = new ButtonSetting("Vanilla sword", false));
    }

    @Override
    public void onDisable() {
        resetFloat();
    }

    public void onUpdate() {
        if (ModuleManager.bedAura.stopAutoblock) {
            return;
        }
        postPlace = false;
        if (vanillaSword.isToggled() && Utils.holdingSword()) {
            return;
        }
        boolean apply = getSlowed() != 0.2f;
        if (!apply || !mc.thePlayer.isUsingItem()) {
            return;
        }
        switch ((int) mode.getInput()) {
            case 1:
                if (mc.thePlayer.ticksExisted % 3 == 0 && !Raven.packetsHandler.C07.get()) {
                    mc.thePlayer.sendQueue.addToSendQueue(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
                }
                break;
            case 2:
                postPlace = true;
                break;
            case 3:
                if (mc.thePlayer.ticksExisted % 3 == 0 && !Raven.packetsHandler.C07.get()) {
                    mc.thePlayer.sendQueue.addToSendQueue(new C08PacketPlayerBlockPlacement(new BlockPos(-1, -1, -1), 1, null, 0, 0, 0));
                }
                break;
            case 4:
                //
                break;
        }
    }

    @SubscribeEvent
    public void onPostMotion(PostMotionEvent e) {
        if (postPlace && mode.getInput() == 2) {
            if (mc.thePlayer.ticksExisted % 3 == 0 && !Raven.packetsHandler.C07.get()) {
                mc.thePlayer.sendQueue.addToSendQueue(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
            }
            postPlace = false;
        }
    }

    @SubscribeEvent
    public void onPostPlayerInput(PostPlayerInputEvent e) {
        handleFloatSetup();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouse(MouseEvent e) {
        handleFloatSetup();

        if (setCancelled && e.button == 1) {
            setCancelled = false;
            e.setCanceled(true);
        }
    }

    private void handleFloatSetup() {
        if (mode.getInput() != 4 || canFloat || reSendConsume || requireJump || getSlowed() == 0.2f || BlockUtils.isInteractable(mc.objectMouseOver) || !Utils.tabbedIn()) {
            return;
        }
        if (!Mouse.isButtonDown(1) || (mc.thePlayer.getHeldItem() == null || !holdingConsumable(mc.thePlayer.getHeldItem()))) {
            return;
        }
        if (!mc.thePlayer.onGround || ModuleManager.sprint.sprintFloat) {
            canFloat = true;
        }
        else {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
            setCancelled = true;
            if (!Utils.jumpDown() && !ModuleManager.bhop.isEnabled()) {
                mc.thePlayer.jump();
            }
            reSendConsume = true;
            canFloat = false;
        }
    }

    @SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if (!Utils.nullCheck()) {
            return;
        }

        if (e.getPacket() instanceof C08PacketPlayerBlockPlacement) {
            if (mode.getInput() != 4 || canFloat || reSendConsume || requireJump || getSlowed() == 0.2f || BlockUtils.isInteractable(mc.objectMouseOver) || !Utils.tabbedIn()) {
                return;
            }
            if (!Mouse.isButtonDown(1) || (mc.thePlayer.getHeldItem() == null || !holdingConsumable(mc.thePlayer.getHeldItem()))) {
                return;
            }
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPreMotion(PreMotionEvent e) {
        /*Block blockBelow = BlockUtils.getBlock(new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 1, mc.thePlayer.posZ));
        Block block = BlockUtils.getBlock(new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ));
        if (block instanceof BlockStairs || block instanceof BlockSlab && ModuleUtils.lastTickOnGround && ModuleUtils.lastTickPos1) {
            ticksOffStairs = 0;
        }
        else {
            ticksOffStairs++;
        }*/

        if (ModuleUtils.inAirTicks > 1) {
            requireJump = false;
        }
        if (ModuleManager.bedAura.stopAutoblock || mode.getInput() != 4) {
            resetFloat();
            return;
        }
        postPlace = false;
        if (!Mouse.isButtonDown(1) || (mc.thePlayer.getHeldItem() == null || !holdingConsumable(mc.thePlayer.getHeldItem()))) {
            if (mc.thePlayer.getHeldItem() != null && holdingConsumable(mc.thePlayer.getHeldItem())) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
            }
            resetFloat();
            return;
        }
        if (!floatConditions()) {
            grounded = 0;
            didC = true;
            requireJump = true;
        }
        else if (didC) {
            grounded++;
            if (grounded > 30) {
                fix = true;
            }
        }
        if (reSendConsume) {
            if (ModuleUtils.inAirTicks > 1) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                reSendConsume = false;
                canFloat = true;
            }
        }
        noSlowing = true;

        if (requireJump) {
            offset = false;
            return;
        }
        if (!canFloat) {
            return;
        }
        /*if (!reSendConsume && offsetDelay <= 2) {
            ++offsetDelay;
            return;
        }*/
        offset = true;
        e.setPosY(e.getPosY() + ModuleUtils.offsetValue);
        ModuleUtils.groundTicks = 0;
        ModuleManager.scaffold.offsetDelay = 2;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) // called last in order to apply fix
    public void onMoveInput(PrePlayerInputEvent e) {
        if (mode.getInput() == 4 && getSlowed() != 0.2f && !canFloat) {
            mc.thePlayer.movementInput.jump = false;
        }
    }

    public static float getSlowed() {
        float val = (100.0F - (float) slowed.getInput()) / 100.0F;
        if (mc.thePlayer.getHeldItem() == null || ModuleManager.noSlow == null || !ModuleManager.noSlow.isEnabled()) {
            return 0.2f;
        }
        else {
            if (swordOnly.isToggled() && !(mc.thePlayer.getHeldItem().getItem() instanceof ItemSword)) {
                return 0.2f;
            }
            if (mc.thePlayer.getHeldItem().getItem() instanceof ItemBow && disableBow.isToggled()) {
                return 0.2f;
            }
            else if (mc.thePlayer.getHeldItem().getItem() instanceof ItemPotion && !ItemPotion.isSplash(mc.thePlayer.getHeldItem().getItemDamage()) && disablePotions.isToggled()) {
                return 0.2f;
            }
            else if (fix) {
                return 0.2f;
            }
            else if (ModuleManager.killAura.blocked) {
                return val;
            }
        }
        return val;
    }

    private boolean floatConditions() {
        Block block = BlockUtils.getBlock(new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ));
        int edge = (int) Math.round((mc.thePlayer.posY % 1.0D) * 100.0D);
        if (mc.thePlayer.posY % 1 == 0) {
            return true;
        }
        if (edge < 10) {
            return true;
        }
        if (!mc.thePlayer.onGround) {
            return true;
        }
        if (block instanceof BlockSnow) {
            return true;
        }
        if (block instanceof BlockCarpet) {
            return true;
        }
        if (block instanceof BlockSlab) {
            return true;
        }
        return false;
    }

    @Override
    public String getInfo() {
        return modes[(int) mode.getInput()];
    }

    private void resetFloat() {
        reSendConsume = canFloat = noSlowing = offset = didC = fix = requireJump = false;
        grounded = offsetDelay = 0;
    }

    public static boolean hasArrows(ItemStack stack) {
        final boolean flag = mc.thePlayer.capabilities.isCreativeMode || EnchantmentHelper.getEnchantmentLevel(Enchantment.infinity.effectId, stack) > 0;
        return flag || mc.thePlayer.inventory.hasItem(Items.arrow);
    }

    double[] floatSpeedLevels = {0.2, 0.23, 0.28, 0.32, 0.37};

    double getFloatSpeed(int speedLevel) {
        double min = 0;
        if (mc.thePlayer.moveStrafing != 0 && mc.thePlayer.moveForward != 0) min = 0.003;
        if (speedLevel >= 0) {
            return floatSpeedLevels[speedLevel] - min;
        }
        return floatSpeedLevels[0] - min;
    }

    private int getSpeedLevel() {
        for (PotionEffect potionEffect : mc.thePlayer.getActivePotionEffects()) {
            if (potionEffect.getEffectName().equals("potion.moveSpeed")) {
                return potionEffect.getAmplifier() + 1;
            }
            return 0;
        }
        return 0;
    }

    private boolean holdingConsumable(ItemStack itemStack) {
        Item heldItem = itemStack.getItem();
        if (heldItem instanceof ItemFood || heldItem instanceof ItemBow && hasArrows(itemStack) || (heldItem instanceof ItemPotion && !ItemPotion.isSplash(mc.thePlayer.getHeldItem().getItemDamage())) || (heldItem instanceof ItemSword && !vanillaSword.isToggled())) {
            return true;
        }
        return false;
    }


    public static boolean holdingEdible(ItemStack stack) {
        if (stack.getItem() instanceof ItemFood && mc.thePlayer.getFoodStats().getFoodLevel() == 20) {
            ItemFood food = (ItemFood) stack.getItem();
            return ((IAccessorItemFood) food).getAlwaysEdible();
        }
        return true;
    }
}
