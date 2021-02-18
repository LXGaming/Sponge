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
package org.spongepowered.test.volumestream;

import com.google.inject.Inject;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.apache.logging.log4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.parameter.CommandContext;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.api.command.parameter.managed.Flag;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.data.persistence.DataContainer;
import org.spongepowered.api.data.persistence.DataFormats;
import org.spongepowered.api.data.type.HandType;
import org.spongepowered.api.data.type.HandTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.event.Cancellable;
import org.spongepowered.api.event.CauseStackManager;
import org.spongepowered.api.event.EventContextKeys;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.cause.entity.SpawnTypes;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.event.lifecycle.RegisterCommandEvent;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.world.BlockChangeFlags;
import org.spongepowered.api.world.schematic.Schematic;
import org.spongepowered.api.world.volume.archetype.ArchetypeVolume;
import org.spongepowered.api.world.volume.stream.StreamOptions;
import org.spongepowered.api.world.volume.stream.VolumeApplicators;
import org.spongepowered.api.world.volume.stream.VolumeCollectors;
import org.spongepowered.api.world.volume.stream.VolumePositionTranslators;
import org.spongepowered.math.vector.Vector3i;
import org.spongepowered.plugin.PluginContainer;
import org.spongepowered.plugin.jvm.Plugin;
import org.spongepowered.test.LoadableModule;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

@Plugin("volumestreamtest")
public final class VolumeStreamTest implements LoadableModule {

    @Inject private PluginContainer plugin;
    @Inject private Logger logger;
    @Inject @ConfigDir(sharedRoot = false) private Path config;
    private Path schematicsDir;

    private final CopyPastaListener listener = new CopyPastaListener();
    private static final Map<UUID, PlayerData> player_data = new HashMap<>();

    private static PlayerData get(final Player pl) {
        PlayerData data = VolumeStreamTest.player_data.get(pl.getUniqueId());
        if (data == null) {
            data = new PlayerData(pl.getUniqueId());
            VolumeStreamTest.player_data.put(pl.getUniqueId(), data);
        }
        return data;
    }
    @Override
    public void enable(final CommandContext ctx) {
        Sponge.getEventManager().registerListeners(this.plugin, this.listener);
    }

    @Listener
    public void onGamePreInitialization(final RegisterCommandEvent<Command.Parameterized> event) throws IOException {
        this.schematicsDir = this.config.resolve("schematics");
        Files.createDirectories(this.schematicsDir);
        event.register(
            this.plugin,
            Command.builder()
                .setShortDescription(Component.text("Copies a region of the world to your clipboard"))
                .setPermission(this.plugin.getMetadata().getId() + ".command.copy")
                .setExecutor(src -> {
                    if (!(src.getCause().root() instanceof Player)) {
                        src.sendMessage(Identity.nil(), Component.text("Player only.", NamedTextColor.RED));
                        return CommandResult.success();
                    }
                    final Player player = (Player) src.getCause().root();
                    final PlayerData data = VolumeStreamTest.get(player);
                    if (data.getPos1() == null || data.getPos2() == null) {
                        player.sendMessage(Identity.nil(), Component.text("You must set both positions before copying", NamedTextColor.RED));
                        return CommandResult.success();
                    }
                    final Vector3i min = data.getPos1().min(data.getPos2());
                    final Vector3i max = data.getPos1().max(data.getPos2());
                    data.setOrigin(player.getBlockPosition());
                    final ArchetypeVolume archetypeVolume = player.getWorld().createArchetypeVolume(min, max, player.getBlockPosition());
                    data.setClipboard(archetypeVolume);
                    player.sendMessage(Identity.nil(), Component.text("Saved to clipboard.", NamedTextColor.GREEN));
                    return CommandResult.success();
                }).build(),
            "copy"
        );
        event.register(this.plugin,
            Command.builder()
                .setShortDescription(Component.text("Pastes your clipboard to where you are standing"))
                .setPermission(this.plugin.getMetadata().getId() + ".command.paste")
                .setExecutor(src -> {
                    if (!(src.getCause().root()  instanceof ServerPlayer)) {
                        src.sendMessage(Identity.nil(), Component.text("Player only.", NamedTextColor.RED));
                        return CommandResult.success();
                    }
                    final ServerPlayer player = (ServerPlayer) src.getCause().root();
                    final PlayerData data = VolumeStreamTest.get(player);
                    final ArchetypeVolume volume = data.getClipboard();
                    if (volume == null) {
                        player.sendMessage(Identity.nil(), Component.text("You must copy something before pasting", NamedTextColor.RED));
                        return CommandResult.success();
                    }
                    try (final CauseStackManager.StackFrame frame = Sponge.getServer().getCauseStackManager().pushCauseFrame()) {
                        frame.pushCause(this.plugin);
                        volume.applyToWorld(player.getWorld(), player.getBlockPosition(), SpawnTypes.PLACEMENT::get);
                    }
                    src.sendMessage(Identity.nil(), Component.text("Pasted clipboard into world.", NamedTextColor.GREEN));
                    return CommandResult.success();
                }).build(),
            "paste"
        );
        final Parameter.Value<String> formatKey = Parameter.string().setKey("format")
            .setUsage(key -> "legacy, sponge")
            .build();
        event.register(this.plugin,
            Command.builder()
                .setShortDescription(Component.text("Pastes your clipboard to where you are standing"))
                .setPermission(this.plugin.getMetadata().getId() + ".command.paste")
                .parameter(formatKey)
                .setExecutor(src -> {
                    if (!(src.getCause().root()  instanceof ServerPlayer)) {
                        src.sendMessage(Identity.nil(), Component.text("Player only.", NamedTextColor.RED));
                        return CommandResult.success();
                    }
                    final String format = src.requireOne(formatKey);
                    final ServerPlayer player = (ServerPlayer) src.getCause().root();
                    final PlayerData data = VolumeStreamTest.get(player);
                    final ArchetypeVolume volume = data.getClipboard();
                    if (volume == null) {
                        player.sendMessage(Identity.nil(), Component.text("You must copy something before pasting", NamedTextColor.RED));
                        return CommandResult.success();
                    }
                    if (!"legacy".equalsIgnoreCase(format) && !"sponge".equalsIgnoreCase(format)) {
                        player.sendMessage(Identity.nil(), Component.text("Unsupported schematic format, supported formats are [legacy, sponge]", NamedTextColor.RED));
                        return CommandResult.success();
                    }
                    final Schematic schematic = Schematic.builder()
                        .volume(data.getClipboard())
                        .metaValue(Schematic.METADATA_AUTHOR, player.getName())
                        .metaValue(Schematic.METADATA_NAME, "some_schematic")
                        .build();
                    final DataContainer schematicData = Sponge.getDataManager().getTranslator(Schematic.class)
                        .orElseThrow(() -> new IllegalStateException("Sponge doesn't have a DataTranslator for Schematics!"))
                        .translate(schematic);

                    try {
                        final Path output = Files.createFile(this.schematicsDir.resolve("some_schematic.schematic"));
                        DataFormats.NBT.get().writeTo(new GZIPOutputStream(Files.newOutputStream(output)), schematicData);
                        player.sendMessage(Identity.nil(), Component.text("Saved schematic to " + output.toAbsolutePath(), NamedTextColor.GREEN));
                    } catch (final Exception e) {
                        e.printStackTrace();
                        final StringWriter writer = new StringWriter();
                        e.printStackTrace(new PrintWriter(writer));
                        final Component errorText = Component.text(writer.toString().replace("\t", "    ")
                            .replace("\r\n", "\n")
                            .replace("\r", "\n")
                        );

                        final TextComponent text = Component.text(
                            "Error saving schematic: " + e.getMessage(), NamedTextColor.RED)
                            .hoverEvent(HoverEvent.showText(errorText));

                        return CommandResult.builder()
                            .error(text).build();
                    }
                    src.sendMessage(Identity.nil(), Component.text("Pasted clipboard into world.", NamedTextColor.GREEN));
                    return CommandResult.success();
                }).build(),
            "save"
        );
    }


