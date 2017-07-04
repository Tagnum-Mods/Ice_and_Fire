package com.github.alexthe666.iceandfire.entity;

import com.github.alexthe666.iceandfire.IceAndFire;
import com.github.alexthe666.iceandfire.core.ModItems;
import com.github.alexthe666.iceandfire.core.ModKeys;
import com.github.alexthe666.iceandfire.entity.ai.*;
import com.github.alexthe666.iceandfire.enums.EnumHippogryphTypes;
import com.github.alexthe666.iceandfire.message.MessageDragonControl;
import com.github.alexthe666.iceandfire.message.MessageHippogryphArmor;
import com.google.common.base.Predicate;
import net.ilexiconn.llibrary.server.animation.Animation;
import net.ilexiconn.llibrary.server.animation.AnimationHandler;
import net.ilexiconn.llibrary.server.animation.IAnimatedEntity;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerHorseChest;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class EntityHippogryph extends EntityTameable implements IAnimatedEntity {

    private static final int FLIGHT_CHANCE_PER_TICK = 600;
    private static final DataParameter<Integer> VARIANT = EntityDataManager.<Integer>createKey(EntityHippogryph.class, DataSerializers.VARINT);
    private static final DataParameter<Boolean> SADDLE = EntityDataManager.<Boolean>createKey(EntityHippogryph.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Integer> ARMOR = EntityDataManager.<Integer>createKey(EntityHippogryph.class, DataSerializers.VARINT);
    private static final DataParameter<Boolean> CHESTED = EntityDataManager.<Boolean>createKey(EntityHippogryph.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Boolean> HOVERING = EntityDataManager.<Boolean>createKey(EntityHippogryph.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Boolean> FLYING = EntityDataManager.<Boolean>createKey(EntityHippogryph.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Byte> CONTROL_STATE = EntityDataManager.createKey(EntityHippogryph.class, DataSerializers.BYTE);

    public ContainerHorseChest hippogryphInventory;
    @SideOnly(Side.CLIENT)
    public RollBuffer roll_buffer;
    public static Animation ANIMATION_EAT;
    public static Animation ANIMATION_SPEAK;
    public static Animation ANIMATION_SCRATCH;
    public static Animation ANIMATION_BITE;
    public float sitProgress;
    public float hoverProgress;
    public float flyProgress;
    private boolean isSitting;
    private boolean isHovering;
    private boolean isFlying;
    public int spacebarTicks;
    private int animationTick;
    private Animation currentAnimation;
    private int flyTicks;
    private int hoverTicks;
    public BlockPos airTarget;

    public EntityHippogryph(World worldIn) {
        super(worldIn);
        initHippogryphInv();
        if (FMLCommonHandler.instance().getSide().isClient()) {
            roll_buffer = new RollBuffer();
        }
    }

    @Override
    protected void initEntityAI() {
        this.tasks.addTask(1, new EntityAISwimming(this));
        this.tasks.addTask(2, this.aiSit = new EntityAISit(this));
        this.tasks.addTask(3, new HippogryphAIAttackMelee(this, 1.5D, true));
        this.tasks.addTask(4, new EntityAIMate(this, 1.0D));
        this.tasks.addTask(5, new EntityAITempt(this, 1.0D, Items.RABBIT, false));
        this.tasks.addTask(5, new EntityAITempt(this, 1.0D, Items.COOKED_RABBIT, false));
        this.tasks.addTask(6, new HippogryphAIAirTarget(this));
        this.tasks.addTask(7, new HippogryphAIWander(this, 1.0D));
        this.tasks.addTask(8, new EntityAIWatchClosest(this, EntityLivingBase.class, 6.0F));
        this.tasks.addTask(8, new EntityAILookIdle(this));
        this.targetTasks.addTask(1, new EntityAIOwnerHurtByTarget(this));
        this.targetTasks.addTask(2, new EntityAIOwnerHurtTarget(this));
        this.targetTasks.addTask(3, new EntityAIHurtByTarget(this, true, new Class[0]));
        this.targetTasks.addTask(4, new HippogryphAITarget(this, EntityLivingBase.class, false, new Predicate<Entity>() {
            @Override
            public boolean apply(@Nullable Entity entity) {
                return entity instanceof EntityLivingBase;
            }
        }));
        this.targetTasks.addTask(5, new HippogryphAITargetItems(this, false));

    }
    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(VARIANT, 0);
        this.dataManager.register(ARMOR, 0);
        this.dataManager.register(SADDLE, false);
        this.dataManager.register(CHESTED, false);
        this.dataManager.register(HOVERING, false);
        this.dataManager.register(FLYING, false);
        this.dataManager.register(CONTROL_STATE, (byte)0);

    }

    private void initHippogryphInv() {
        ContainerHorseChest animalchest = this.hippogryphInventory;
        this.hippogryphInventory = new ContainerHorseChest("hippogryphInventory", 17);
        this.hippogryphInventory.setCustomName(this.getName());
        if (animalchest != null) {
            int i = Math.min(animalchest.getSizeInventory(), this.hippogryphInventory.getSizeInventory());
            for (int j = 0; j < i; ++j) {
                ItemStack itemstack = animalchest.getStackInSlot(j);
                if (!itemstack.isEmpty()) {
                    this.hippogryphInventory.setInventorySlotContents(j, itemstack.copy());
                }
            }
        }
        ItemStack saddle = animalchest.getStackInSlot(0);
        ItemStack chest = animalchest.getStackInSlot(1);

        if (world.isRemote) {
            IceAndFire.NETWORK_WRAPPER.sendToServer(new MessageHippogryphArmor(this.getEntityId(), 0, saddle != null && saddle.getItem() == Items.SADDLE && !saddle.isEmpty() ? 1 : 0));
            IceAndFire.NETWORK_WRAPPER.sendToServer(new MessageHippogryphArmor(this.getEntityId(), 1, chest != null && chest.getItem() == Item.getItemFromBlock(Blocks.CHEST) && !chest.isEmpty() ? 1 : 0));
            IceAndFire.NETWORK_WRAPPER.sendToServer(new MessageHippogryphArmor(this.getEntityId(), 2, this.getIntFromArmor(animalchest.getStackInSlot(1))));
        }
    }

    private void updateDragonSlots() {
        ItemStack saddle = this.hippogryphInventory.getStackInSlot(0);
        ItemStack chest = this.hippogryphInventory.getStackInSlot(1);
        this.setSaddled(saddle != null && saddle.getItem() == Items.SADDLE && !saddle.isEmpty());
        this.setChested(chest != null && chest.getItem() == Item.getItemFromBlock(Blocks.CHEST) && !chest.isEmpty());
        this.setArmor(getIntFromArmor(this.hippogryphInventory.getStackInSlot(2)));

    }

    private int getIntFromArmor(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() != null && stack.getItem() == ModItems.iron_hippogryph_armor) {
            return 1;
        }
        if (!stack.isEmpty() && stack.getItem() != null && stack.getItem() == ModItems.gold_hippogryph_armor) {
            return 2;
        }
        if (!stack.isEmpty() && stack.getItem() != null && stack.getItem() == ModItems.diamond_hippogryph_armor) {
            return 3;
        }
        return 0;
    }

    public void openGUI(EntityPlayer playerEntity) {
        if (!this.world.isRemote && (!this.isBeingRidden() || this.isPassenger(playerEntity))) {
            playerEntity.openGui(IceAndFire.INSTANCE, 4, this.world, this.getEntityId(), 0, 0);
        }
    }

    public boolean up() {
        return (dataManager.get(CONTROL_STATE) & 1) == 1;
    }

    public boolean down() {
        return (dataManager.get(CONTROL_STATE) >> 1 & 1) == 1;
    }

    public boolean attack() {
        return (dataManager.get(CONTROL_STATE) >> 2 & 1) == 1;
    }

    public boolean dismount() {
        return (dataManager.get(CONTROL_STATE) >> 3 & 1) == 1;
    }

    public void up(boolean up) {
        setStateField(0, up);
    }

    public void down(boolean down) {
        setStateField(1, down);
    }

    public void attack(boolean attack) {
        setStateField(2, attack);
    }

    public void dismount(boolean dismount) {
        setStateField(3, dismount);
    }

    private void setStateField(int i, boolean newState) {
        byte prevState = dataManager.get(CONTROL_STATE);
        if (newState) {
            dataManager.set(CONTROL_STATE, (byte) (prevState | (1 << i)));
        } else {
            dataManager.set(CONTROL_STATE, (byte) (prevState & ~(1 << i)));
        }
    }

    public byte getControlState() {
        return dataManager.get(CONTROL_STATE);
    }

    public void setControlState(byte state) {
        dataManager.set(CONTROL_STATE, state);
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        compound.setInteger("Variant", this.getVariant());
        compound.setBoolean("Chested", this.isChested());
        compound.setBoolean("Saddled", this.isSaddled());
        compound.setBoolean("Hovering", this.isHovering());
        compound.setBoolean("Flying", this.isFlying());
        compound.setInteger("Armor", this.getArmor());
        if (hippogryphInventory != null) {
            NBTTagList nbttaglist = new NBTTagList();
            for (int i = 0; i < this.hippogryphInventory.getSizeInventory(); ++i) {
                ItemStack itemstack = this.hippogryphInventory.getStackInSlot(i);
                if (!itemstack.isEmpty()) {
                    NBTTagCompound nbttagcompound = new NBTTagCompound();
                    nbttagcompound.setByte("Slot", (byte) i);
                    itemstack.writeToNBT(nbttagcompound);
                    nbttaglist.appendTag(nbttagcompound);
                }
            }
            compound.setTag("Items", nbttaglist);
        }
        if (this.getCustomNameTag() != null && !this.getCustomNameTag().isEmpty()) {
            compound.setString("CustomName", this.getCustomNameTag());
        }

    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        this.setVariant(compound.getInteger("Variant"));
        this.setChested(compound.getBoolean("Chested"));
        this.setSaddled(compound.getBoolean("Saddled"));
        this.setHovering(compound.getBoolean("Hovering"));
        this.setFlying(compound.getBoolean("Flying"));
        this.setArmor(compound.getInteger("Armor"));
        if (hippogryphInventory != null) {
            NBTTagList nbttaglist = compound.getTagList("Items", 10);
            this.initHippogryphInv();
            for (int i = 0; i < nbttaglist.tagCount(); ++i) {
                NBTTagCompound nbttagcompound = nbttaglist.getCompoundTagAt(i);
                int j = nbttagcompound.getByte("Slot") & 255;
                if (j <= 4) {
                    this.hippogryphInventory.setInventorySlotContents(j, new ItemStack(nbttagcompound));
                }
            }
        } else {
            NBTTagList nbttaglist = compound.getTagList("Items", 10);
            this.initHippogryphInv();
            for (int i = 0; i < nbttaglist.tagCount(); ++i) {
                NBTTagCompound nbttagcompound = nbttaglist.getCompoundTagAt(i);
                int j = nbttagcompound.getByte("Slot") & 255;
                this.initHippogryphInv();
                this.hippogryphInventory.setInventorySlotContents(j, new ItemStack(nbttagcompound));
                //this.setArmorInSlot(j, this.getIntFromArmor(ItemStack.loadItemStackFromNBT(nbttagcompound)));
                ItemStack saddle = hippogryphInventory.getStackInSlot(0);
                ItemStack chest = hippogryphInventory.getStackInSlot(1);
                if (world.isRemote) {
                    IceAndFire.NETWORK_WRAPPER.sendToServer(new MessageHippogryphArmor(this.getEntityId(), 0, saddle != null && saddle.getItem() == Items.SADDLE && !saddle.isEmpty() ? 1 : 0));
                    IceAndFire.NETWORK_WRAPPER.sendToServer(new MessageHippogryphArmor(this.getEntityId(), 1, chest != null && chest.getItem() == Item.getItemFromBlock(Blocks.CHEST) && !chest.isEmpty() ? 1 : 0));
                    IceAndFire.NETWORK_WRAPPER.sendToServer(new MessageHippogryphArmor(this.getEntityId(), 2, this.getIntFromArmor(hippogryphInventory.getStackInSlot(1))));
                }
            }
        }
    }

    public int getVariant() {
        return this.dataManager.get(VARIANT);
    }

    public EnumHippogryphTypes getEnumVariant() {
        return EnumHippogryphTypes.values()[this.getVariant()];
    }

    public void setEnumVariant(EnumHippogryphTypes variant) {
        this.setVariant(variant.ordinal());
    }

    public void setVariant(int variant) {
        this.dataManager.set(VARIANT, variant);
    }

    public boolean isSaddled() {
        return this.dataManager.get(SADDLE);
    }

    public void setSaddled(boolean saddle) {
        this.dataManager.set(SADDLE, saddle);
    }

    public boolean isChested() {
        return this.dataManager.get(CHESTED);
    }

    public void setChested(boolean chested) {
        this.dataManager.set(CHESTED, chested);
    }

    public boolean isSitting() {
        if (world.isRemote) {
            boolean isSitting = (((Byte)this.dataManager.get(TAMED)).byteValue() & 1) != 0;
            this.isSitting = isSitting;
            return isSitting;
        }
        return isSitting;
    }

    public void setSleeping(boolean sitting) {
        if (!world.isRemote) {
            this.isSitting = sitting;
        }
        byte b0 = ((Byte)this.dataManager.get(TAMED)).byteValue();
        if (sitting) {
            this.dataManager.set(TAMED, Byte.valueOf((byte)(b0 | 1)));
        }
        else {
            this.dataManager.set(TAMED, Byte.valueOf((byte)(b0 & -2)));
        }
    }


    public boolean isHovering() {
        if (world.isRemote) {
            return this.isHovering = this.dataManager.get(HOVERING);
        }
        return isHovering;
    }

    public void setHovering(boolean hovering) {
        this.dataManager.set(HOVERING, hovering);
        if (!world.isRemote) {
            this.isHovering = hovering;
        }
    }

    public boolean isFlying() {
        if (world.isRemote) {
            return this.isFlying = this.dataManager.get(FLYING);
        }
        return isFlying;
    }

    public void setFlying(boolean flying) {
        this.dataManager.set(FLYING, flying);
        if (!world.isRemote) {
            this.isFlying = flying;
        }
    }

    public int getArmor() {
        return this.dataManager.get(ARMOR);
    }

    public void setArmor(int armorType) {
        this.dataManager.set(ARMOR, armorType);
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.3D);
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(40.0D);
        this.getAttributeMap().registerAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
        this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(5.0D);
        this.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(32.0D);
    }

    public boolean canMove() {
        return !this.isSitting() && this.getControllingPassenger() == null && sitProgress == 0;
    }

    @Override
    @Nullable
    public IEntityLivingData onInitialSpawn(DifficultyInstance difficulty, @Nullable IEntityLivingData livingdata) {
        livingdata = super.onInitialSpawn(difficulty, livingdata);
        this.setEnumVariant(EnumHippogryphTypes.getRandomType());
        return livingdata;
    }

    @Override
    public boolean attackEntityFrom(DamageSource dmg, float i) {
        if (this.isBeingRidden() && dmg.getTrueSource() != null && this.getControllingPassenger() != null && dmg.getTrueSource() == this.getControllingPassenger()) {
            return false;
        }
        float damageReduction = getArmor() * 0.2F;
        i -= damageReduction;
        return super.attackEntityFrom(dmg, i);
    }

    @Nullable
    @Override
    public EntityAgeable createChild(EntityAgeable ageable) {
        return null;
    }

    @Override
    public int getAnimationTick() {
        return animationTick;
    }

    @Override
    public void setAnimationTick(int tick) {
        animationTick = tick;
    }

    @Override
    public Animation getAnimation() {
        return currentAnimation;
    }

    @Override
    public void setAnimation(Animation animation) {
        currentAnimation = animation;
    }

    public void playLivingSound() {
        if (this.getAnimation() == this.NO_ANIMATION) {
            this.setAnimation(ANIMATION_SPEAK);
        }
        super.playLivingSound();
    }

    protected void playHurtSound(DamageSource source) {
        if (this.getAnimation() == this.NO_ANIMATION) {
            this.setAnimation(ANIMATION_SPEAK);
        }
        super.playHurtSound(source);
    }

    @Override
    public Animation[] getAnimations() {
        return new Animation[]{IAnimatedEntity.NO_ANIMATION, EntityHippogryph.ANIMATION_EAT, EntityHippogryph.ANIMATION_BITE, EntityHippogryph.ANIMATION_SPEAK, EntityHippogryph.ANIMATION_SCRATCH};
    }

    public boolean isRidingPlayer(EntityPlayer player) {
        return this.getControllingPassenger() != null && this.getControllingPassenger() instanceof EntityPlayer && this.getControllingPassenger().getUniqueID().equals(player.getUniqueID());
    }

    public boolean shouldDismountInWater(Entity rider) {
        return false;
    }

    public boolean isDirectPathBetweenPoints(Vec3d vec1, Vec3d vec2) {
        RayTraceResult movingobjectposition = this.world.rayTraceBlocks(vec1, new Vec3d(vec2.x, vec2.y + (double) this.height * 0.5D, vec2.z), false, true, false);
        return movingobjectposition == null || movingobjectposition.typeOfHit != RayTraceResult.Type.BLOCK;
    }

    @SideOnly(Side.CLIENT)
    protected void updateClientControls() {
        Minecraft mc = Minecraft.getMinecraft();
        if (this.isRidingPlayer(mc.player)) {
            byte previousState = getControlState();
            up(mc.gameSettings.keyBindJump.isKeyDown());
            down(mc.gameSettings.keyBindSneak.isKeyDown());
            attack(ModKeys.dragon_strike.isKeyDown());
            dismount(ModKeys.dragon_down.isKeyDown());
            byte controlState = getControlState();
            if (controlState != previousState) {
                IceAndFire.NETWORK_WRAPPER.sendToServer(new MessageDragonControl(this.getEntityId(), controlState));
            }
        }
        if (this.getRidingEntity() != null && this.getRidingEntity() == mc.player) {
            byte previousState = getControlState();
            dismount(ModKeys.dragon_down.isKeyDown());
            byte controlState = getControlState();
            if (controlState != previousState) {
                IceAndFire.NETWORK_WRAPPER.sendToServer(new MessageDragonControl(this.getEntityId(), controlState));
            }
        }
    }

    @Override
    public void moveEntityWithHeading(float strafe, float forward) {
        if (!this.canMove() && !this.isBeingRidden()) {
            strafe = 0;
            forward = 0;
            super.moveEntityWithHeading(strafe, forward);
            return;
        }
        if (this.isBeingRidden() && this.canBeSteered()) {
            EntityLivingBase controller = (EntityLivingBase) this.getControllingPassenger();
            if (controller != null) {
                strafe = controller.moveStrafing * 0.5F;
                forward = controller.moveForward;
                if (forward <= 0.0F) {
                    forward *= 0.25F;
                }
                if (this.isFlying() || this.isHovering()) {
                    motionX *= 1.06;
                    motionZ *= 1.06;
                }
                jumpMovementFactor = 0.05F;
                this.setAIMoveSpeed(onGround ? (float) this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue() : 5);
            }
        }
        super.moveEntityWithHeading(strafe, forward);
    }

    @Override
    public boolean attackEntityAsMob(Entity entityIn) {
        boolean flag = entityIn.attackEntityFrom(DamageSource.causeMobDamage(this), ((int) this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue()));
        if (flag && this.getAnimation() != this.ANIMATION_BITE && this.getAnimation() != this.ANIMATION_SCRATCH) {
            this.setAnimation(this.getRNG().nextBoolean() ? this.ANIMATION_SCRATCH : this.ANIMATION_BITE);
        }
        if (flag) {
            this.applyEnchantments(this, entityIn);
        }

        return flag;
    }

    public EntityItem createEgg(EntityHippogryph partner) {
        int i = MathHelper.floor(this.posX);
        int j = MathHelper.floor(this.posY);
        int k = MathHelper.floor(this.posZ);
        ItemStack stack = new ItemStack(ModItems.hippogryph_egg);
        EntityItem egg = new EntityItem(this.world, i, j, k, stack);
        return egg;
    }

    public void fall(float distance, float damageMultiplier) {
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (this.isFlying() && this.ticksExisted % 40 == 0 || this.isFlying() && this.isSitting()) {
            this.setFlying(false);
            this.setFlying(true);
            this.setSitting(false);
        }
        if (!this.canMove() && this.getAttackTarget() != null) {
            this.setAttackTarget(null);
        }
        if (!this.canMove()) {
            this.getNavigator().clearPathEntity();

        }
        if (this.getControllingPassenger() != null) {
            if (motionY > 0.5) {
                this.motionY = 0.5;
            }
            if (motionY < -0.5) {
                this.motionY = -0.5;
            }
        } else {
            if (motionY > 0.8) {
                this.motionY = 0.5;
            }
            if (motionY < -0.8) {
                this.motionY = -0.8;
            }
            if (motionY > 1) {
                this.motionY = 0;
            }
        }
        AnimationHandler.INSTANCE.updateAnimations(this);

        boolean sitting = isSitting() && !isHovering() && !isFlying();
        if (sitting && sitProgress < 20.0F) {
            sitProgress += 0.5F;
        } else if (!sitting && sitProgress > 0.0F) {
            sitProgress -= 0.5F;
        }

        boolean hovering = isHovering();
        if (hovering && hoverProgress < 20.0F) {
            hoverProgress += 0.5F;
        } else if (!hovering && hoverProgress > 0.0F) {
            hoverProgress -= 0.5F;
        }
        boolean flying = this.isFlying() || !this.onGround && !this.isHovering() && this.airTarget != null;
        if (flying && flyProgress < 20.0F) {
            flyProgress += 0.5F;
        } else if (!flying && flyProgress > 0.0F) {
            flyProgress -= 0.5F;
        }
        if (this.onGround && this.doesWantToLand() && (this.isFlying() || this.isHovering())) {
            this.setFlying(false);
            this.setHovering(false);
        }
        if (this.getControllingPassenger() != null && !this.onGround && (this.isFlying() || this.isHovering())) {
            this.motionY *= 0D;
        }
        if (this.isHovering()) {
            if (this.isSitting()) {
                this.setHovering(false);
            }
            this.hoverTicks++;
            if (this.doesWantToLand()) {
                this.motionY -= 0.25D;
            } else {
                if (this.getControllingPassenger() == null) {
                    this.motionY += 0.08;
                }
                if (this.hoverTicks > 40) {
                    if (!this.isChild()) {
                        this.setFlying(true);
                    }
                    this.setHovering(false);
                    this.hoverTicks = 0;
                    this.flyTicks = 0;
                }
            }
        }
        if (this.isSitting()) {
            this.getNavigator().clearPathEntity();
        }
        if (!this.isFlying() && !this.isHovering() && this.airTarget != null && this.onGround) {
            this.airTarget = null;
        }
        if (this.isFlying() && this.airTarget == null && this.onGround && this.getControllingPassenger() == null) {
            this.setFlying(false);
        }

        if (this.isFlying() && getAttackTarget() == null) {
            flyAround();
        } else if (getAttackTarget() != null) {
            flyTowardsTarget();
        }
        if (this.onGround && flyTicks != 0) {
            flyTicks = 0;
        }
        if (this.isFlying() && this.doesWantToLand()) {
            this.setFlying(false);
            this.setHovering(!this.onGround);
            if (this.onGround) {
                flyTicks = 0;
                this.setFlying(false);
            }
        }
        if (this.isFlying()) {
            this.flyTicks++;
        }
        if ((this.isHovering() || this.isFlying()) && this.isSitting()) {
            this.setFlying(false);
            this.setHovering(false);
        }
        if (!world.isRemote && this.getRNG().nextInt(FLIGHT_CHANCE_PER_TICK) == 0 && !this.isSitting() && !this.isFlying() && this.getPassengers().isEmpty() && !this.isChild() && !this.isHovering() && !this.isSitting() && this.canMove() && this.onGround) {
            this.setHovering(true);
            this.setSitting(false);
            this.setSitting(false);
            this.hoverTicks = 0;
            this.flyTicks = 0;
        }
        if (getAttackTarget() != null && !this.getPassengers().isEmpty() && this.getOwner() != null && this.getPassengers().contains(this.getOwner())) {
            this.setAttackTarget(null);
        }
    }

    public boolean doesWantToLand() {
        return this.flyTicks > 6000 || down() || flyTicks > 40 && this.flyProgress == 0;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (world.isRemote) {//TODO
            this.updateClientControls();
        }
        if (this.up()) {
            if (!this.isFlying() && !this.isHovering()) {
                this.spacebarTicks += 2;
            }
            if (this.isFlying() || this.isHovering()) {
                this.motionY += 0.4D;
            }
        } else if (this.dismount()) {
            if (this.isFlying() || this.isHovering()) {
                this.motionY -= 0.4D;
                if (this.onGround) {
                    this.setFlying(false);
                    this.setHovering(false);
                }
            }
        }
        if (!this.dismount() && (this.isFlying() || this.isHovering())) {
            this.motionY += 0.01D;
        }

        if (this.attack() && this.getControllingPassenger() != null && this.getControllingPassenger() instanceof EntityPlayer) {
            Entity entity = null;
            List<Entity> list = this.world.getEntitiesWithinAABBExcludingEntity(this, this.getEntityBoundingBox().expand(2, 2, 2));
            Collections.sort(list, new EntityAINearestAttackableTarget.Sorter(this));
            if (!list.isEmpty()) {
                entity = list.get(0);
                if (entity.getUniqueID().compareTo(this.getControllingPassenger().getUniqueID()) != 0) {
                    if (this.getAnimation() != this.ANIMATION_BITE && this.getAnimation() != this.ANIMATION_SCRATCH) {
                        this.setAnimation(this.getRNG().nextBoolean() ? this.ANIMATION_SCRATCH : this.ANIMATION_BITE);
                        entity.attackEntityFrom(DamageSource.causeMobDamage(this), ((int) this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue()));
                    }
                }
            }

        }
        if (this.getControllingPassenger() != null && this.getControllingPassenger().isSneaking()) {
            this.getControllingPassenger().dismountRidingEntity();
        }
        if (this.isFlying() && !this.isHovering() && this.getControllingPassenger() != null && !this.onGround && Math.max(Math.abs(motionZ), Math.abs(motionX)) < 0.1F) {
            this.setHovering(true);
            this.setFlying(false);
        }
        if (this.isHovering() && !this.isFlying() && this.getControllingPassenger() != null && !this.onGround && Math.max(Math.abs(motionZ), Math.abs(motionX)) > 0.1F) {
            this.setFlying(true);
            this.setHovering(false);
        }
        if (this.spacebarTicks > 0) {
            this.spacebarTicks--;
        }
        if (this.spacebarTicks > 20 && this.getOwner() != null && this.getPassengers().contains(this.getOwner()) && !this.isFlying() && !this.isHovering()) {
            this.setHovering(true);
        }
        if (world.isRemote) {
            roll_buffer.calculateChainFlapBuffer(50, 10, 4, this);
        }
        if (this.getAttackTarget() != null && this.getRidingEntity() == null && this.getAttackTarget().isDead || this.getAttackTarget() != null && this.getAttackTarget() instanceof EntityDragonBase && ((EntityDragonBase) this.getAttackTarget()).isDead) {
            this.setAttackTarget(null);
        }
    }

    public void flyAround() {
        if (airTarget != null) {
            if (!isTargetInAir() || flyTicks > 6000 || !this.isFlying()) {
                airTarget = null;
            }
            flyTowardsTarget();
        }
    }

    public boolean isTargetBlocked(Vec3d target){
        if(target != null) {
            RayTraceResult rayTrace = world.rayTraceBlocks(new Vec3d(this.getPosition()), target, false);
            if (rayTrace != null && rayTrace.hitVec != null) {
                BlockPos pos = new BlockPos(rayTrace.hitVec);
                if (!world.isAirBlock(pos)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void flyTowardsTarget() {
        if (airTarget != null && isTargetInAir() && this.isFlying() && this.getDistanceSquared(new Vec3d(airTarget.getX(), this.posY, airTarget.getZ())) > 3) {
            double targetX = airTarget.getX() + 0.5D - posX;
            double targetY = Math.min(airTarget.getY(), 256) + 1D - posY;
            double targetZ = airTarget.getZ() + 0.5D - posZ;
            motionX += (Math.signum(targetX) * 0.5D - motionX) * 0.100000000372529 * 5;
            motionY += (Math.signum(targetY) * 0.5D - motionY) * 0.100000000372529 * 5;
            motionZ += (Math.signum(targetZ) * 0.5D - motionZ) * 0.100000000372529 * 5;
            float angle = (float) (Math.atan2(motionZ, motionX) * 180.0D / Math.PI) - 90.0F;
            float rotation = MathHelper.wrapDegrees(angle - rotationYaw);
            moveForward = 0.5F;
            prevRotationYaw = rotationYaw;
            rotationYaw += rotation;
            if (!this.isFlying()) {
                this.setFlying(true);
            }
        } else {
            this.airTarget = null;
        }
        if (airTarget != null && isTargetInAir() && this.isFlying() && this.getDistanceSquared(new Vec3d(airTarget.getX(), this.posY, airTarget.getZ())) < 3 && this.doesWantToLand()) {
            this.setFlying(false);
            this.setHovering(true);
        }
    }

    protected boolean isTargetInAir() {
        return airTarget != null && ((world.getBlockState(airTarget).getMaterial() == Material.AIR) ||  world.getBlockState(airTarget).getMaterial() == Material.AIR);
    }

    public float getDistanceSquared(Vec3d vec3d) {
        float f = (float) (this.posX - vec3d.x);
        float f1 = (float) (this.posY - vec3d.y);
        float f2 = (float) (this.posZ - vec3d.z);
        return f * f + f1 * f1 + f2 * f2;
    }

    public boolean replaceItemInInventory(int inventorySlot, @Nullable ItemStack itemStackIn) {
        int j = inventorySlot - 500 + 2;
        if (j >= 0 && j < this.hippogryphInventory.getSizeInventory()) {
            this.hippogryphInventory.setInventorySlotContents(j, itemStackIn);
            return true;
        } else {
            return false;
        }
    }

    public void onDeath(DamageSource cause) {
        super.onDeath(cause);
        if (hippogryphInventory != null && !this.world.isRemote) {
            for (int i = 0; i < hippogryphInventory.getSizeInventory(); ++i) {
                ItemStack itemstack = hippogryphInventory.getStackInSlot(i);
                if (!itemstack.isEmpty()) {
                    this.entityDropItem(itemstack, 0.0F);
                }
            }
        }
    }

}
