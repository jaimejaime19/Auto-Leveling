package daripher.autoleveling.init;

import daripher.autoleveling.AutoLevelingMod;
import javax.annotation.Nonnull;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;

public class AutoLevelingCreativeTabs {
  public static final ItemGroup AUTO_LEVELING =
      new ItemGroup(AutoLevelingMod.MOD_ID) {
        @Nonnull
        @Override
        public ItemStack makeIcon() {
          return new ItemStack(AutoLevelingItems.BLACKLIST_TOOL.get());
        }
      };
}
