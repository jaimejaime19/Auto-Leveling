package daripher.autoleveling.event;

import daripher.autoleveling.AutoLevelingMod;
import daripher.autoleveling.config.Config;
import daripher.autoleveling.data.DimensionsLevelingSettingsReloader;
import daripher.autoleveling.data.EntitiesLevelingSettingsReloader;
import daripher.autoleveling.init.AutoLevelingAttributes;
import daripher.autoleveling.mixin.LivingEntityAccessor;
import daripher.autoleveling.network.NetworkDispatcher;
import daripher.autoleveling.network.message.SyncLevelingData;
import daripher.autoleveling.saveddata.GlobalLevelingData;
import daripher.autoleveling.saveddata.WorldLevelingData;
import daripher.autoleveling.settings.DimensionLevelingSettings;
import daripher.autoleveling.settings.LevelingSettings;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.network.PacketDistributor;

@EventBusSubscriber(modid = AutoLevelingMod.MOD_ID)
public class MobsLevelingEvents {
  private static final String LEVEL_TAG = "LEVEL";

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void applyLevelBonuses(EntityJoinLevelEvent event) {
    if (!shouldSetLevel(event.getEntity())) return;
    LivingEntity entity = (LivingEntity) event.getEntity();
    if (hasLevel(entity)) {
      applyAttributeBonuses(entity);
      return;
    }
    BlockPos spawnPos = getSpawnPosition(entity);
    double distanceToSpawn = Math.sqrt(spawnPos.distSqr(entity.blockPosition()));
    int level = createLevelForEntity(entity, distanceToSpawn);
    setLevel(entity, level);
    applyAttributeBonuses(entity);
    addEquipment(entity);
  }

  private static BlockPos getSpawnPosition(LivingEntity entity) {
    ResourceKey<Level> dimension = entity.level().dimension();
    DimensionLevelingSettings settings = DimensionsLevelingSettingsReloader.get(dimension);
    if (settings.spawnPosOverride() == null) {
      return entity.level().getSharedSpawnPos();
    }
    return settings.spawnPosOverride();
  }

  @SubscribeEvent
  public static void adjustExperienceDrop(LivingExperienceDropEvent event) {
    if (!hasLevel(event.getEntity())) return;
    int level = getLevel(event.getEntity()) + 1;
    int originalExp = event.getDroppedExperience();
    double expBonus = Config.COMMON.expBonus.get() * level;
    event.setDroppedExperience((int) (originalExp + originalExp * expBonus));
  }

  @SubscribeEvent
  public static void dropAdditionalLoot(LivingDropsEvent event) {
    LivingEntity entity = event.getEntity();
    if (!hasLevel(entity)) return;
    ResourceLocation lootTableId =
        new ResourceLocation(AutoLevelingMod.MOD_ID, "gameplay/leveled_mobs");
    MinecraftServer server = entity.level().getServer();
    if (server == null) return;
    LootTable lootTable = server.getLootData().getLootTable(lootTableId);
    LootParams lootParams = createLootParams(entity, event.getSource());
    lootTable.getRandomItems(lootParams, entity::spawnAtLocation);
  }

  @SubscribeEvent
  public static void reloadSettings(AddReloadListenerEvent event) {
    event.addListener(new DimensionsLevelingSettingsReloader());
    event.addListener(new EntitiesLevelingSettingsReloader());
  }

  @SubscribeEvent
  public static void syncEntityLevel(PlayerEvent.StartTracking event) {
    if (!hasLevel(event.getTarget())) return;
    LivingEntity entity = (LivingEntity) event.getTarget();
    ServerPlayer player = (ServerPlayer) event.getEntity();
    PacketDistributor.PacketTarget packetTarget = PacketDistributor.PLAYER.with(() -> player);
    NetworkDispatcher.network_channel.send(packetTarget, new SyncLevelingData(entity));
  }

  @SubscribeEvent
  public static void applyAttributesDamageBonus(LivingHurtEvent event) {
    DamageSource damage = event.getSource();
    if (!(damage.getEntity() instanceof LivingEntity attacker)) return;
    float multiplier = getDamageMultiplier(damage, attacker);
    if (multiplier > 1F) event.setAmount(event.getAmount() * multiplier);
  }

