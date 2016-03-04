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
package org.spongepowered.common.mixin.core.world;

import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.IChunkGenerator;
import org.spongepowered.api.service.context.Context;
import org.spongepowered.api.util.annotation.NonnullByDefault;
import org.spongepowered.api.world.Dimension;
import org.spongepowered.api.world.DimensionType;
import org.spongepowered.api.world.GeneratorType;
import org.spongepowered.api.world.GeneratorTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.common.config.SpongeConfig;
import org.spongepowered.common.interfaces.world.IMixinWorldProvider;
import org.spongepowered.common.interfaces.world.IMixinWorldType;
import org.spongepowered.common.registry.type.world.DimensionRegistryModule;
import org.spongepowered.common.world.DimensionManager;

import javax.annotation.Nullable;

@NonnullByDefault
@Mixin(WorldProvider.class)
public abstract class MixinWorldProvider implements Dimension, IMixinWorldProvider {

    private boolean allowPlayerRespawns;
    private SpongeConfig<SpongeConfig.DimensionConfig> dimensionConfig;
    private volatile Context dimContext;

    @Shadow protected World worldObj;
    @Shadow public abstract net.minecraft.world.DimensionType getDimensionType();
    @Shadow protected boolean isHellWorld;
    @Shadow public WorldType terrainType;
    @Shadow protected boolean hasNoSky;
    @Shadow private String generatorSettings;
    @Shadow public abstract IChunkGenerator func_186060_c();

    @Override
    public IChunkGenerator createChunkGenerator(String settings) {
        this.generatorSettings = settings;
        return this.func_186060_c();
    }

    @Override
    public String getName() {
        return this.getDimensionType().getName();
    }

    @Override
    public boolean allowsPlayerRespawns() {
        return this.allowPlayerRespawns;
    }

    @Override
    public void setAllowsPlayerRespawns(boolean allow) {
        this.allowPlayerRespawns = allow;
    }

    @Override
    public int getMinimumSpawnHeight() {
        return this.getAverageGroundLevel();
    }

// TODO 1.9 Update - This may not be needed anymore --Zidane
//    public boolean canCoordinateBeSpawn(int x, int z) {
//        if (this.terrainType.equals(GeneratorTypes.THE_END)) {
//            return this.worldObj.getGroundAboveSeaLevel(new BlockPos(x, 0, z)).getMaterial().blocksMovement();
//        } else {
//            return this.worldObj.getGroundAboveSeaLevel(new BlockPos(x, 0, z)) == Blocks.grass;
//        }
//    }

    @Override
    public boolean doesWaterEvaporate() {
        return this.isHellWorld;
    }

    @Override
    public void setWaterEvaporates(boolean evaporates) {
        this.isHellWorld = evaporates;
    }

    @Override
    public boolean hasSky() {
        return !getHasNoSky();
    }

    public boolean getHasNoSky() {
        return this.terrainType.equals(GeneratorTypes.NETHER) || this.hasNoSky;
    }

    @Override
    public DimensionType getType() {
        return DimensionRegistryModule.getInstance().fromProviderId(DimensionManager.getProviderType(this.getDimensionType().getId()));
    }

    @Override
    @Nullable
    public String getSaveFolder() {
        return (this.getDimensionType().getId() == 0 ? null : "DIM" + this.getDimensionType().getId());
    }

    @Override
    public void setDimension(int dim) {
        //this.dimensionId = dim;
    }

    @Override
    public void setDimensionConfig(SpongeConfig<SpongeConfig.DimensionConfig> config) {
        this.dimensionConfig = config;
    }

    @Override
    public SpongeConfig<SpongeConfig.DimensionConfig> getDimensionConfig() {
        return this.dimensionConfig;
    }

    @Override
    public Context getContext() {
        if (this.dimContext == null) {
            this.dimContext = new Context(Context.DIMENSION_KEY, getName());
        }
        return this.dimContext;
    }

    @Inject(method = "getProviderForDimension", at = @At("HEAD"), cancellable = true)
    private static void onGetProvider(int dimension, CallbackInfoReturnable<WorldProvider> callbackInfoReturnable) {
        WorldProvider provider = DimensionManager.createProviderFor(dimension);
        DimensionRegistryModule.getInstance().validateProvider(provider);
        Dimension dim = (Dimension) provider;
        dim.setAllowsPlayerRespawns(provider.canRespawnHere());
        callbackInfoReturnable.setReturnValue(provider);
        callbackInfoReturnable.cancel();
    }

    @Override
    public int getAverageGroundLevel() {
        return ((IMixinWorldType) this.terrainType).getMinimumSpawnHeight(this.worldObj);
    }

    @Override
    public GeneratorType getGeneratorType() {
        return (GeneratorType) this.terrainType;
    }
}
