package daripher.autoleveling.item;

import java.util.List;

import daripher.autoleveling.config.Config;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

public class WhitelistToolItem extends Item {
	public WhitelistToolItem() {
		super(new Properties().stacksTo(1));
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack itemStack, Player player, LivingEntity entity, InteractionHand hand) {
		if (!player.level().isClientSide) whitelistEntity(player, entity);
		return InteractionResult.SUCCESS;
	}

	protected void whitelistEntity(Player player, LivingEntity entity) {
		String id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).toString();
		List<String> whitelist = Config.COMMON.whitelistedMobs.get();
		if (whitelist.contains(id)) {
			whitelist.remove(id);
			player.sendSystemMessage(Component.translatable(getDescriptionId() + ".removed", id));
		} else {
			whitelist.add(id);
			player.sendSystemMessage(Component.translatable(getDescriptionId() + ".added", id));
		}
		Config.COMMON.whitelistedMobs.set(whitelist);
	}

	@Override
	public void appendHoverText(ItemStack itemStack, Level level, List<Component> components, TooltipFlag tooltipFlag) {
		components.add(Component.translatable(getDescriptionId() + ".tooltip"));
	}
}
