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
package org.spongepowered.common.world.schematic;

import com.mojang.datafixers.DataFixer;
import io.leangen.geantyref.TypeToken;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.ResourceKey;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.entity.BlockEntityArchetype;
import org.spongepowered.api.block.entity.BlockEntityType;
import org.spongepowered.api.data.persistence.DataContainer;
import org.spongepowered.api.data.persistence.DataContentUpdater;
import org.spongepowered.api.data.persistence.DataQuery;
import org.spongepowered.api.data.persistence.DataTranslator;
import org.spongepowered.api.data.persistence.DataView;
import org.spongepowered.api.data.persistence.InvalidDataException;
import org.spongepowered.api.entity.EntityType;
import org.spongepowered.api.registry.Registry;
import org.spongepowered.api.registry.RegistryType;
import org.spongepowered.api.registry.RegistryTypes;
import org.spongepowered.api.world.biome.Biome;
import org.spongepowered.api.world.schematic.Palette;
import org.spongepowered.api.world.schematic.PaletteType;
import org.spongepowered.api.world.schematic.PaletteTypes;
import org.spongepowered.api.world.schematic.Schematic;
import org.spongepowered.api.world.volume.archetype.entity.EntityArchetypeEntry;
import org.spongepowered.api.world.volume.biome.BiomeVolume;
import org.spongepowered.api.world.volume.block.BlockVolume;
import org.spongepowered.common.SpongeCommon;
import org.spongepowered.common.block.BlockStateSerializerDeserializer;
import org.spongepowered.common.block.entity.SpongeBlockEntityArchetypeBuilder;
import org.spongepowered.common.data.persistence.schematic.SchematicUpdater1_to_2;
import org.spongepowered.common.entity.SpongeEntityArchetypeBuilder;
import org.spongepowered.common.util.Constants;
import org.spongepowered.common.world.volume.buffer.archetype.SpongeArchetypeVolume;
import org.spongepowered.common.world.volume.buffer.block.ArrayMutableBlockBuffer;
import org.spongepowered.math.vector.Vector3d;
import org.spongepowered.math.vector.Vector3i;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SchematicTranslator implements DataTranslator<Schematic> {

    private static final SchematicTranslator INSTANCE = new SchematicTranslator();
    private static final TypeToken<Schematic> TYPE_TOKEN = TypeToken.get(Schematic.class);

    private static final ConcurrentSkipListSet<String> MISSING_MOD_IDS = new ConcurrentSkipListSet<>();

    private static final DataContentUpdater V1_TO_2 = new SchematicUpdater1_to_2();
    private static final ResourceKey CATALOG_KEY = ResourceKey.sponge("schematic");

    @Nullable private static DataFixer VANILLA_FIXER;

    public static SchematicTranslator get() {
        return SchematicTranslator.INSTANCE;
    }

    private SchematicTranslator() {

    }


    @Override
    public TypeToken<Schematic> getToken() {
        return SchematicTranslator.TYPE_TOKEN;
    }

    @Override
    public Schematic translate(DataView unprocessed) throws InvalidDataException {
        if (SchematicTranslator.VANILLA_FIXER == null) {
            SchematicTranslator.VANILLA_FIXER = SpongeCommon.getServer().getFixerUpper();
        }
        final int version = unprocessed.getInt(Constants.Sponge.Schematic.VERSION).get();
        // TODO version conversions

        if (version > Constants.Sponge.Schematic.CURRENT_VERSION) {
            throw new InvalidDataException(String.format("Unknown schematic version %d (current version is %d)", version, Constants.Sponge.Schematic.CURRENT_VERSION));
        } else if (version == 1) {
            unprocessed = SchematicTranslator.V1_TO_2.update(unprocessed);
        }
        { // Strictly for loading tile entities when the format wasn't finalized yet.
            final List<DataView> dataViews = unprocessed.getViewList(Constants.Sponge.Schematic.Versions.V1_TILE_ENTITY_DATA).orElse(null);
            if (dataViews != null) {
                unprocessed.remove(Constants.Sponge.Schematic.Versions.V1_TILE_ENTITY_DATA);
                unprocessed.set(Constants.Sponge.Schematic.BLOCKENTITY_DATA, dataViews);
            }
        }
        final int dataVersion = unprocessed.getInt(Constants.Sponge.Schematic.DATA_VERSION).get();
        // DataFixer will be able to upgrade entity and tile entity data if and only if we're running a valid server and
        // the data version is outdated.
        // Don't run fixers for now
//        final boolean needsFixers = dataVersion < Constants.MINECRAFT_DATA_VERSION && SchematicTranslator.VANILLA_FIXER != null;
        final DataView updatedView = unprocessed;

        final @Nullable DataView metadata = updatedView.getView(Constants.Sponge.Schematic.METADATA).orElse(null);
        if (metadata != null) {
            final Optional<DataView> dot_data = metadata.getView(DataQuery.of("."));
            if (dot_data.isPresent()) {
                final DataView data = dot_data.get();
                for (final DataQuery key : data.getKeys(false)) {
                    if (!metadata.contains(key)) {
                        metadata.set(key, data.get(key).get());
                    }
                }
            }
        }
        if (metadata != null) {
            final String schematicName = metadata.getString(Constants.Sponge.Schematic.NAME).orElse("unknown");
            metadata.getStringList(Constants.Sponge.Schematic.REQUIRED_MODS).ifPresent(mods -> {
                for (final String modId : mods) {
                    if (!Sponge.getPluginManager().getPlugin(modId).isPresent()) {
                        if (SchematicTranslator.MISSING_MOD_IDS.add(modId)) {
                            SpongeCommon
                                .getLogger().warn("When attempting to load the Schematic: " + schematicName + " there is a missing modid: " + modId + " some blocks/tiles/entities may not load correctly.");
                        }
                    }
                }
            });
        }

        // TODO error handling for these optionals
        final int width = updatedView.getShort(Constants.Sponge.Schematic.WIDTH).get();
        final int height = updatedView.getShort(Constants.Sponge.Schematic.HEIGHT).get();
        final int length = updatedView.getShort(Constants.Sponge.Schematic.LENGTH).get();
        if (width > Constants.Sponge.Schematic.MAX_SIZE || height > Constants.Sponge.Schematic.MAX_SIZE || length > Constants.Sponge.Schematic.MAX_SIZE) {
            throw new InvalidDataException(String.format("Schematic is larger than maximum allowable size (found: (%d, %d, %d) max: (%d, %<d, %<d)",
                    width, height, length, Constants.Sponge.Schematic.MAX_SIZE));
        }

        final int[] offset = (int[]) updatedView.get(Constants.Sponge.Schematic.OFFSET).orElse(new int[3]);
        if (offset.length != 3) {
            throw new InvalidDataException("Schematic offset was not of length 3");
        }
        final Palette<BlockState, BlockType> palette;
        final Optional<DataView> paletteData = updatedView.getView(Constants.Sponge.Schematic.BLOCK_PALETTE);
        final int palette_max = updatedView.getInt(Constants.Sponge.Schematic.BLOCK_PALETTE_MAX).orElse(0xFFFF);
        if (paletteData.isPresent()) {
            // If we had a default palette_max we don't want to allocate all
            // that space for nothing so we use a sensible default instead
            final MutableBimapPalette<BlockState, BlockType> bimap = new MutableBimapPalette<>(
                PaletteTypes.BLOCK_STATE_PALETTE.get(),
                Sponge.getGame().registries().registry(RegistryTypes.BLOCK_TYPE),
                RegistryTypes.BLOCK_TYPE,
                palette_max != 0xFFFF ? palette_max : 64
            );
            palette = bimap;
            final DataView paletteMap = paletteData.get();
            final Set<DataQuery> paletteKeys = paletteMap.getKeys(false);
            for (final DataQuery key : paletteKeys) {
                final BlockState state = BlockStateSerializerDeserializer.deserialize(key.getParts().get(0))
                    .orElseGet(() -> BlockTypes.BEDROCK.get().getDefaultState());
                bimap.assign(state, paletteMap.getInt(key).get());
            }
        } else {
            palette = PaletteTypes.BLOCK_STATE_PALETTE.get().create(Sponge.getGame().registries(), RegistryTypes.BLOCK_TYPE);
        }

        final Palette<Biome, Biome> biomePalette;
        final Optional<DataView> biomePaletteData = updatedView.getView(Constants.Sponge.Schematic.BIOME_PALETTE);
        final int biome_max = updatedView.getInt(Constants.Sponge.Schematic.BIOME_PALETTE_MAX).orElse(0xFFFF);
        if (biomePaletteData.isPresent()) {
            final MutableBimapPalette<Biome, Biome> bimap = new MutableBimapPalette<>(
                PaletteTypes.BIOME_PALETTE.get(),
                Sponge.getGame().registries().registry(RegistryTypes.BIOME),
                RegistryTypes.BIOME,
                biome_max != 0xFFF ? palette_max : 64);
            biomePalette = bimap;
            final DataView biomeMap = biomePaletteData.get();
            final Set<DataQuery> biomeKeys = biomeMap.getKeys(false);
            for (final DataQuery biomeKey : biomeKeys) {
                final ResourceKey key = ResourceKey.resolve(biomeKey.getParts().get(0));
                final Biome biome = Sponge.getGame().registries().registry(RegistryTypes.BIOME).findValue(key).get();
                bimap.assign(biome, biomeMap.getInt(biomeKey).get());
            }
        } else {
            biomePalette = PaletteTypes.BIOME_PALETTE.get().create(Sponge.getGame().registries(), RegistryTypes.BIOME);
        }

        final SpongeArchetypeVolume archetypeVolume = new SpongeArchetypeVolume(new Vector3i(-offset[0], -offset[1], -offset[2]), new Vector3i(width, height, length), Sponge.getGame().registries());
        final SpongeSchematicBuilder builder = new SpongeSchematicBuilder();

        final BlockVolume.Mutable<ArrayMutableBlockBuffer> buffer = new ArrayMutableBlockBuffer(new Vector3i(-offset[0], -offset[1], -offset[2]), new Vector3i(width, height, length));

        final byte[] blockData = (byte[]) updatedView.get(Constants.Sponge.Schematic.BLOCK_DATA)
            .orElseThrow(() -> new InvalidDataException("Missing BlockData for Schematic"));
        SchematicTranslator.readByteArrayData(width, (width * length), offset, palette, blockData, archetypeVolume, BlockVolume.Mutable::setBlock);

        updatedView.get(Constants.Sponge.Schematic.BIOME_DATA).ifPresent(biomesObj -> {
            final byte[] biomes = (byte[]) biomesObj;
            SchematicTranslator.readByteArrayData(width, (width * length), offset, biomePalette, biomes, archetypeVolume,
                BiomeVolume.Mutable::setBiome
            );
        });

        updatedView.getViewList(Constants.Sponge.Schematic.BLOCKENTITY_DATA)
            .ifPresent(tileData ->
                tileData.forEach(blockEntityData -> {
                        final int[] pos = (int[]) blockEntityData.get(Constants.Sponge.Schematic.BLOCKENTITY_POS)
                            .orElseThrow(() -> new IllegalStateException("Schematic not abiding by format, all BlockEntities must have an x y z pos"));
                        blockEntityData.getString(Constants.Sponge.Schematic.BLOCKENTITY_ID)
                            .map(ResourceKey::resolve)
                            .map(key -> Sponge.getGame().registries().registry(RegistryTypes.BLOCK_ENTITY_TYPE).findValue(key))
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .ifPresent(type -> {
                                final int x = pos[0] - offset[0];
                                final int y = pos[1] - offset[1];
                                final int z = pos[2] - offset[2];
                                final BlockEntityArchetype archetype = new SpongeBlockEntityArchetypeBuilder()
                                    .state(archetypeVolume.getBlock(x, y, z))
                                    .blockEntityData(blockEntityData)
                                    .blockEntity(type)
                                    .build();
                                archetypeVolume.addBlockEntity(x, y, z, archetype);
                            });
                    }
                )
            );
        updatedView.getViewList(Constants.Sponge.Schematic.ENTITIES)
            .map(List::stream)
            .orElse(Stream.of())
            .filter(entity -> entity.contains(Constants.Sponge.Schematic.ENTITIES_POS, Constants.Sponge.Schematic.ENTITIES_ID))
            .map(view -> {
                final String typeId = view.getString(Constants.Sponge.Schematic.ENTITIES_ID).get();
                final ResourceKey key = ResourceKey.resolve(typeId);
                final Optional<EntityType<@NonNull ?>> entityType = Sponge.getGame().registries().registry(
                    RegistryTypes.ENTITY_TYPE).findValue(key);
                return entityType.map(type -> {
                    final double[] pos = (double[]) view.get(Constants.Sponge.Schematic.ENTITIES_POS)
                        .orElseThrow(() -> new IllegalStateException("Schematic not abiding by format, all BlockEntities must have an x y z pos"));
                    return EntityArchetypeEntry.of(new SpongeEntityArchetypeBuilder()
                        .type(type)
                        .entityData(view)
                        .build(), new Vector3d(pos[0], pos[1], pos[2]));
                });
            }).filter(Optional::isPresent)
            .map(Optional::get)
            .forEach(archetypeVolume::addEntity);


        if (metadata != null) {
            final DataContainer meta = DataContainer.createNew(DataView.SafetyMode.NO_DATA_CLONED);
            for (final DataQuery key : metadata.getKeys(false)) {
                meta.set(key, metadata.get(key).get());
            }
            builder.metadata(meta);
        }

        builder.volume(archetypeVolume);
        return builder.build();
    }

    static interface PostSetter<V, T> {

        void apply(V volume, int x, int y, int z, T type);
    }

    private static <Buffer, Type, ParentType> void readByteArrayData(
        final int width,
        final int i1,
        final int[] offset,
        final Palette<Type, ParentType> palette,
        final byte[] data,
        final Buffer buffer,
        final PostSetter<Buffer, Type> setter
    ) {
        int index = 0;
        int i = 0;
        int value = 0;
        int varint_length = 0;
        while (i < data.length) {
            value = 0;
            varint_length = 0;

            while (true) {
                value |= (data[i] & 127) << (varint_length++ * 7);
                if (varint_length > 5) {
                    throw new RuntimeException("VarInt too big (probably corrupted data)");
                }
                if ((data[i] & 128) != 128) {
                    i++;
                    break;
                }
                i++;
            }
            // index = (y * length + z) * width + x
            final int y = index / i1;
            final int z = (index % i1) / width;
            final int x = (index % i1) % width;
            final Type state = palette.get(value, Sponge.getGame().registries()).get();
            setter.apply(buffer, x - offset[0], y - offset[1], z - offset[2], state);

            index++;
        }
    }

    @Override
    public DataContainer translate(final Schematic schematic) throws InvalidDataException {
        final DataContainer data = DataContainer.createNew(DataView.SafetyMode.NO_DATA_CLONED);
        this.addTo(schematic, data);
        return data;
    }

    @Override
    public DataView addTo(final Schematic schematic, final DataView data) {
        final int xMin = schematic.getBlockMin().getX();
        final int yMin = schematic.getBlockMin().getY();
        final int zMin = schematic.getBlockMin().getZ();
        final int width = schematic.getBlockSize().getX();
        final int height = schematic.getBlockSize().getY();
        final int length = schematic.getBlockSize().getZ();
        if (width > Constants.Sponge.Schematic.MAX_SIZE || height > Constants.Sponge.Schematic.MAX_SIZE || length > Constants.Sponge.Schematic.MAX_SIZE) {
            throw new IllegalArgumentException(String.format(
                    "Schematic is larger than maximum allowable size (found: (%d, %d, %d) max: (%d, %<d, %<d)", width, height, length, Constants.Sponge.Schematic.MAX_SIZE));
        }
        data.set(Constants.Sponge.Schematic.WIDTH, width);
        data.set(Constants.Sponge.Schematic.HEIGHT, height);
        data.set(Constants.Sponge.Schematic.LENGTH, length);

        data.set(Constants.Sponge.Schematic.VERSION, Constants.Sponge.Schematic.CURRENT_VERSION);
        data.set(Constants.Sponge.Schematic.DATA_VERSION, Constants.MINECRAFT_DATA_VERSION);
        for (final DataQuery metaKey : schematic.getMetadata().getKeys(false)) {
            data.set(Constants.Sponge.Schematic.METADATA.then(metaKey), schematic.getMetadata().get(metaKey).get());
        }
        final Set<String> requiredMods = new HashSet<>();

        final int[] offset = new int[] {-xMin, -yMin, -zMin};
        data.set(Constants.Sponge.Schematic.OFFSET, offset);

        final Palette.Mutable<BlockState, BlockType> palette = schematic.getBlockPalette().asMutable(Sponge.getGame().registries());
        try (final ByteArrayOutputStream buffer = new ByteArrayOutputStream(width * height * length)) {
            for (int y = 0; y < height; y++) {
                final int y0 = yMin + y;
                for (int z = 0; z < length; z++) {
                    final int z0 = zMin + z;
                    for (int x = 0; x < width; x++) {
                        final int x0 = xMin + x;
                        final BlockState state = schematic.getBlock(x0, y0, z0);
                        SchematicTranslator.writeIdToBuffer(buffer, palette.getOrAssign(state));
                    }
                }
            }

            data.set(Constants.Sponge.Schematic.BLOCK_DATA, buffer.toByteArray());
        } catch (final IOException e) {
            // should never reach here
        }

        final Palette.Mutable<Biome, Biome> biomePalette = schematic.getBiomePalette().asMutable(Sponge.getGame().registries());

        try (final ByteArrayOutputStream buffer = new ByteArrayOutputStream(width * height * length)) {
            for (int y = 0; y < height; y++) {
                final int y0 = yMin + y;
                for (int z = 0; z < length; z++) {
                    final int z0 = zMin + z;
                    for (int x = 0; x < width; x++) {
                        final int x0 = xMin + x;
                        final Biome state = schematic.getBiome(x0, y0, z0);
                        SchematicTranslator.writeIdToBuffer(buffer, biomePalette.getOrAssign(state));
                    }

                }
            }

            data.set(Constants.Sponge.Schematic.BIOME_DATA, buffer.toByteArray());
        } catch (final IOException e) {
            // Should never reach here.
        }

        SchematicTranslator.writePaletteToView(data, palette, schematic.getBlockStateRegistry(), Constants.Sponge.Schematic.BLOCK_PALETTE, BlockState::getType, requiredMods);
        data.set(Constants.Sponge.Schematic.BLOCK_PALETTE_MAX, palette.getHighestId());

        SchematicTranslator.writePaletteToView(data, biomePalette, schematic.getBiomeRegistry(), Constants.Sponge.Schematic.BIOME_PALETTE, Function.identity(), requiredMods);
        data.set(Constants.Sponge.Schematic.BIOME_PALETTE_MAX, biomePalette.getHighestId());


        final Registry<BlockEntityType> blockEntityRegistry = Sponge.getGame().registries().registry(
            RegistryTypes.BLOCK_ENTITY_TYPE);
        final Palette.Mutable<BlockEntityType, BlockEntityType> blockEntityTypePalette = new MutableBimapPalette<>(
            PaletteTypes.BLOCK_ENTITY_PALETTE.get(),
            blockEntityRegistry, RegistryTypes.BLOCK_ENTITY_TYPE
        );
        final List<DataView> blockEntities = schematic.getBlockEntityArchetypes().entrySet().stream().map(entry -> {
            final Vector3i pos = entry.getKey();
            final BlockEntityArchetype archetype = entry.getValue();
            final DataContainer entityData = archetype.getBlockEntityData();
            final int[] apos = new int[] {pos.getX() - xMin, pos.getY() - yMin, pos.getZ() - zMin};
            entityData.set(Constants.Sponge.Schematic.BLOCKENTITY_POS, apos);
            blockEntityTypePalette.getOrAssign(archetype.getBlockEntityType());
            return entityData;
        }).collect(Collectors.toList());

        SchematicTranslator.writePaletteToView(data, blockEntityTypePalette, blockEntityRegistry, Constants.Sponge.Schematic.BLOCK_ENTITY_PALETTE, Function.identity(), requiredMods);
        data.set(Constants.Sponge.Schematic.BLOCKENTITY_DATA, blockEntities);

        final Registry<EntityType<?>> entityRegistry = Sponge.getGame().registries().registry(RegistryTypes.ENTITY_TYPE);
        final Palette.Mutable<EntityType<@NonNull ?>, EntityType<@NonNull ?>> entityTypePalette = new MutableBimapPalette<>(
            PaletteType.<EntityType<@NonNull ?>, EntityType<@NonNull ?>>builder()
                .resolver((str, registry) -> registry.findValue(ResourceKey.resolve(str)))
                .stringifier((registry, obj) -> registry.findValueKey(obj)
                    .orElseThrow(() -> new IllegalArgumentException("Unregistered EntityType"))
                    .asString()
                )
            .build(),
            entityRegistry,
            RegistryTypes.ENTITY_TYPE
        );
        final List<DataView> entities = schematic.getEntityArchetypesByPosition().stream().map(entry -> {
            final DataContainer entityData = entry.getArchetype().getEntityData();
            entityTypePalette.getOrAssign(entry.getArchetype().getType());

            final List<Double> entityPosition = new ArrayList<>();
            entityPosition.add(entry.getPosition().getX());
            entityPosition.add(entry.getPosition().getY());
            entityPosition.add(entry.getPosition().getZ());
            entityData.set(Constants.Sponge.Schematic.ENTITIES_POS, entityPosition);
            return entityData;
        }).collect(Collectors.toList());
        SchematicTranslator.writePaletteToView(data, entityTypePalette, entityRegistry, Constants.Sponge.Schematic.ENTITY_TYPE_PALETTE, Function.identity(), requiredMods);

        data.set(Constants.Sponge.Schematic.ENTITIES, entities);

        if (!requiredMods.isEmpty()) {
            data.set(Constants.Sponge.Schematic.METADATA.then(Constants.Sponge.Schematic.REQUIRED_MODS), requiredMods);
        }

        return data;
    }

    private static <T, P> void writePaletteToView(
        final DataView view,
        final Palette.Mutable<T, P> palette,
        final Registry<P> parentRegistryType,
        final DataQuery paletteQuery,
        final Function<T, P> parentGetter,
        final Set<String> requiredMods
    ) {
        palette.streamWithIds().forEach(entry -> {
            // getOrAssign to skip the optional, it will never assign
            final String stringified = palette.getType().getStringifier().apply(
                parentRegistryType,
                entry.getKey()
            );
            view.set(paletteQuery.then(stringified), entry.getValue());
            final ResourceKey blockKey = parentRegistryType
                .findValueKey(parentGetter.apply(entry.getKey()))
                .orElseThrow(() -> new IllegalStateException(
                    "Somehow have a BlockState that is not registered in the global BlockType registry"));
            if (!"minecraft".equals(blockKey.getNamespace())) {
                requiredMods.add(blockKey.getNamespace());
            }
        });
    }

     private static void writeIdToBuffer(final ByteArrayOutputStream buffer, final int orAssign) {
        int id = orAssign;

        while ((id & -128) != 0) {
            buffer.write(id & 127 | 128);
            id >>>= 7;
        }
        buffer.write(id);
    }

}
