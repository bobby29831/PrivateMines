/**
 * MIT License
 *
 * Copyright (c) 2021 - 2022 Kyle Hicks
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.untouchedodin0.privatemines.events;

import me.untouchedodin0.kotlin.mine.type.MineType;
import me.untouchedodin0.privatemines.mine.Mine;
import me.untouchedodin0.privatemines.mine.data.MineData;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class PrivateMineUpgradeEvent extends Event {

    public static final HandlerList handlers = new HandlerList();

    public UUID owner;
    public Mine mine;
    public MineType oldType;
    public MineType newType;
    public boolean cancelled;

    public PrivateMineUpgradeEvent(UUID owner, Mine mine, MineType oldType, MineType newType) {
        this.owner = owner;
        this.mine = mine;
        this.oldType = oldType;
        this.newType = newType;
    }

    public UUID getOwner() {
        return owner;
    }

    public Mine getMine() {
        return mine;
    }

    public MineType getOldType() {
        return oldType;
    }

    public MineType getNewType() {
        return newType;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
}
