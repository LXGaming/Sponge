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
package org.spongepowered.common.entity.ai.goal.builtin.creature;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import org.spongepowered.api.entity.ai.goal.builtin.creature.RangedAttackAgainstAgentGoal;
import org.spongepowered.api.entity.living.Ranger;

public final class SpongeRangedAttackAgainstAgentGoalBuilder implements RangedAttackAgainstAgentGoal.Builder {

    private double maxSpeed;
    private int delayBetweenAttacks;
    private float attackRadius;

    public SpongeRangedAttackAgainstAgentGoalBuilder() {
        this.reset();
    }

    @Override
    public RangedAttackAgainstAgentGoal.Builder moveSpeed(double speed) {
        this.maxSpeed = speed;
        return this;
    }

    @Override
    public RangedAttackAgainstAgentGoal.Builder delayBetweenAttacks(int delay) {
        this.delayBetweenAttacks = delay;
        return this;
    }

    @Override
    public RangedAttackAgainstAgentGoal.Builder attackRadius(float radius) {
        this.attackRadius = radius;
        return this;
    }

    @Override
    public RangedAttackAgainstAgentGoal.Builder from(RangedAttackAgainstAgentGoal value) {
        checkNotNull(value);
        this.maxSpeed = value.getMoveSpeed();
        this.delayBetweenAttacks = value.getDelayBetweenAttacks();
        this.attackRadius = value.getAttackRadius();
        return this;
    }

    @Override
    public RangedAttackAgainstAgentGoal.Builder reset() {
        // I'ma use Snowmen defaults. I like Snowmen.
        this.maxSpeed = 1.25D;
        this.delayBetweenAttacks = 20;
        this.attackRadius = 10.0f;
        return this;
    }

    @Override
    public RangedAttackAgainstAgentGoal build(Ranger owner) {
        checkNotNull(owner);
        checkArgument(owner instanceof RangedAttackMob, "Ranger must be an IRangedAttackMob!");
        return (RangedAttackAgainstAgentGoal) new RangedAttackGoal((RangedAttackMob) owner, this.maxSpeed, this.delayBetweenAttacks, this.attackRadius);
    }
}