  public static float getDamageMultiplier(DamageSource damage, LivingEntity attacker) {
    if (damage.is(DamageTypeTags.IS_PROJECTILE)) {
      return getAttributeValue(attacker, AutoLevelingAttributes.PROJECTILE_DAMAGE_MULTIPLIER.get());
    }
    if (damage.is(DamageTypeTags.IS_EXPLOSION)) {
      return getAttributeValue(attacker, AutoLevelingAttributes.EXPLOSION_DAMAGE_MULTIPLIER.get());
    }
    return 1F;
  }

  private static float getAttributeValue(LivingEntity entity, Attribute damageBonusAttribute) {
    if (entity.getAttribute(damageBonusAttribute) == null) return 0F;
    return (float) Objects.requireNonNull(entity.getAttribute(damageBonusAttribute)).getValue();
  }

  @OnlyIn(Dist.CLIENT)
  public static boolean shouldShowName(LivingEntity entity) {
    if (!Minecraft.renderNames()) return false;
    if (entity.isVehicle()) return false;
    Minecraft minecraft = Minecraft.getInstance();
    if (entity == minecraft.getCameraEntity()) return false;
    LocalPlayer clientPlayer = minecraft.player;
    if (clientPlayer == null) return false;
    if (!clientPlayer.hasLineOfSight(entity) || entity.isInvisibleTo(clientPlayer)) return false;
    if (!hasLevel(entity)) return false;
    if (!shouldShowLevel(entity)) return false;
    if (Config.COMMON.alwaysShowLevel.get()) return true;
    return Config.COMMON.showLevelWhenLookingAt.get() && minecraft.crosshairPickEntity == entity;
  }

  private static boolean shouldSetLevel(Entity entity) {
    if (entity.level().isClientSide) return false;
    return canHaveLevel(entity);
  }

  private static int createLevelForEntity(LivingEntity entity, double distance) {
    MinecraftServer server = entity.getServer();
    if (server == null) return 0;
    LevelingSettings settings = getLevelingSettings(entity);
    int monsterLevel = settings.startingLevel() - 1;
    int maxLevel = settings.maxLevel();
    monsterLevel += (int) (settings.levelsPerDistance() * distance);
    monsterLevel += (int) Math.pow(distance, distance * settings.levelPowerPerDistance()) - 1;
    if (entity.getY() < 64) {
      double deepness = 64 - entity.getY();
      monsterLevel += (int) (settings.levelsPerDeepness() * deepness);
      monsterLevel += (int) Math.pow(deepness, deepness * settings.levelPowerPerDeepness()) - 1;
    }
    int levelBonus = settings.randomLevelBonus() + 1;
    if (levelBonus > 0) monsterLevel += entity.getRandom().nextInt(levelBonus);
    monsterLevel = Math.abs(monsterLevel);
    monsterLevel += WorldLevelingData.get((ServerLevel) entity.level()).getLevelBonus();
    if (maxLevel > 0) monsterLevel = Math.min(monsterLevel, maxLevel - 1);
    GlobalLevelingData globalLevelingData = GlobalLevelingData.get(server);
    monsterLevel += globalLevelingData.getLevelBonus();
    return monsterLevel;
  }

  @Nonnull
  private static LevelingSettings getLevelingSettings(LivingEntity entity) {
    LevelingSettings settings = EntitiesLevelingSettingsReloader.get(entity.getType());
    if (settings == null) {
      ResourceKey<Level> dimension = entity.level().dimension();
      return DimensionsLevelingSettingsReloader.get(dimension);
    }
    return settings;
  }

  public static void applyAttributeBonuses(LivingEntity entity) {
    getAttributeBonuses(entity)
        .forEach((attribute, bonus) -> applyAttributeBonus(entity, attribute, bonus));
  }

  private static Map<Attribute, AttributeModifier> getAttributeBonuses(LivingEntity entity) {
    LevelingSettings settings = getLevelingSettings(entity);
    if (settings.attributeModifiers() == null || settings.attributeModifiers().isEmpty()) {
      return Config.getAttributeBonuses();
    }
    return settings.attributeModifiers();
  }

  private static void applyAttributeBonus(
      LivingEntity entity, Attribute attribute, AttributeModifier modifier) {
    AttributeInstance attributeInstance = entity.getAttribute(attribute);
    if (attributeInstance == null) {
      return;
    }
    AttributeModifier oldModifier = attributeInstance.getModifier(modifier.getId());
    if (oldModifier != null && oldModifier.getAmount() == modifier.getAmount()) {
      return;
    }
    if (oldModifier != null) attributeInstance.removeModifier(oldModifier);
    int level = getLevel(entity);
    double amount = modifier.getAmount() * level;
    oldModifier =
        new AttributeModifier(modifier.getId(), "AutoLeveling", amount, modifier.getOperation());
    attributeInstance.addPermanentModifier(oldModifier);
    if (attribute == Attributes.MAX_HEALTH) entity.heal(entity.getMaxHealth());
  }

