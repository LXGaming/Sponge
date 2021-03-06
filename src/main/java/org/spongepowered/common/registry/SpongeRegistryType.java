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
package org.spongepowered.common.registry;

import org.spongepowered.api.ResourceKey;
import org.spongepowered.api.registry.DefaultedRegistryType;
import org.spongepowered.api.registry.RegistryHolder;
import org.spongepowered.api.registry.RegistryType;

import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Supplier;

public class SpongeRegistryType<T> implements RegistryType<T> {

    private final ResourceKey root;
    private final ResourceKey location;

    public SpongeRegistryType(final ResourceKey root, final ResourceKey location) {
        this.root = Objects.requireNonNull(root, "root");
        this.location = Objects.requireNonNull(location, "location");
    }

    @Override
    public final ResourceKey root() {
        return this.root;
    }

    @Override
    public final ResourceKey location() {
        return this.location;
    }

    @Override
    public final <V extends T> DefaultedRegistryType<V> asDefaultedType(final Supplier<RegistryHolder> defaultHolder) {
        return new SpongeDefaultedRegistryType<>(this.root, this.location, Objects.requireNonNull(defaultHolder, "defaultHolder"));
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", this.getClass().getSimpleName() + "[", "]")
                .add("root=" + this.root)
                .add("location='" + this.location + "'")
                .toString();
    }

    public static final class FactoryImpl implements Factory {

        @Override
        public <T> RegistryType<T> create(final ResourceKey root, final ResourceKey location) {
            Objects.requireNonNull(root, "root");
            Objects.requireNonNull(location, "location");

            return new SpongeRegistryType<T>(root, location);
        }
    }
}
