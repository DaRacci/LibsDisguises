package me.libraryaddict.disguise.utilities.packets.packetlisteners;

import com.comphenix.protocol.PacketType.Play.Server;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.LibsDisguises;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.libraryaddict.disguise.disguisetypes.MetaIndex;
import me.libraryaddict.disguise.disguisetypes.PlayerDisguise;
import me.libraryaddict.disguise.utilities.DisguiseUtilities;
import me.libraryaddict.disguise.utilities.LibsPremium;
import me.libraryaddict.disguise.utilities.packets.LibsPackets;
import me.libraryaddict.disguise.utilities.packets.PacketsManager;
import me.libraryaddict.disguise.utilities.reflection.NmsVersion;
import me.libraryaddict.disguise.utilities.reflection.ReflectionManager;
import me.libraryaddict.disguise.utilities.reflection.WatcherValue;
import me.libraryaddict.disguise.utilities.sounds.SoundGroup;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PacketListenerViewSelfDisguise extends PacketAdapter {
    public PacketListenerViewSelfDisguise(LibsDisguises plugin) {
        super(plugin, ListenerPriority.HIGH, Server.NAMED_ENTITY_SPAWN, Server.ATTACH_ENTITY, Server.REL_ENTITY_MOVE, Server.REL_ENTITY_MOVE_LOOK,
            Server.ENTITY_LOOK, Server.ENTITY_TELEPORT, Server.ENTITY_HEAD_ROTATION, Server.ENTITY_METADATA, Server.ENTITY_EQUIPMENT, Server.ANIMATION,
            Server.ENTITY_EFFECT, Server.ENTITY_VELOCITY, Server.UPDATE_ATTRIBUTES, Server.ENTITY_STATUS);
    }

    @Override
    public void onPacketSending(final PacketEvent event) {
        if (event.isCancelled()) {
            return;
        }

        try {
            final Player observer = event.getPlayer();

            if (observer.getName().contains("UNKNOWN[")) {// If the player is temporary
                return;
            }

            PacketContainer packet = event.getPacket();

            // If packet isn't meant for the disguised player's self disguise
            if (packet.getIntegers().read(0) != observer.getEntityId()) {
                return;
            }

            if (!DisguiseAPI.isSelfDisguised(observer)) {
                return;
            }

            final Disguise disguise = DisguiseAPI.getDisguise(observer, observer);

            if (disguise == null) {
                return;
            }

            // Here I grab the packets to convert them to, So I can display them as if the disguise sent them.
            LibsPackets transformed = PacketsManager.getPacketsHandler().transformPacket(packet, disguise, observer, observer);

            if (transformed.isUnhandled()) {
                transformed.addPacket(packet);
            }

            LibsPackets selfTransformed = new LibsPackets(disguise);

            for (PacketContainer newPacket : transformed.getPackets()) {
                if (newPacket.getType() != Server.PLAYER_INFO && newPacket.getType() != Server.ENTITY_DESTROY &&
                    newPacket.getIntegers().read(0) == observer.getEntityId()) {
                    if (newPacket == packet) {
                        newPacket = newPacket.shallowClone();
                    }

                    newPacket.getIntegers().write(0, DisguiseAPI.getSelfDisguiseId());
                }

                selfTransformed.addPacket(newPacket);
            }

            for (Map.Entry<Integer, ArrayList<PacketContainer>> entry : transformed.getDelayedPacketsMap().entrySet()) {
                for (PacketContainer newPacket : entry.getValue()) {
                    if (newPacket == packet) {
                        newPacket = newPacket.shallowClone();
                    }

                    if (newPacket.getType() != Server.PLAYER_INFO && newPacket.getType() != Server.ENTITY_DESTROY) {
                        newPacket.getIntegers().write(0, DisguiseAPI.getSelfDisguiseId());
                    }

                    selfTransformed.addDelayedPacket(newPacket, entry.getKey());
                }
            }

            if (disguise.isPlayerDisguise()) {
                LibsDisguises.getInstance().getSkinHandler().handlePackets(observer, (PlayerDisguise) disguise, selfTransformed);
            }

            for (PacketContainer newPacket : selfTransformed.getPackets()) {
                ProtocolLibrary.getProtocolManager().sendServerPacket(observer, newPacket, false);
            }

            selfTransformed.sendDelayed(observer);

            if (event.getPacketType() == Server.ENTITY_METADATA) {
                if (!LibsPremium.getPluginInformation().isPremium() || LibsPremium.getPaidInformation() != null ||
                    LibsPremium.getPluginInformation().getBuildNumber().matches("#\\d+")) {
                    event.setPacket(packet = packet.deepClone());
                }

                if (NmsVersion.v1_19_R2.isSupported()) {
                    for (WrappedDataValue watch : packet.getDataValueCollectionModifier().read(0)) {
                        if (watch.getIndex() != 0) {
                            continue;
                        }

                        byte b = (byte) watch.getRawValue();

                        // Add invisibility, remove glowing
                        byte a = (byte) ((b | 1 << 5) & ~(1 << 6));

                        watch.setValue(a);
                    }
                } else {
                    for (WrappedWatchableObject watch : packet.getWatchableCollectionModifier().read(0)) {
                        if (watch.getIndex() != 0) {
                            continue;
                        }

                        byte b = (byte) watch.getRawValue();

                        // Add invisibility, remove glowing
                        byte a = (byte) ((b | 1 << 5) & ~(1 << 6));

                        watch.setValue(a);
                    }
                }
            } else if (event.getPacketType() == Server.NAMED_ENTITY_SPAWN) {
                event.setCancelled(true);

                List<WatcherValue> watchableList = new ArrayList<>();
                byte b = 1 << 5;

                if (observer.isSprinting()) {
                    b = (byte) (b | 1 << 3);
                }

                WatcherValue watch = new WatcherValue(MetaIndex.ENTITY_META, b);

                watchableList.add(watch);

                PacketContainer metaPacket = ReflectionManager.getMetadataPacket(observer.getEntityId(), watchableList);

                ProtocolLibrary.getProtocolManager().sendServerPacket(observer, metaPacket);
            } else if (event.getPacketType() == Server.ANIMATION) {
                if (packet.getIntegers().read(1) != 2) {
                    event.setCancelled(true);
                }
            } else if (event.getPacketType() == Server.ATTACH_ENTITY || event.getPacketType() == Server.REL_ENTITY_MOVE ||
                event.getPacketType() == Server.REL_ENTITY_MOVE_LOOK || event.getPacketType() == Server.ENTITY_LOOK ||
                event.getPacketType() == Server.ENTITY_TELEPORT || event.getPacketType() == Server.ENTITY_HEAD_ROTATION ||
                event.getPacketType() == Server.ENTITY_EQUIPMENT) {
                event.setCancelled(true);
            } else if (event.getPacketType() == Server.ENTITY_STATUS) {
                if (disguise.isSelfDisguiseSoundsReplaced() && !disguise.getType().isPlayer() && packet.getBytes().read(0) == 2) {
                    event.setCancelled(true);

                    // As of 1.19.3, no sound is sent but instead the client is expected to play a hurt sound on entity status effect
                    if (NmsVersion.v1_19_R2.isSupported()) {
                        SoundGroup group = SoundGroup.getGroup(disguise);
                        Object sound = group.getSound(SoundGroup.SoundType.HURT);

                        if (sound != null) {
                            PacketContainer newPacket = new PacketContainer(Server.ENTITY_SOUND);
                            StructureModifier mods = newPacket.getModifier();

                            mods.write(0, sound);
                            // Category
                            mods.write(2, DisguiseAPI.getSelfDisguiseId());
                            mods.write(3, 1f);
                            mods.write(4, 1f);
                            mods.write(5, (long) (Math.random() * 1000L));

                            newPacket.getSoundCategories().write(0, EnumWrappers.SoundCategory.MASTER);

                            ProtocolLibrary.getProtocolManager().sendServerPacket(observer, newPacket);
                        }
                    }
                }
            } else if (event.getPacketType() == Server.ENTITY_VELOCITY && !DisguiseUtilities.isPlayerVelocity(observer)) {
                // The player only sees velocity changes when there is a velocity event. As the method claims there
                // was no velocity event...
                event.setCancelled(true);
                // Clear old velocity, this should only occur once.
                DisguiseUtilities.setPlayerVelocity(null);
            }
        } catch (Throwable ex) {
            event.setCancelled(true);
            ex.printStackTrace();
        }
    }
}
