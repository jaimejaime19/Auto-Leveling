package daripher.autoleveling.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import daripher.autoleveling.capability.LevelingDataProvider;
import daripher.autoleveling.client.LeveledMobsTextures;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.client.renderer.entity.model.EntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ResourceLocation;

@Mixin(LivingRenderer.class)
public class MixinLivingEntityRenderer<T extends LivingEntity, M extends EntityModel<T>> {
	@Shadow
	protected M model;

	@Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
	protected void injectGetRenderType(T entity, boolean visible, boolean invisibleToPlayer, boolean glowing, CallbackInfoReturnable<RenderType> callbackInfo) {
		LevelingDataProvider.getLevelingData(entity).ifPresent(levelingData -> {
			ResourceLocation texture = LeveledMobsTextures.get(entity.getType(), levelingData.getLevel() + 1);

			if (texture != null) {
				if (invisibleToPlayer) {
					callbackInfo.setReturnValue(RenderType.itemEntityTranslucentCull(texture));
				} else if (visible) {
					callbackInfo.setReturnValue(model.renderType(texture));
				} else {
					callbackInfo.setReturnValue(glowing ? RenderType.outline(texture) : null);
				}
			}
		});
	}
}
