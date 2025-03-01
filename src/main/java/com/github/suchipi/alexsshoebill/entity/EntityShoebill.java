package com.github.suchipi.alexsshoebill.entity;

import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.github.alexthe666.citadel.animation.Animation;
import com.github.alexthe666.citadel.animation.AnimationHandler;
import com.github.alexthe666.citadel.animation.IAnimatedEntity;
import com.github.suchipi.alexsshoebill.config.AMConfig;
import com.github.suchipi.alexsshoebill.entity.ai.AnimalAIWadeSwimming;
import com.github.suchipi.alexsshoebill.entity.ai.CreatureAITargetItems;
import com.github.suchipi.alexsshoebill.entity.ai.DirectPathNavigator;
import com.github.suchipi.alexsshoebill.entity.ai.EntityAINearestTarget3D;
import com.github.suchipi.alexsshoebill.entity.ai.FlightMoveController;
import com.github.suchipi.alexsshoebill.entity.ai.GroundPathNavigatorWide;
import com.github.suchipi.alexsshoebill.entity.ai.ShoebillAIFish;
import com.github.suchipi.alexsshoebill.entity.ai.ShoebillAIFlightFlee;
import com.github.suchipi.alexsshoebill.misc.AMSoundRegistry;
import com.github.suchipi.alexsshoebill.misc.AMTagRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class EntityShoebill extends Animal implements IAnimatedEntity, ITargetsDroppedItems {

    public static final Animation ANIMATION_FISH = Animation.create(40);
    public static final Animation ANIMATION_BEAKSHAKE = Animation.create(20);
    public static final Animation ANIMATION_ATTACK = Animation.create(20);
    private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(EntityShoebill.class, EntityDataSerializers.BOOLEAN);
    public float prevFlyProgress;
    public float flyProgress;
    public int revengeCooldown = 0;
    private int animationTick;
    private Animation currentAnimation;
    private boolean isLandNavigator;
    public int fishingCooldown = 1200 + random.nextInt(1200);
    public int lureLevel = 0;
    public int luckLevel = 0;
    public static final Predicate<LivingEntity> TARGET_BABY  = (animal) -> {
        return animal.isBaby();
    };

    protected EntityShoebill(EntityType type, Level world) {
        super(type, world);
        this.setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
        this.setPathfindingMalus(BlockPathTypes.WATER_BORDER, 0.0F);
        switchNavigator(false);
    }

    public boolean checkSpawnRules(LevelAccessor worldIn, MobSpawnType spawnReasonIn) {
        return AMEntityRegistry.rollSpawn(AMConfig.shoebillSpawnRolls, this.getRandom(), spawnReasonIn);
    }

    public static AttributeSupplier.Builder bakeAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 10D).add(Attributes.ATTACK_DAMAGE, 4.0D).add(Attributes.MOVEMENT_SPEED, 0.2F);
    }

    protected SoundEvent getAmbientSound() {
        return AMSoundRegistry.SHOEBILL_RATTLE;
    }

    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return AMSoundRegistry.SHOEBILL_RATTLE;
    }

    protected SoundEvent getDeathSound() {
        return AMSoundRegistry.SHOEBILL_RATTLE;
    }

    public boolean isFood(ItemStack stack) {
        return false;
    }

    public boolean hurt(DamageSource source, float amount) {
        boolean prev = super.hurt(source, amount);
        if (prev && source.getEntity() != null && !(source.getEntity() instanceof AbstractFish)) {
            double range = 15;
            int fleeTime = 100 + getRandom().nextInt(150);
            this.revengeCooldown = fleeTime;
            List<? extends EntityShoebill> list = this.level.getEntitiesOfClass(this.getClass(), this.getBoundingBox().inflate(range, range / 2, range));
            for (EntityShoebill gaz : list) {
                gaz.revengeCooldown = fleeTime;
            }
        }
        return prev;
    }

    private void switchNavigator(boolean onLand) {
        if (onLand) {
            this.moveControl = new MoveControl(this);
            this.navigation = new GroundPathNavigatorWide(this, level);
            this.isLandNavigator = true;
        } else {
            this.moveControl = new FlightMoveController(this, 0.7F, false);
            this.navigation = new DirectPathNavigator(this, level);
            this.isLandNavigator = false;
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(FLYING, false);
    }

    protected void registerGoals() {
        this.goalSelector.addGoal(0, new AnimalAIWadeSwimming(this));
        this.goalSelector.addGoal(1, new ShoebillAIFish(this));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(4, new ShoebillAIFlightFlee(this));
        this.goalSelector.addGoal(5, new TemptGoal(this, 1.1D, Ingredient.of(AMTagRegistry.SHOEBILL_FOODSTUFFS), false));
        this.goalSelector.addGoal(6, new RandomStrollGoal(this, 1D, 1400));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.targetSelector.addGoal(1, new EntityAINearestTarget3D(this, AbstractFish.class, 30, false, true, null));
        this.targetSelector.addGoal(2, new CreatureAITargetItems(this, false, 10));
        this.targetSelector.addGoal(3, (new HurtByTargetGoal(this, Player.class)).setAlertOthers());

    }

    public boolean isTargetBlocked(Vec3 target) {
        Vec3 Vector3d = new Vec3(this.getX(), this.getEyeY(), this.getZ());
        return this.level.clip(new ClipContext(Vector3d, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS;
    }

    public boolean causeFallDamage(float distance, float damageMultiplier) {
        return false;
    }

    protected void checkFallDamage(double y, boolean onGroundIn, BlockState state, BlockPos pos) {
    }

    public void tick() {
        super.tick();
        if(this.isInWater()){
            maxUpStep = 1.2F;
        }else{
            maxUpStep = 0.6F;
        }
        prevFlyProgress = flyProgress;
        if (isFlying() && flyProgress < 5F) {
            flyProgress++;
        }
        if (!isFlying() && flyProgress > 0F) {
            flyProgress--;
        }
        if (revengeCooldown > 0) {
            revengeCooldown--;
        }
        if (revengeCooldown == 0 && this.getLastHurtByMob() != null) {
            this.setLastHurtByMob(null);
        }
        if (!level.isClientSide) {
            if(fishingCooldown > 0){
                fishingCooldown--;
            }
            if(this.getAnimation() == NO_ANIMATION && this.getRandom().nextInt(700) == 0){
                this.setAnimation(ANIMATION_BEAKSHAKE);
            }
            if (isFlying() && this.isLandNavigator) {
                switchNavigator(false);
            }
            if (!isFlying() && !this.isLandNavigator) {
                switchNavigator(true);
            }
            if (this.revengeCooldown > 0 && !this.isFlying()) {
                if (this.onGround || this.isInWater()) {
                    this.setFlying(false);
                }
            }
            if (isFlying()) {
                this.setNoGravity(true);
            } else {
                this.setNoGravity(false);
            }
        }
        if (!level.isClientSide && this.getTarget() != null && this.hasLineOfSight(this.getTarget()) && this.getAnimation() == ANIMATION_ATTACK && this.getAnimationTick() == 9) {
            float f1 = this.getYRot() * ((float) Math.PI / 180F);
            getTarget().knockback(0.3F, getTarget().getX() - this.getX(), getTarget().getZ() - this.getZ());
            this.getTarget().hurt(DamageSource.mobAttack(this), (float) this.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue());
        }
        AnimationHandler.INSTANCE.updateAnimations(this);
    }


    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("Flying", this.isFlying());
        compound.putInt("FishingTimer", this.fishingCooldown);
        compound.putInt("FishingLuck", this.luckLevel);
        compound.putInt("FishingLure", this.lureLevel);
        compound.putInt("RevengeCooldownTimer", this.revengeCooldown);
    }

    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setFlying(compound.getBoolean("Flying"));
        this.fishingCooldown = compound.getInt("FishingTimer");
        this.luckLevel = compound.getInt("FishingLuck");
        this.lureLevel = compound.getInt("FishingLure");
        this.revengeCooldown = compound.getInt("RevengeCooldownTimer");

    }


    protected float getWaterSlowDown() {
        return 0.98F;
    }

    public boolean doHurtTarget(Entity entityIn) {
        if (this.getAnimation() == NO_ANIMATION) {
            this.setAnimation(ANIMATION_ATTACK);
        }
        return true;
    }

    public boolean isFlying() {
        return this.entityData.get(FLYING);
    }

    public void setFlying(boolean flying) {
        this.entityData.set(FLYING, flying);
    }


    @Override
    public int getAnimationTick() {
        return animationTick;
    }

    @Override
    public void setAnimationTick(int i) {
        animationTick = i;
    }

    @Override
    public Animation getAnimation() {
        return currentAnimation;
    }

    @Override
    public void setAnimation(Animation animation) {
        currentAnimation = animation;
    }

    @Override
    public Animation[] getAnimations() {
        return new Animation[]{ANIMATION_FISH, ANIMATION_BEAKSHAKE, ANIMATION_ATTACK};
    }

    public InteractionResult mobInteract(Player p_230254_1_, InteractionHand p_230254_2_) {
        ItemStack lvt_3_1_ = p_230254_1_.getItemInHand(p_230254_2_);
         if ((lvt_3_1_.getItem() == Items.PUFFERFISH || lvt_3_1_.getItem() == Items.PUFFERFISH_BUCKET) && this.isAlive()) {
             if(this.luckLevel < 10) {
                 luckLevel = Mth.clamp(luckLevel + 1, 0, 10);
                 for (int i = 0; i < 6 + random.nextInt(3); i++) {
                     double d2 = this.random.nextGaussian() * 0.02D;
                     double d0 = this.random.nextGaussian() * 0.02D;
                     double d1 = this.random.nextGaussian() * 0.02D;
                     this.level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, lvt_3_1_), this.getX() + (double) (this.random.nextFloat() * this.getBbWidth()) - (double) this.getBbWidth() * 0.5F, this.getY() + this.getBbHeight() * 0.5F + (double) (this.random.nextFloat() * this.getBbHeight() * 0.5F), this.getZ() + (double) (this.random.nextFloat() * this.getBbWidth()) - (double) this.getBbWidth() * 0.5F, d0, d1, d2);
                 }
                 this.playSound(SoundEvents.CAT_EAT, this.getSoundVolume(), this.getVoicePitch());
                 lvt_3_1_.shrink(1);
                 return net.minecraft.world.InteractionResult.sidedSuccess(this.level.isClientSide);
             }else{
                 if(this.getAnimation() == NO_ANIMATION){
                     this.setAnimation(ANIMATION_BEAKSHAKE);
                 }
                 return InteractionResult.SUCCESS;
             }
         } else if (lvt_3_1_.getItem() == Items.TURTLE_EGG && this.isAlive()) {
             if(this.lureLevel < 10){
                 lureLevel = Mth.clamp(lureLevel + 1, 0, 10);
                 fishingCooldown = Mth.clamp(fishingCooldown - 200, 200, 2400);
                 for (int i = 0; i < 6 + random.nextInt(3); i++) {
                     double d2 = this.random.nextGaussian() * 0.02D;
                     double d0 = this.random.nextGaussian() * 0.02D;
                     double d1 = this.random.nextGaussian() * 0.02D;
                     this.level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, lvt_3_1_), this.getX() + (double) (this.random.nextFloat() * this.getBbWidth()) - (double) this.getBbWidth() * 0.5F, this.getY() + this.getBbHeight() * 0.5F + (double) (this.random.nextFloat() * this.getBbHeight() * 0.5F), this.getZ() + (double) (this.random.nextFloat() * this.getBbWidth()) - (double) this.getBbWidth() * 0.5F, d0, d1, d2);
                 }
                 lvt_3_1_.shrink(1);
                 this.playSound(SoundEvents.CAT_EAT, this.getSoundVolume(), this.getVoicePitch());
                 return net.minecraft.world.InteractionResult.sidedSuccess(this.level.isClientSide);
             }else{
                 if(this.getAnimation() == NO_ANIMATION){
                     this.setAnimation(ANIMATION_BEAKSHAKE);
                 }
                 return InteractionResult.SUCCESS;
             }

         } else {
            return super.mobInteract(p_230254_1_, p_230254_2_);
        }
    }


    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel serverWorld, AgeableMob ageableEntity) {
        return AMEntityRegistry.SHOEBILL.get().create(serverWorld);
    }

    @Override
    public boolean canTargetItem(ItemStack stack) {
        return stack.is(AMTagRegistry.SHOEBILL_FOODSTUFFS) || stack.getItem() == Items.PUFFERFISH && luckLevel < 10 || stack.getItem() == Items.TURTLE_EGG && lureLevel < 10;
    }

    public void resetFishingCooldown(){
        fishingCooldown = Math.max(1200 + random.nextInt(1200) - lureLevel * 120, 200);
    }
    @Override
    public void onGetItem(ItemEntity e) {
        this.playSound(SoundEvents.CAT_EAT, this.getSoundVolume(), this.getVoicePitch());
        if(e.getItem().getItem() == Items.PUFFERFISH){
            luckLevel = Mth.clamp(luckLevel + 1, 0, 10);
        }
        if(e.getItem().getItem() == Items.TURTLE_EGG){
            lureLevel = Mth.clamp(lureLevel + 1, 0, 10);
        }
        this.heal(5);
    }
}
