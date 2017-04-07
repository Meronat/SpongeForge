/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.mod.mixin.core.item;

import com.google.common.primitives.Ints;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import org.apache.logging.log4j.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.util.PrettyPrinter;
import org.spongepowered.common.SpongeImpl;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

@Mixin(net.minecraft.item.ItemStack.class)
public abstract class MixinItemStack {

    @Shadow private net.minecraft.item.Item item;
    @Shadow public abstract net.minecraft.item.Item getItem();

    private static final Set<String> ITEM_IDS_NOT_FOUND = new ConcurrentSkipListSet<>();
    // Disable Forge PlaceEvent patch as we handle this in World setBlockState

    /**
     * @author blood - October 7th, 2015
     * @reason Rewrites the method to vanilla logic where we handle events.
     * The forge event is thrown within our system.
     *
     * @param playerIn The player using the item
     * @param worldIn The world the item is being used
     * @param pos The position of the block being interacted with
     * @param side The side of the block
     * @param hitX The hit position from 0 to 1 of the face of the block
     * @param hitY The hit position from 0 to 1 on the depth of the block
     * @param hitZ The hit position from 0 to 1 on the height of the block
     * @return True if the use was successful
     */
    @Overwrite
    public EnumActionResult onItemUse(EntityPlayer playerIn, World worldIn, BlockPos pos, EnumHand hand, EnumFacing
            side, float hitX, float hitY, float hitZ) {
        final EnumActionResult result = this.getItem().onItemUse((net.minecraft.item.ItemStack)(Object)this, playerIn, worldIn, pos, hand, side, hitX,
                hitY, hitZ);

        if (result == EnumActionResult.SUCCESS) {
            playerIn.addStat(StatList.getObjectUseStats(this.item));
        }

        return result;
    }

    @Redirect(method = "setItem", at = @At(value = "FIELD", target = "Lnet/minecraft/item/ItemStack;item:Lnet/minecraft/item/Item;"))
    private Item spongeVerifyItemSet(ItemStack thisStack, Item item) {
        final String id = item.getRegistryName().toString();
        Item foundItem = Item.REGISTRY.getObject(new ResourceLocation(id));

        if (foundItem == null) {
            try {
                // Sponge Start - Use Ints.tryParse instead of Integer.parseInt to avoid an exception
                // return getItemById(Integer.parseInt(id));
                final Integer itemId = Ints.tryParse(id);
                if (itemId == null) {
                    // Since no exception was printed out, we can safely say
                    // that the id is alphanumerical, not just numerical.
                    if (!ITEM_IDS_NOT_FOUND.contains(id)) {
                        ITEM_IDS_NOT_FOUND.add(id);
                        // now we can print it to console
                        new PrettyPrinter(60).add("Non Registered Item Id Found").centre().hr()
                                .addWrapped("Sponge has detected that an invalid Item id has been found, attempting to "
                                            + "be deserialized. The issue is that the Item by the id is not "
                                            + "registered with Forge, which causes exceptions to be thrown "
                                            + "when entities have that item added and when they are deserialized.")
                                .add()
                                .add("This is what Sponge found:")
                                .add("%s : %s", "Item Id attempting to be located", id)
                                .add()
                                .add("Printing stacktrace")
                                .add(new Exception())
                                .trace(System.err, SpongeImpl.getLogger(), Level.ERROR);
                    }
                    return null;
                }
                return Item.getItemById(itemId);
                // Sponge End
            } catch (Exception e) {
                // do nothing
            }
        }

        return foundItem;
    }

}
