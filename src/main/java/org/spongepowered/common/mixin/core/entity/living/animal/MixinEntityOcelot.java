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
package org.spongepowered.common.mixin.core.entity.living.animal;

import net.minecraft.entity.passive.EntityOcelot;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.api.data.manipulator.mutable.entity.OcelotData;
import org.spongepowered.api.entity.living.animal.Ocelot;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.util.annotation.NonnullByDefault;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.spongepowered.common.Sponge;

import java.util.Random;

@NonnullByDefault
@Mixin(EntityOcelot.class)
public abstract class MixinEntityOcelot extends MixinEntityAnimal implements Ocelot {

    private ItemStack currentItemStack;

    @Override
    public OcelotData getOcelotData() {
        return get(OcelotData.class).get();
    }

    @Inject(method = "interact", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/passive/EntityOcelot;isTamed()Z", shift = At.Shift.BEFORE, ordinal = 0), locals = LocalCapture.CAPTURE_FAILHARD)
    public void afterGetCurrentItem(EntityPlayer player, CallbackInfoReturnable<Boolean> cir, ItemStack currentItemStack) {
        this.currentItemStack = currentItemStack.copy();
    }

    @Redirect(method = "interact", at = @At(value = "INVOKE", target = "Ljava/util/Random;nextInt(I)I", args = "log=true", ordinal = 0))
    public int onTame(Random rand, int bound, EntityPlayer player) {
        int random = rand.nextInt(bound);
        if (random == 0 && !Sponge.getGame().getEventManager().post(SpongeEventFactory.createTameEntityEvent(Sponge.getGame(), Cause.of(player, this.currentItemStack), this))) {
            this.currentItemStack = null;
            return random;
        }
        this.currentItemStack = null;
        return 1;
    }
}
