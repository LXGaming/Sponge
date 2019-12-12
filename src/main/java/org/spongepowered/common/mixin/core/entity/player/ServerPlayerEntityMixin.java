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
package org.spongepowered.common.mixin.core.entity.player;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableSet;
import net.minecraft.block.BlockState;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeMap;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.entity.merchant.IMerchant;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.container.ChestContainer;
import net.minecraft.inventory.container.CraftingResultSlot;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.play.ServerPlayNetHandler;
import net.minecraft.network.play.client.CClientSettingsPacket;
import net.minecraft.network.play.server.SAnimateHandPacket;
import net.minecraft.network.play.server.SChangeBlockPacket;
import net.minecraft.network.play.server.SCombatPacket;
import net.minecraft.network.play.server.SEntityPropertiesPacket;
import net.minecraft.network.play.server.SSetSlotPacket;
import net.minecraft.network.play.server.SSpawnPositionPacket;
import net.minecraft.network.play.server.SUpdateHealthPacket;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreCriteria;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.stats.Stats;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.GameRules;
import net.minecraft.world.GameType;
import net.minecraft.world.IInteractionObject;
import net.minecraft.world.server.ServerWorld;
import org.objectweb.asm.Opcodes;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.data.type.SkinPart;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.entity.living.player.gamemode.GameMode;
import org.spongepowered.api.event.Cancellable;
import org.spongepowered.api.event.CauseStackManager;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.entity.DestructEntityEvent;
import org.spongepowered.api.event.entity.living.humanoid.ChangeGameModeEvent;
import org.spongepowered.api.event.entity.living.humanoid.player.PlayerChangeClientSettingsEvent;
import org.spongepowered.api.item.inventory.Carrier;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.item.inventory.entity.Hotbar;
import org.spongepowered.api.item.inventory.query.QueryTypes;
import org.spongepowered.api.item.inventory.transaction.SlotTransaction;
import org.spongepowered.api.profile.GameProfile;
import org.spongepowered.api.scoreboard.Scoreboard;
import org.spongepowered.api.service.user.UserStorageService;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.channel.MessageChannel;
import org.spongepowered.api.text.chat.ChatType;
import org.spongepowered.api.text.chat.ChatVisibility;
import org.spongepowered.api.util.Tristate;
import org.spongepowered.api.world.gamerule.DefaultGameRules;
import org.spongepowered.api.world.storage.WorldProperties;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.bridge.command.CommandSenderBridge;
import org.spongepowered.common.bridge.command.CommandSourceBridge;
import org.spongepowered.common.bridge.data.DataCompoundHolder;
import org.spongepowered.common.bridge.entity.player.PlayerEntityBridge;
import org.spongepowered.common.bridge.entity.player.ServerPlayerEntityBridge;
import org.spongepowered.common.bridge.inventory.container.TrackedContainerBridge;
import org.spongepowered.common.bridge.inventory.container.TrackedInventoryBridge;
import org.spongepowered.common.bridge.inventory.ViewableInventoryBridge;
import org.spongepowered.common.bridge.network.ServerPlayNetHandlerBridge;
import org.spongepowered.common.bridge.permissions.SubjectBridge;
import org.spongepowered.common.bridge.scoreboard.ScorePlayerTeamBridge;
import org.spongepowered.common.bridge.scoreboard.ServerScoreboardBridge;
import org.spongepowered.common.data.type.SpongeSkinPart;
import org.spongepowered.common.entity.living.human.HumanEntity;
import org.spongepowered.common.event.ShouldFire;
import org.spongepowered.common.event.SpongeCommonEventFactory;
import org.spongepowered.common.event.tracking.PhaseContext;
import org.spongepowered.common.event.tracking.PhaseTracker;
import org.spongepowered.common.event.tracking.phase.entity.BasicEntityContext;
import org.spongepowered.common.event.tracking.phase.entity.EntityPhase;
import org.spongepowered.common.inventory.adapter.InventoryAdapter;
import org.spongepowered.common.item.util.ItemStackUtil;
import org.spongepowered.common.registry.type.entity.SkinPartRegistryModule;
import org.spongepowered.common.service.user.SpongeUserStorageService;
import org.spongepowered.common.text.SpongeTexts;
import org.spongepowered.common.text.chat.ChatUtil;
import org.spongepowered.common.util.Constants;
import org.spongepowered.common.util.LocaleCache;
import org.spongepowered.common.util.NetworkUtil;
import org.spongepowered.common.util.VecHelper;
import org.spongepowered.common.world.border.PlayerOwnBorderListener;
import org.spongepowered.math.vector.Vector3d;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin extends PlayerEntityMixin implements SubjectBridge, ServerPlayerEntityBridge, CommandSenderBridge,
    CommandSourceBridge {

    @Shadow @Final public MinecraftServer server;
    @Shadow @Final public PlayerInteractionManager interactionManager;
    @Shadow public ServerPlayNetHandler connection;
    @Shadow private int lastExperience;
    @Shadow private float lastHealth;
    @Shadow private int lastFoodLevel;
    @Shadow public boolean isChangingQuantityOnly;

    @Shadow public abstract ServerWorld getServerWorld();

    // Used to restore original item received in a packet after canceling an event
    private ItemStack impl$packetItem = ItemStack.EMPTY;
    private final User impl$user = this.impl$getUserObjectOnConstruction();
    private ImmutableSet<SkinPart> impl$skinParts = ImmutableSet.of();
    private int impl$viewDistance;
    @Nullable private GameType impl$pendingGameType;
    private Scoreboard impl$spongeScoreboard = Sponge.getGame().getServer().getServerScoreboard().get();
    @Nullable private ServerPlayerEntity impl$delegate;
    @Nullable private Vector3d impl$velocityOverride = null;
    private double impl$healthScale = Constants.Entity.Player.DEFAULT_HEALTH_SCALE;
    private float impl$cachedModifiedHealth = -1;
    private final PlayerOwnBorderListener impl$borderListener = new PlayerOwnBorderListener((ServerPlayerEntity) (Object) this);
    @Nullable private Boolean impl$keepInventory = null;
    @Nullable private Text impl$displayName = null;

    @Override
    public void spongeImpl$writeToSpongeCompound(final CompoundNBT compound) {
        super.spongeImpl$writeToSpongeCompound(compound);
        if (this.bridge$isHealthScaled()) {
            compound.putDouble(Constants.Sponge.Entity.Player.HEALTH_SCALE, this.impl$healthScale);
        }
    }

    @Override
    public void spongeImpl$readFromSpongeCompound(final CompoundNBT compound) {
        super.spongeImpl$readFromSpongeCompound(compound);
        if (compound.contains(Constants.Sponge.Entity.Player.HEALTH_SCALE, Constants.NBT.TAG_DOUBLE)) {
            this.impl$healthScale = compound.getDouble(Constants.Sponge.Entity.Player.HEALTH_SCALE);
        }
    }

    @Inject(method = "removeEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/NetHandlerPlayServer;sendPacket(Lnet/minecraft/network/Packet;)V"))
    private void impl$removeHumanHook(final Entity entityIn, final CallbackInfo ci) {
        if (entityIn instanceof HumanEntity) {
            ((HumanEntity) entityIn).onRemovedFrom((ServerPlayerEntity) (Object) this);
        }
    }

    @Override
    public boolean bridge$keepInventory() {
        if (this.impl$keepInventory == null) {
            return this.world.getGameRules().getBoolean(DefaultGameRules.KEEP_INVENTORY);
        }
        return this.impl$keepInventory;
    }

    /**
     * @author blood - May 12th, 2016
     * @author gabizou - June 3rd, 2016
     *
     * @reason SpongeForge requires an overwrite so we do it here instead. This handles player death events.
     */
    @Override
    @Overwrite
    public void onDeath(final DamageSource cause) {
        // Sponge start
        final boolean isMainThread = Sponge.isServerAvailable() && Sponge.getServer().isMainThread();
        final Optional<DestructEntityEvent.Death> optEvent = SpongeCommonEventFactory.callDestructEntityEventDeath((ServerPlayerEntity) (Object) this, cause, isMainThread);
        if (optEvent.map(Cancellable::isCancelled).orElse(true)) {
            return;
        }
        final DestructEntityEvent.Death event = optEvent.get();

        // Double check that the PhaseTracker is already capturing the Death phase
        final boolean tracksEntityDeaths;
        if (isMainThread && !this.world.isRemote) {
            tracksEntityDeaths = PhaseTracker.getInstance().getCurrentState().tracksEntityDeaths();
        } else {
            tracksEntityDeaths = false;
        }
        try (final PhaseContext<?> context = this.impl$createDeathContext(cause, tracksEntityDeaths)) {
            if (context != null) {
                context.buildAndSwitch();
            }
            // Sponge end

            final boolean flag = this.world.getGameRules().getBoolean(Constants.GameRule.SHOW_DEATH_MESSAGES);
            this.connection.sendPacket(new SCombatPacket(this.getCombatTracker(), SCombatPacket.Event.ENTITY_DIED, flag));

            if (flag) {
                final Team team = this.getTeam();

                if (team != null && team.getDeathMessageVisibility() != Team.Visible.ALWAYS) {
                    if (team.getDeathMessageVisibility() == Team.Visible.HIDE_FOR_OTHER_TEAMS) {
                        this.server.getPlayerList()
                            .sendMessageToAllTeamMembers((ServerPlayerEntity) (Object) this, this.getCombatTracker().getDeathMessage());
                    } else if (team.getDeathMessageVisibility() == Team.Visible.HIDE_FOR_OWN_TEAM) {
                        this.server.getPlayerList()
                            .sendMessageToTeamOrAllPlayers((ServerPlayerEntity) (Object) this, this.getCombatTracker().getDeathMessage());
                    }
                } else {
                    this.server.getPlayerList().sendMessage(this.getCombatTracker().getDeathMessage());
                }
            }

            this.spawnShoulderEntities();

            // Ignore keepInventory GameRule instead use keepInventory from Event
            if (!event.getKeepInventory() && !this.isSpectator()) {
                this.destroyVanishingCursedItems();
                this.inventory.dropAllItems();
            }

            for (final ScoreObjective scoreobjective : this.getWorldScoreboard().getObjectivesFromCriteria(ScoreCriteria.DEATH_COUNT)) {
                final Score score = this.getWorldScoreboard().getOrCreateScore(this.shadow$getName(), scoreobjective);
                score.incrementScore();
            }

            final LivingEntity entitylivingbase = this.getAttackingEntity();

            if (entitylivingbase != null) {
                final EntityList.EntityEggInfo entitylist$entityegginfo = EntityList.ENTITY_EGGS.get(EntityList.getKey(entitylivingbase));

                if (entitylist$entityegginfo != null) {
                    this.addStat(entitylist$entityegginfo.entityKilledByStat);
                }

                entitylivingbase.awardKillScore((ServerPlayerEntity) (Object) this, this.scoreValue, cause);
            }

            this.addStat(Stats.DEATHS);
            this.takeStat(Stats.TIME_SINCE_DEATH);
            this.extinguish();
            this.setFlag(0, false);
            this.getCombatTracker().reset();

            this.impl$keepInventory = event.getKeepInventory();
        } // Sponge - brackets
    }

    @Nullable
    private PhaseContext<?> impl$createDeathContext(final DamageSource cause, final boolean tracksEntityDeaths) {
        return !tracksEntityDeaths
               ? EntityPhase.State.DEATH.createPhaseContext()
                   .source(this)
                   .setDamageSource((org.spongepowered.api.event.cause.entity.damage.source.DamageSource) cause)
               : null;
    }

    @Inject(method = "copyFrom", at = @At("HEAD"))
    private void impl$copySpongeDataOnRespawn(final ServerPlayerEntity oldPlayer, final boolean respawnFromEnd, final CallbackInfo ci) {
        // Copy over sponge data from the old player.
        // Allows plugins to specify data that persists after players respawn.
        if (!(oldPlayer instanceof DataCompoundHolder)) {
            return;
        }
        final DataCompoundHolder oldEntity = (DataCompoundHolder) oldPlayer;
        if (oldEntity.data$hasSpongeDataCompound()) {
            ((DataCompoundHolder) this).data$getSpongeCompound().setTag(Constants.Sponge.SPONGE_DATA, oldEntity.data$getSpongeDataCompound());
            this.spongeImpl$readFromSpongeCompound(((DataCompoundHolder) this).data$getSpongeDataCompound());
        }
    }

    @Redirect(method = "copyFrom", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/GameRules;getBoolean(Ljava/lang/String;)Z"))
    private boolean impl$useKeepFromBridge(final GameRules gameRules, final String key, final ServerPlayerEntity corpse, final boolean keepEverything) {
        final boolean keep = ((PlayerEntityBridge) corpse).bridge$keepInventory(); // Override Keep Inventory GameRule?
        if (!keep) {
            // Copy corpse inventory to respawned player
            this.inventory.copyInventory(corpse.inventory);
            // Clear corpse so that mods do not copy from it again
            corpse.inventory.clear();
        }
        return keep;
    }

    /**
     * @author Aaron1101 August 11th, 2018
     * @reason Wrap the method in a try-with-resources for a EntityPhase.State.PLAYER_WAKE_UP
     */
    @SuppressWarnings("ConstantConditions")
    @Override
    @Overwrite
    public void wakeUpPlayer(final boolean immediately, final boolean updateWorldFlag, final boolean setSpawn) {
        // Sponge start - enter phase
        try (final BasicEntityContext basicEntityContext = EntityPhase.State.PLAYER_WAKE_UP.createPhaseContext()
                .source(this)) {
            basicEntityContext.buildAndSwitch();
            // Sponge end

            if (this.isPlayerSleeping()) {
                this.getServerWorld().getEntityTracker()
                        .sendToTrackingAndSelf((Entity) (Object) this, new SAnimateHandPacket((Entity) (Object) this, 2)); // Sponge - cast to Entity
            }

            super.wakeUpPlayer(immediately, updateWorldFlag, setSpawn);

            if (this.connection != null) {
                this.connection.setPlayerLocation(this.posX, this.posY, this.posZ, this.rotationYaw, this.rotationPitch);
            }
        } // Sponge - add bracket to close 'try' block
    }

    @Override
    public Optional<User> bridge$getBackingUser() {
        // may be null during initialization, mainly used to avoid potential stack overflow with #bridge$getUserObject
        return Optional.of(this.impl$user);
    }

    @Override
    public User bridge$getUserObject() {
        final UserStorageService service = SpongeImpl.getGame().getServiceManager().provideUnchecked(UserStorageService.class);
        if (this.isFake) { // Fake players are recogizeable through the field set up with bridge$isFake.
            return service.getOrCreate(SpongeUserStorageService.FAKEPLAYER_PROFILE);
        }
        return service.getOrCreate((GameProfile) this.getGameProfile());
    }

    private User impl$getUserObjectOnConstruction() {
        final UserStorageService service = SpongeImpl.getGame().getServiceManager().provideUnchecked(UserStorageService.class);
        if (this.isFake || !(service instanceof SpongeUserStorageService)) {
            return bridge$getUserObject();
        }
        // Emnsure that the game profile is up to date.
        return ((SpongeUserStorageService) service).forceRecreateUser((GameProfile) this.getGameProfile());
    }

    @Override
    public User bridge$getUser() {
        return this.impl$user;
    }

    // Post before the player values are updated
    @SuppressWarnings({"ConstantConditions", "UnstableApiUsage"})
    @Inject(method = "handleClientSettings", at = @At("HEAD"))
    private void impl$throwClientSettingsEvent(final CClientSettingsPacket packet, final CallbackInfo ci) {
        if (ShouldFire.PLAYER_CHANGE_CLIENT_SETTINGS_EVENT) {
            final CauseStackManager csm = Sponge.getCauseStackManager();
            csm.pushCause(this);
            try {
                final Cause cause = csm.getCurrentCause();
                final ImmutableSet<SkinPart> skinParts = SkinPartRegistryModule.getInstance().getAll().stream()
                    .map(part -> (SpongeSkinPart) part)
                    .filter(part -> part.test(packet.getModelPartFlags()))
                    .collect(ImmutableSet.toImmutableSet());
                final Locale locale = LocaleCache.getLocale(packet.getLang());
                final ChatVisibility visibility = (ChatVisibility) (Object) packet.getChatVisibility();
                final PlayerChangeClientSettingsEvent event = SpongeEventFactory.createPlayerChangeClientSettingsEvent(cause, visibility, skinParts,
                    locale, (Player) this, packet.isColorsEnabled(), packet.view);
                SpongeImpl.postEvent(event);
            } finally {
                csm.popCause();
            }
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    @Inject(method = "handleClientSettings", at = @At("RETURN"))
    private void impl$updateSkinFromPacket(final CClientSettingsPacket packet, final CallbackInfo ci) {
        this.impl$skinParts = SkinPartRegistryModule.getInstance().getAll().stream()
            .map(part -> (SpongeSkinPart) part)
            .filter(part -> part.test(packet.getModelPartFlags()))
            .collect(ImmutableSet.toImmutableSet()); // Returned set is immutable
        this.impl$viewDistance = packet.view;
    }

    /**
     * @author simon816 - 14th November, 2016
     *
     * @reason Redirect messages sent to the player to fire a message event. Once the
     * event is handled, it will send the message to
     * {@link Player#sendMessage(ChatType, Text)}.
     *
     * @param component The message
     */
    @Overwrite
    public void sendMessage(final ITextComponent component) {
        if (this.isFake) {
            // Don't bother sending messages to fake players
            return;
        }
        ChatUtil.sendMessage(component, MessageChannel.fixed((Player) this), (CommandSource) this.server, false);
    }

    /**
     * @author Minecrell - August 22nd, 2016
     * @reason Use InetSocketAddress#getHostString() where possible (instead of
     *     inspecting SocketAddress#toString()) to support IPv6 addresses
     */
    @Overwrite
    public String getPlayerIP() {
        return NetworkUtil.getHostString(this.connection.netManager.getRemoteAddress());
    }

    @Override
    public String bridge$getSubjectCollectionIdentifier() {
        return ((SubjectBridge) this.impl$user).bridge$getSubjectCollectionIdentifier();
    }

    @Override
    public String bridge$getIdentifier() {
        return this.impl$user.getIdentifier();
    }

    @Override
    public Tristate bridge$permDefault(final String permission) {
        return ((SubjectBridge) this.impl$user).bridge$permDefault(permission);
    }

    @Override
    public void bridge$setPacketItem(final ItemStack itemstack) {
        this.impl$packetItem = itemstack;
    }

    @Override
    public void bridge$refreshExp() {
        this.lastExperience = -1;
    }

    @Override
    public void bridge$restorePacketItem(final Hand hand) {
        if (this.impl$packetItem.isEmpty()) {
            return;
        }

        this.isChangingQuantityOnly = true;
        this.setHeldItem(hand, this.impl$packetItem);
        final Slot slot = this.openContainer.getSlotFromInventory(this.inventory, this.inventory.currentItem);
        this.openContainer.detectAndSendChanges();
        this.isChangingQuantityOnly = false;
        // force client itemstack update if place event was cancelled
        if (slot != null) {
            this.connection.sendPacket(new SSetSlotPacket(this.openContainer.windowId, slot.slotNumber, this.impl$packetItem));
        }
    }

    @Override
    public void bridge$initScoreboard() {
        ((ServerScoreboardBridge) this.getWorldScoreboard()).bridge$addPlayer((ServerPlayerEntity) (Object) this, true);
    }

    @Override
    public Scoreboard bridge$getScoreboard() {
        return this.impl$spongeScoreboard;
    }

    @Override
    public void bridge$replaceScoreboard(@Nullable Scoreboard scoreboard) {
        if (scoreboard == null) {
            scoreboard = Sponge.getGame().getServer().getServerScoreboard()
                .orElseThrow(() -> new IllegalStateException("Server does not have a valid scoreboard"));
        }
        this.impl$spongeScoreboard = scoreboard;
    }

    @Override
    public void bridge$setScoreboardOnRespawn(final Scoreboard scoreboard) {
        this.impl$spongeScoreboard = scoreboard;
        ((ServerScoreboardBridge) ((Player) this).getScoreboard()).bridge$addPlayer((ServerPlayerEntity) (Object) this, false);
    }

    @Override
    public void bridge$removeScoreboardOnRespawn() {
        ((ServerScoreboardBridge) ((Player) this).getScoreboard()).bridge$removePlayer((ServerPlayerEntity) (Object) this, false);
    }

    @Override
    public MessageChannel bridge$getDeathMessageChannel() {
        final ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (player.world.getGameRules().getBoolean(Constants.GameRule.SHOW_DEATH_MESSAGES)) {
            @Nullable final Team team = player.getTeam();

            if (team != null && team.getDeathMessageVisibility() != Team.Visible.ALWAYS) {
                if (team.getDeathMessageVisibility() == Team.Visible.HIDE_FOR_OTHER_TEAMS) {
                    return ((ScorePlayerTeamBridge) team).bridge$getTeamChannel(player);
                } else if (team.getDeathMessageVisibility() == Team.Visible.HIDE_FOR_OWN_TEAM) {
                    return ((ScorePlayerTeamBridge) team).bridge$getNonTeamChannel();
                }
            } else {
                return ((Player) this).getMessageChannel();
            }
        }

        return MessageChannel.TO_NONE;
    }

    @Override
    public net.minecraft.scoreboard.Scoreboard getWorldScoreboard() {
        return (net.minecraft.scoreboard.Scoreboard) ((Player) this).getScoreboard();
    }


    @Inject(method = "markPlayerActive()V", at = @At("HEAD"))
    private void onPlayerActive(final CallbackInfo ci) {
        ((ServerPlayNetHandlerBridge) this.connection).bridge$resendLatestResourcePackRequest();
    }

    @Override
    public CommandSource bridge$asCommandSource() {
        return (CommandSource) this;
    }

    @Override
    public ICommandSender bridge$asICommandSender() {
        return (ICommandSender) this;
    }

    @Override
    public void bridge$setImplVelocity(final Vector3d velocity) {
        super.bridge$setImplVelocity(velocity);
        this.impl$velocityOverride = null;
    }

    @Override
    public void bridge$setVelocityOverride(@Nullable final Vector3d velocity) {
        this.impl$velocityOverride = velocity;
    }

    @Nullable
    @Override
    public Vector3d bridge$getVelocityOverride() {
        return this.impl$velocityOverride;
    }

    @Override
    public Set<SkinPart> bridge$getSkinParts() {
        return this.impl$skinParts;
    }

    @Override
    public boolean bridge$hasDelegate() {
        return this.impl$delegate != null;
    }

    @Nullable
    @Override
    public ServerPlayerEntity bridge$getDelegate() {
        return this.impl$delegate;
    }

    @SuppressWarnings("ConstantConditions")
    @Inject(method = "setGameType(Lnet/minecraft/world/GameType;)V", at = @At("HEAD"), cancellable = true)
    private void spongeImpl$onSetGameTypeThrowEvent(final GameType gameType, final CallbackInfo ci) {
        if (ShouldFire.CHANGE_GAME_MODE_EVENT_TARGET_PLAYER) {
            try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
                frame.pushCause(this);
                final ChangeGameModeEvent.TargetPlayer event =
                    SpongeEventFactory.createChangeGameModeEventTargetPlayer(frame.getCurrentCause(),
                        (GameMode) (Object) this.interactionManager.getGameType(), (GameMode) (Object) gameType, (Player) this);
                SpongeImpl.postEvent(event);
                if (event.isCancelled()) {
                    ci.cancel();
                }
                this.impl$pendingGameType = (GameType) (Object) event.getGameMode();
            }
        } else {
            this.impl$pendingGameType = gameType;
        }
    }

    /**
     * This injector must appear <b>after</b> {@link #spongeImpl$onSetGameTypeThrowEvent} since it
     * assigns the {@link #impl$pendingGameType} returned by the event to the actual
     * local variable in the method.
     */
    @ModifyVariable(method = "setGameType(Lnet/minecraft/world/GameType;)V", at = @At(value = "HEAD", remap = false), argsOnly = true)
    private GameType spongeImpl$assignPendingGameType(final GameType gameType) {
        return this.impl$pendingGameType;
    }

    @Redirect(method = "onDeath", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/GameRules;getBoolean(Ljava/lang/String;)Z", ordinal = 0))
    private boolean spongeImpl$SuppressDeathMessageDueToPriorEvent(final GameRules gameRules, final String gameRule) {
        return false; // Suppress death messages since this is handled together with the event calling
    }

    @Override
    public void bridge$setTargetedLocation(@Nullable final Vector3d vec) {
        super.bridge$setTargetedLocation(vec);
        this.connection.sendPacket(new SSpawnPositionPacket(VecHelper.toBlockPos(this.bridge$getTargetedLocation())));
    }

    @Override
    @Nullable
    public Text bridge$getDisplayNameText() {
        return Text.of(this.shadow$getName());
    }

    @Override
    public void bridge$setDisplayName(@Nullable final Text displayName) {
        // Do nothing
    }

    @Override
    public void bridge$sendBlockChange(final BlockPos pos, final BlockState state) {
        final SChangeBlockPacket packet = new SChangeBlockPacket();
        packet.pos = pos;
        packet.blockState = state;
        this.connection.sendPacket(packet);
    }

    /**
     * @author gabizou, April 7th, 2016
     *
     * Technically an overwrite of {@link EntityPlayer#dropItem(boolean)}
     * @param dropAll
     * @return
     */
    @Override
    @Nullable
    public ItemEntity dropItem(final boolean dropAll) {
        final ItemStack currentItem = this.inventory.getCurrentItem();
        if (currentItem.isEmpty()) {
            return null;
        }

        // Add SlotTransaction to PlayerContainer
        final org.spongepowered.api.item.inventory.Slot slot = ((Inventory) this.inventoryContainer)
                .query(QueryTypes.INVENTORY_TYPE.of(Hotbar.class))
                .getSlot(this.inventory.currentItem).get();
        final ItemStackSnapshot originalItem = ItemStackUtil.snapshotOf(currentItem);
        final int count = dropAll && !currentItem.isEmpty() ? currentItem.getCount() : 1;
        final ItemStack itemToDrop = this.inventory.decrStackSize(this.inventory.currentItem, count);
        final SlotTransaction transaction = new SlotTransaction(slot, originalItem, ItemStackUtil.snapshotOf(this.inventory.getCurrentItem()));
        ((TrackedInventoryBridge) this.inventoryContainer).bridge$getCapturedSlotTransactions().add(transaction);

        return this.dropItem(itemToDrop, false, true);
    }

    @Override
    public void stopActiveHand() { // stopActiveHand
        // Our using item state is probably desynced from the client (e.g. from the initial air interaction of a bow being cancelled).
        // We need to re-send the player's inventory to overwrite any client-side inventory changes that may have occured as a result
        // of the client (but not the server) calling Item#onPlayerStoppedUsing (which in the case of a bow, removes one arrow from the inventory).
        if (this.activeItemStack.isEmpty()) {
            ((ServerPlayerEntity) (Object) this).sendContainerToPlayer(((ServerPlayerEntity) (Object) this).container);
        }
        super.stopActiveHand();
    }

    @Inject(method = "closeContainer", at = @At("RETURN"))
    private void onCloseContainer(final CallbackInfo ci) {
        final TrackedInventoryBridge mixinContainer = (TrackedInventoryBridge) this.openContainer;
        // Safety measure to avoid memory leaks as mods may call this directly
        if (mixinContainer.bridge$capturingInventory()) {
            mixinContainer.bridge$setCaptureInventory(false);
            mixinContainer.bridge$getCapturedSlotTransactions().clear();
        }
    }

    @Inject(method = "displayGUIChest",
        at = @At(value = "FIELD",
            target = "Lnet/minecraft/entity/player/EntityPlayerMP;openContainer:Lnet/minecraft/inventory/Container;",
            opcode = Opcodes.PUTFIELD,
            ordinal = 1,
            shift = At.Shift.AFTER))
    private void onSetContainer(final IInventory chestInventory, final CallbackInfo ci) {
        if (!(chestInventory instanceof IInteractionObject) && this.openContainer instanceof ChestContainer && this.isSpectator()) {
            SpongeImpl.getLogger().warn("Opening fallback ContainerChest for inventory '{}'. Most API inventory methods will not be supported", chestInventory);
            ((InventoryAdapter) this.openContainer).bridge$setSpectatorChest(true);
        }
    }

    @Inject(method = "displayGui", at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Container;addListener(Lnet/minecraft/inventory/IContainerListener;)V"))
    private void onDisplayGuiAddListener(IInteractionObject guiOwner, CallbackInfo ci) {
        this.trackInteractable(guiOwner);
    }

    @Inject(method = "displayGUIChest", at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Container;addListener(Lnet/minecraft/inventory/IContainerListener;)V"))
    private void onDisplayGuiChestAddListener(IInventory inventory, CallbackInfo ci) {
        this.trackInteractable(inventory);
    }

    @Inject(method = "displayVillagerTradeGui", at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Container;addListener(Lnet/minecraft/inventory/IContainerListener;)V"))
    private void onDisplayVillagerTradeGuiAddListener(IMerchant villager, CallbackInfo ci) {
        this.trackInteractable(villager);
    }

    @Inject(method = "openHorseInventory", at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Container;addListener(Lnet/minecraft/inventory/IContainerListener;)V"))
    private void onOpenGuiHorseInventoryAddListener(AbstractHorse horse, IInventory inventoryIn, CallbackInfo ci) {
        this.trackInteractable(inventoryIn);
    }

    private void trackInteractable(Object inventory) {
        if (inventory instanceof Carrier) {
            inventory = ((Carrier) inventory).getInventory();
        }
        if (inventory instanceof Inventory) {
            ((Inventory) inventory).asViewable().ifPresent(i -> ((ViewableInventoryBridge) i).bridge$addContainer(this.openContainer));
        }
        ((TrackedContainerBridge) this.openContainer).bridge$setViewed(inventory);
        // TODO else unknown inventory - try to provide wrapper Interactable
    }

    @Override
    public PlayerOwnBorderListener bridge$getWorldBorderListener() {
        return this.impl$borderListener;
    }

    /**
     * Send SlotCrafting updates to client for custom recipes.
     *
     * @author Faithcaio - 31.12.2016
     * @reason Vanilla is not updating the Client when Slot is SlotCrafting - this is an issue when plugins register new recipes
     */
    @Inject(method = "sendSlotContents", at = @At("HEAD"))
    private void sendSlotContents(
        final net.minecraft.inventory.container.Container containerToSend, final int slotInd, final ItemStack stack, final CallbackInfo ci) {
        if (containerToSend.getSlot(slotInd) instanceof CraftingResultSlot) {
            this.connection.sendPacket(new SSetSlotPacket(containerToSend.windowId, slotInd, stack));
        }
    }

    @Redirect(method = "onUpdateEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/EntityPlayerMP;getHealth()F"
            ),
            slice =  @Slice(
                    from = @At(
                            value = "FIELD",
                            target = "Lnet/minecraft/entity/player/EntityPlayerMP;lastHealth:F"
                    ),
                    to = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/network/play/server/SPacketUpdateHealth;<init>(FIF)V"
                    )
            )
    )
    private float spongeGetScaledHealthForPacket(final ServerPlayerEntity entityPlayerMP) {
        return this.bridge$getInternalScaledHealth();
    }

    @Inject(method = "onUpdateEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/EntityPlayerMP;getTotalArmorValue()I", ordinal = 1))
    private void updateHealthPriorToArmor(final CallbackInfo ci) {
        this.bridge$refreshScaledHealth();
    }

    @Override
    public void bridge$setHealthScale(final double scale) {
        checkArgument(scale > 0, "Health scale must be greater than 0!");
        checkArgument(scale < Float.MAX_VALUE, "Health scale cannot exceed Float.MAX_VALUE!");
        this.impl$healthScale = scale;
        this.impl$cachedModifiedHealth = -1;
        this.lastHealth = -1.0F;
        if (scale != Constants.Entity.Player.DEFAULT_HEALTH_SCALE) {
            final CompoundNBT spongeData = ((DataCompoundHolder) this).data$getSpongeDataCompound();
            spongeData.putDouble(Constants.Sponge.Entity.Player.HEALTH_SCALE, scale);
        } else {
            if (((DataCompoundHolder) this).data$hasSpongeDataCompound()) {
                ((DataCompoundHolder) this).data$getSpongeDataCompound().remove(Constants.Sponge.Entity.Player.HEALTH_SCALE);
            }
        }
        this.bridge$refreshScaledHealth();
    }

    @Override
    public void bridge$refreshScaledHealth() {
        // We need to use the dirty instances to signify that the player needs to ahve it updated, instead
        // of modifying the attribute instances themselves, we bypass other potentially detrimental logi
        // that would otherwise break the actual health scaling.
        final Set<IAttributeInstance> dirtyInstances = ((AttributeMap) this.getAttributeMap()).getDirtyInstances();
        this.bridge$injectScaledHealth(dirtyInstances);

        // Send the new information to the client.
        this.connection.sendPacket(new SUpdateHealthPacket(this.bridge$getInternalScaledHealth(), this.getFoodStats().getFoodLevel(),
                this.getFoodStats().getSaturationLevel()));
        this.connection.sendPacket(new SEntityPropertiesPacket(this.getEntityId(), dirtyInstances));
        // Reset the dirty instances since they've now been manually updated on the client.
        dirtyInstances.clear();

    }

    @Override
    public void bridge$injectScaledHealth(final Collection<IAttributeInstance> set) {
        // We need to remove the existing attribute instance for max health, since it's not always going to be the
        // same as SharedMonsterAttributes.MAX_HEALTH
        @Nullable Collection<AttributeModifier> modifiers = null;
        boolean foundMax = false; // Sometimes the max health isn't modified and no longer dirty
        for (final Iterator<IAttributeInstance> iter = set.iterator(); iter.hasNext(); ) {
            final IAttributeInstance dirtyInstance = iter.next();
            if ("generic.maxHealth".equals(dirtyInstance.getAttribute().getName())) {
                foundMax = true;
                modifiers = dirtyInstance.getModifiers();
                iter.remove();
                break;
            }
        }
        if (!foundMax) {
            // Means we didn't find the max health attribute and need to fetch the modifiers from
            // the cached map because it wasn't marked dirty for some reason
            modifiers = this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).getModifiers();
        }

        // We now re-create a new ranged attribute for our desired max health
        final double defaultt =
                this.bridge$isHealthScaled() ? this.impl$healthScale : this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).getBaseValue();

        final RangedAttribute maxHealth =
            new RangedAttribute(null, "generic.maxHealth", defaultt, 0.0D, Float.MAX_VALUE);
        maxHealth.setDescription("Max Health");
        maxHealth.setShouldWatch(true); // needs to be watched

        final ModifiableAttributeInstance attribute = new ModifiableAttributeInstance(this.getAttributeMap(), maxHealth);

        if (!modifiers.isEmpty()) {
            modifiers.forEach(attribute::applyModifier);
        }
        set.add(attribute);
    }

    @Override
    public double bridge$getHealthScale() {
        return this.impl$healthScale;
    }

    @Override
    public float bridge$getInternalScaledHealth() {
        if (!this.bridge$isHealthScaled()) {
            return this.getHealth();
        }
        if (this.impl$cachedModifiedHealth == -1) {
            // Because attribute modifiers from mods can add onto health and multiply health, we
            // need to replicate what the mod may be trying to represent, regardless whether the health scale
            // says to show only x hearts.
            final IAttributeInstance maxAttribute = this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
            double modifiedScale = this.impl$healthScale;
            // Apply additive modifiers
            for (final AttributeModifier attributemodifier : maxAttribute.getModifiersByOperation(0)) {
                modifiedScale += attributemodifier.getAmount();
            }

            for (final AttributeModifier attributemodifier1 : maxAttribute.getModifiersByOperation(1)) {
                modifiedScale += modifiedScale * attributemodifier1.getAmount();
            }

            for (final AttributeModifier attributemodifier2 : maxAttribute.getModifiersByOperation(2)) {
                modifiedScale *= 1.0D + attributemodifier2.getAmount();
            }

            this.impl$cachedModifiedHealth = (float) modifiedScale;
        }
        return (this.getHealth() / this.getMaxHealth()) * this.impl$cachedModifiedHealth;
    }

    @Override
    public boolean bridge$isHealthScaled() {
        return this.impl$healthScale != Constants.Entity.Player.DEFAULT_HEALTH_SCALE;
    }

    @Redirect(method = "readEntityFromNBT", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getForceGamemode()Z"))
    private boolean onCheckForcedGameMode(final MinecraftServer minecraftServer) {
        return minecraftServer.getForceGamemode() && !this.bridge$hasForcedGamemodeOverridePermission();
    }

    @Override
    public boolean bridge$hasForcedGamemodeOverridePermission() {
        final Player player = (Player) this;
        return player.hasPermission(player.getActiveContexts(), Constants.Permissions.FORCE_GAMEMODE_OVERRIDE);
    }

    @Override
    public void bridge$setDelegateAfterRespawn(final ServerPlayerEntity delegate) {
        this.impl$delegate = delegate;
    }

    @Override
    public void bridge$setContainerDisplay(final Text displayName) {
        this.impl$displayName = displayName;
    }

    @Redirect(method = "displayGUIChest", at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/IInventory;getDisplayName()Lnet/minecraft/util/text/ITextComponent;"))
    private ITextComponent impl$updateDisplayName(final IInventory chestInventory) {
        if (this.impl$displayName == null) {
            return chestInventory.getDisplayName();
        }
        return new StringTextComponent(SpongeTexts.toLegacy(this.impl$displayName));
    }

    @Redirect(method = "displayGui", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/IInteractionObject;getDisplayName()Lnet/minecraft/util/text/ITextComponent;"))
    private ITextComponent impl$updateDisplayName(final IInteractionObject guiOwner) {
        if (this.impl$displayName == null) {
            return guiOwner.getDisplayName();
        }
        return new StringTextComponent(SpongeTexts.toLegacy(this.impl$displayName));
    }

    @Redirect(method = "openGuiHorseInventory", at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/IInventory;getDisplayName()Lnet/minecraft/util/text/ITextComponent;"))
    private ITextComponent impl$updateDisplayNameForHorseInventory(final IInventory inventoryIn) {
        if (this.impl$displayName == null) {
            return inventoryIn.getDisplayName();
        }
        return new StringTextComponent(SpongeTexts.toLegacy(this.impl$displayName));
    }

    @Redirect(method = "displayVillagerTradeGui", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/IMerchant;getDisplayName()Lnet/minecraft/util/text/ITextComponent;"))
    private ITextComponent impl$updateDisplayNameForVillagerTrading(final IMerchant villager) {
        if (this.impl$displayName == null) {
            return villager.getDisplayName();
        }
        return new StringTextComponent(SpongeTexts.toLegacy(this.impl$displayName));
    }

    @SuppressWarnings("BoundedWildcard")
    @Inject(method = "canAttackPlayer", at = @At("HEAD"), cancellable = true)
    private void impl$useWorldBasedAttackRules(final PlayerEntity other, final CallbackInfoReturnable<Boolean> cir) {
        final boolean worldPVP = ((WorldProperties) other.world.getWorldInfo()).isPVPEnabled();

        if (!worldPVP) {
            cir.setReturnValue(false);
            return;
        }

        final boolean teamPVP = super.canAttackPlayer(other);
        cir.setReturnValue(teamPVP);
    }

    @Override
    public int bridge$getViewDistance() {
        return this.impl$viewDistance;
    }
}