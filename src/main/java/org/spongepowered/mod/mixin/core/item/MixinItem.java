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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.primitives.Ints;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.RegistryNamespaced;
import org.apache.logging.log4j.Level;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.util.PrettyPrinter;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.registry.type.ItemTypeRegistryModule;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import javax.annotation.Nullable;

@Mixin(Item.class)
public abstract class MixinItem implements ItemType {


    @Shadow @Final public static RegistryNamespaced<ResourceLocation, Item> REGISTRY;

    @Shadow public static Item getItemById(int id) {
        return null; // Shadowed
    }

    private static final Set<String> ITEM_IDS_NOT_FOUND = new ConcurrentSkipListSet<>();

    @Inject(method = "registerItem(ILnet/minecraft/util/ResourceLocation;Lnet/minecraft/item/Item;)V", at = @At("RETURN"))
    private static void registerMinecraftItem(int id, ResourceLocation name, Item item, CallbackInfo ci) {
        final Item registered;
        final ResourceLocation nameForObject = REGISTRY.getNameForObject(item);
        if (nameForObject == null) {
            registered = checkNotNull(REGISTRY.getObject(name), "Someone replaced a vanilla item with a null item!!!");
        } else {
            registered = item;
        }
        ItemTypeRegistryModule.getInstance().registerAdditionalCatalog((ItemType) registered);
    }

    /**
     * @author gabizou April 7th, 2017
     * @reason Adds a printout of item id's that are failing to be found either by the
     * registry, or numerical id (usually because by numerical id, it's going to simply
     * be a string named id, but it was never registered to begin with).
     *
     * @param id The string id
     * @return The item, or null
     */
    @Nullable
    @Overwrite
    public static Item getByNameOrId(String id) {
        Item item = REGISTRY.getObject(new ResourceLocation(id));

        if (item == null) {
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
                return getItemById(itemId);
                // Sponge End
            } catch (Exception e) {
                // do nothing
            }
        }

        return item;
    }
}