  public static void addEquipment(LivingEntity entity) {
    MinecraftServer server = entity.level().getServer();
    if (server == null) return;
    for (EquipmentSlot slot : EquipmentSlot.values()) {
      LootTable equipmentTable = getEquipmentLootTableForSlot(server, entity, slot);
      if (equipmentTable == LootTable.EMPTY) continue;
      LootParams lootParams = createEquipmentLootParams(entity);
      equipmentTable.getRandomItems(lootParams, itemStack -> entity.setItemSlot(slot, itemStack));
    }
  }

  private static LootTable getEquipmentLootTableForSlot(
      MinecraftServer server, LivingEntity entity, EquipmentSlot slot) {
    ResourceLocation entityId = EntityType.getKey(entity.getType());
    ResourceLocation lootTableId = getEquipmentTableId(slot, entityId);
    return server.getLootData().getLootTable(lootTableId);
  }

  @Nonnull
  private static ResourceLocation getEquipmentTableId(
      EquipmentSlot slot, ResourceLocation entityId) {
    String path = "equipment/" + entityId.getPath() + "_" + slot.getName();
    return new ResourceLocation(entityId.getNamespace(), path);
  }

  private static LootParams createLootParams(LivingEntity entity, DamageSource damageSource) {
    LivingEntityAccessor accessor = (LivingEntityAccessor) entity;
    ServerLevel level = (ServerLevel) entity.level();
    LootParams.Builder builder =
        new LootParams.Builder(level)
            .withParameter(LootContextParams.THIS_ENTITY, entity)
            .withParameter(LootContextParams.ORIGIN, entity.position())
            .withParameter(LootContextParams.DAMAGE_SOURCE, damageSource)
            .withOptionalParameter(LootContextParams.KILLER_ENTITY, damageSource.getEntity())
            .withOptionalParameter(
                LootContextParams.DIRECT_KILLER_ENTITY, damageSource.getDirectEntity());
    int lastHurtByPlayerTime = accessor.getLastHurtByPlayerTime();
    Player lastHurtByPlayer = accessor.getLastHurtByPlayer();
    if (lastHurtByPlayerTime > 0 && lastHurtByPlayer != null) {
      builder =
          builder
              .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, lastHurtByPlayer)
              .withLuck(lastHurtByPlayer.getLuck());
    }
    return builder.create(LootContextParamSets.ENTITY);
  }

  private static LootParams createEquipmentLootParams(LivingEntity entity) {
    return new LootParams.Builder((ServerLevel) entity.level())
        .withParameter(LootContextParams.THIS_ENTITY, entity)
        .withParameter(LootContextParams.ORIGIN, entity.position())
        .create(LootContextParamSets.SELECTOR);
  }

  private static boolean canHaveLevel(Entity entity) {
    if (!(entity instanceof LivingEntity)) return false;
    if (entity.getType() == EntityType.PLAYER) return false;
    ResourceLocation entityId = EntityType.getKey(entity.getType());
    String entityNamespace = entityId.getNamespace();
    List<String> blacklist = Config.COMMON.blacklistedMobs.get();
    if (blacklist.contains(entityNamespace + ":*")) return false;
    List<String> whitelist = Config.COMMON.whitelistedMobs.get();
    if (whitelist.contains(entityNamespace + ":*")) return true;
    if (blacklist.contains(entityId.toString())) return false;
    if (!whitelist.isEmpty()) return whitelist.contains(entityId.toString());
    return true;
  }

  public static boolean shouldShowLevel(Entity entity) {
    ResourceLocation entityId = EntityType.getKey(entity.getType());
    List<String> blacklist = Config.COMMON.blacklistedShownLevels.get();
    if (blacklist.contains(entityId.toString())) return false;
    String namespace = entityId.getNamespace();
    return !blacklist.contains(namespace + ":*");
  }

  public static boolean hasLevel(Entity entity) {
    return entity.getPersistentData().contains(LEVEL_TAG);
  }

  public static int getLevel(LivingEntity entity) {
    return entity.getPersistentData().getInt(LEVEL_TAG);
  }

  public static void setLevel(LivingEntity entity, int level) {
    entity.getPersistentData().putInt(LEVEL_TAG, level);
  }
}
