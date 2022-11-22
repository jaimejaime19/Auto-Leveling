package daripher.autoleveling.item;

import java.util.List;

import daripher.autoleveling.config.Config;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.Util;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.registries.ForgeRegistries;

public class BlacklistToolItem extends AutoLevelingToolItem
{
	@Override
	public ActionResultType interactLivingEntity(ItemStack itemStack, PlayerEntity player, LivingEntity entity, Hand hand)
	{
		if (!player.level.isClientSide)
		{
			String entityId = ForgeRegistries.ENTITIES.getKey(entity.getType()).toString();
			List<String> blacklistedEntities = Config.COMMON.blacklistedMobs.get();
			
			if (blacklistedEntities.contains(entityId))
			{
				blacklistedEntities.remove(entityId);
				player.sendMessage(new TranslationTextComponent(getDescriptionId() + ".removed", entityId), Util.NIL_UUID);
			}
			else
			{
				blacklistedEntities.add(entityId);
				player.sendMessage(new TranslationTextComponent(getDescriptionId() + ".added", entityId), Util.NIL_UUID);
			}
			
			Config.COMMON.blacklistedMobs.set(blacklistedEntities);
		}
		
		return ActionResultType.SUCCESS;
	}
	
	@Override
	public void appendHoverText(ItemStack itemStack, World level, List<ITextComponent> components, ITooltipFlag tooltipFlag)
	{
		components.add(new TranslationTextComponent(getDescriptionId() + ".tooltip"));
	}
}