    public static class CopyPastaListener {

        @Listener
        public void onLeftClick(final InteractBlockEvent.Primary event, @Root final Player player) {
            event.getContext().get(EventContextKeys.USED_ITEM).ifPresent(snapshot -> {
                final BlockSnapshot block = event.getBlock();
                if (snapshot.getType().equals(ItemTypes.WOODEN_AXE.get()) && block != BlockSnapshot.empty()) {
                    VolumeStreamTest.get(player).setPos1(block.getPosition());
                    player.sendMessage(Component.text("Position 1 set to " + block.getPosition(), NamedTextColor.LIGHT_PURPLE));

                    if (event instanceof Cancellable) {
                        ((Cancellable) event).setCancelled(true);
                    }
                }
            });
        }

        @Listener
        public void onInteract(final InteractBlockEvent.Secondary event, @Root final Player player) {
            event.getContext().get(EventContextKeys.USED_ITEM).ifPresent(snapshot -> {
                final BlockSnapshot block = event.getBlock();
                if (snapshot.getType().equals(ItemTypes.WOODEN_AXE.get()) && block != BlockSnapshot.empty()) {
                    VolumeStreamTest.get(player).setPos2(block.getPosition());
                    player.sendMessage(Component.text("Position 2 set to " + block.getPosition(), NamedTextColor.LIGHT_PURPLE));
                    event.setCancelled(true);
                }
            });
        }
    }

    public static class PlayerData {

        private final UUID uid;
        private Vector3i pos1;
        private Vector3i pos2;
        private Vector3i origin;
        private ArchetypeVolume clipboard;

        public PlayerData(final UUID uid) {
            this.uid = uid;
        }

        public UUID getUid() {
            return this.uid;
        }

        public Vector3i getPos1() {
            return this.pos1;
        }

        public void setPos1(final Vector3i pos) {
            this.pos1 = pos;
        }

        public Vector3i getPos2() {
            return this.pos2;
        }

        public void setPos2(final Vector3i pos) {
            this.pos2 = pos;
        }

        public ArchetypeVolume getClipboard() {
            return this.clipboard;
        }

        public void setClipboard(final ArchetypeVolume volume) {
            this.clipboard = volume;
        }

        public Vector3i getOrigin() {
            return this.origin;
        }

        public void setOrigin(final Vector3i origin) {
            this.origin = origin;
        }
    }
}
